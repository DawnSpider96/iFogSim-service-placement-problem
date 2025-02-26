package org.fog.utils;

import org.fog.entities.PlacementRequest;
import org.fog.placement.MyHeuristic;
import org.fog.placement.PlacementLogicFactory;
import org.fog.test.perfeval.SimulationConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MetricUtils {
    // This class contains static functions for manipulation and plotting of simulation metric values

    // Resources
    private static final String[] resources = new String[]{"cpu", "ram"};

    private static final HashMap<Integer, String> heuristics = new HashMap<Integer, String>(){{
        put(PlacementLogicFactory.BEST_FIT, "BestFit");
        put(PlacementLogicFactory.CLOSEST_FIT, "ClosestFit");
        put(PlacementLogicFactory.MAX_FIT, "MaxFit");
        put(PlacementLogicFactory.RANDOM, "Random");
        put(PlacementLogicFactory.MULTI_OPT, "MultiOpt");
        put(PlacementLogicFactory.SIMULATED_ANNEALING, "SA");
        put(PlacementLogicFactory.ACO, "ACO");
        put(PlacementLogicFactory.ILP, "ILP");
    }};

    private static Double calculateStandardDeviation(List<MyHeuristic.DeviceState> snapshot, String resourceType){
        /**
         * Calculates the standard deviation
         * of a specified resource's usage
         * across all edge servers in simulation
         * at a single timestamp.
         * The calculation is based on the usage of either CPU or RAM as specified by the {@code resourceType}.
         *
         * @param snapshot List of {@code DeviceState} instances representing the state of each edge server,
         *                 including identifiers and resource statistics.
         * @param resourceType The type of resource for which the standard deviation is calculated. This parameter
         *                     should be "cpu" or "ram".
         * @return The standard deviation of the resource utilization across all edge servers for the specified resource type.
         *         Returns 0.0 if the input list is empty or all values are the same.
         * @throws IllegalArgumentException if {@code resourceType} is neither "cpu" nor "ram".
         */

        if (!resourceType.equals("cpu") && !resourceType.equals("ram")) {
            throw new IllegalArgumentException("Resource type must be 'cpu' or 'ram'");
        }

        List<Double> usagesForCycle = new ArrayList<>();
        // Collect usages
        for (MyHeuristic.DeviceState state : snapshot) {
            double usage = 0;
            if (resourceType.equals("cpu")) {
                usage = state.getCPUUsage();
            } else { //resourceType is ram
                usage = state.getRAMUsage();
            }
            usagesForCycle.add(usage);
        }

        double mean = usagesForCycle.stream().mapToDouble(a -> a).average().orElse(0.0);
        double variance = usagesForCycle.stream().mapToDouble(a -> Math.pow(a - mean, 2)).average().orElse(0.0);

        return Math.sqrt(variance);
    }

    public static Map<String, Map<Double, Double>> handleSimulationResource (Map<Double, List<MyHeuristic.DeviceState>> snapshots){
        /**
         * For a SINGLE simulation,
         * calculates the standard deviation of resource usage (CPU, RAM, etc.) for each resource at each timestamp.
         * This method is used to analyze variability in resource usage over time in a simulation environment.
         *
         * @param snapshots A map where each key is a timestamp (double) corresponding to a placement cycle,
         *                  and each value is a list of {@code DeviceState} instances representing the state
         *                  of all edge servers at that timestamp.
         *                  Each {@code DeviceState} includes id, resources remaining (after the cycle) and total resources.
         * @return A map where each key is a resource name (String) and each value is another map.
         *         The nested map's key is the timestamp of the placement cycle (double), and its value is the standard deviation
         *         (double) of the usage of that resource across all edge servers at that timestamp.
         *         This structure allows easy access to the variability data of each resource at each simulation timestamp.
         */
        Map<String, Map<Double, Double>> standardDeviationsPerResource = new HashMap<>();
        for (String resourceName : resources) {
            Map<Double, Double> standardDeviations = new HashMap<>();
            for (Map.Entry<Double, List<MyHeuristic.DeviceState>> entry : snapshots.entrySet()) {
                Double timestamp = entry.getKey();
                List<MyHeuristic.DeviceState> snapshot = entry.getValue();
                standardDeviations.put(timestamp, calculateStandardDeviation(snapshot, resourceName));
            }
            standardDeviationsPerResource.put(resourceName, standardDeviations);
        }
        return standardDeviationsPerResource;
    }

    private static double calculateMapMean(Map<Double, Double> timestampToValue) {
        return timestampToValue.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static Double calculateLatencyOneCycle(Map<PlacementRequest, Double> latencyMap){
        /**
         * Calculates the average latency
         * of a placement cycle
         * at a single timestamp.
         *
         * @param latencyMap Map of PlacementRequest objects to their latencies in ONE placement cycle.
         * @return The mean of the latencies. Nothing related to the PRs themselves.
         */

        return latencyMap.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    public static Map<Double, Double> handleSimulationLatency (Map<Double, Map<PlacementRequest, Double>> latencies){
        /**
         * For a SINGLE simulation,
         * calculates the average latency of PRs at each placement cycle (timestamp).
         *
         * @param latencies A map where each key is a timestamp (double) corresponding to a placement cycle,
         *                  and each value is a Map of PlacementRequest Objects to their latency.
         * @return A map of timestamp to average latency of that PLacement Cycle (timestamp).
         */
        Map<Double, Double> ls = new HashMap<>();
        for (Map.Entry<Double, Map<PlacementRequest, Double>> entry : latencies.entrySet()) {
            Double timestamp = entry.getKey();
            Map<PlacementRequest, Double> latencyMap = entry.getValue();
            ls.put(timestamp, calculateLatencyOneCycle(latencyMap));
        }
        return ls;
    }

    public static void writeResourceDistributionToCSV(List<Map<String, Map<Double, Double>>> resourceData,
                                                      List<Map<Double, Double>> latencyData,
                                                      List<SimulationConfig> simConfigs,
                                                      String filePath) throws IOException {
        List<Map<String, Double>> averagedResourceData = new ArrayList<>();
        List<Double> averagedLatencyData = new ArrayList<>();

        for (Map<String, Map<Double, Double>> resourceMap : resourceData) {
            Map<String, Double> meanStdDevs = resourceMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> calculateMapMean(e.getValue())
                    ));
            averagedResourceData.add(meanStdDevs);
        }

        for (Map<Double, Double> latencyMap : latencyData) {
            averagedLatencyData.add(calculateMapMean(latencyMap));
        }

        if (averagedResourceData.size() != simConfigs.size()
                || averagedLatencyData.size() != simConfigs.size()) {
            throw new IllegalArgumentException(String.format(
                    "averagedResourceData and averagedLatencyData size mismatch: both must have one element per simulation. %d %d %d",
                    averagedResourceData.size(),
                    averagedLatencyData.size(),
                    simConfigs.size()));
        }

        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.append("Edge Servers, Users, Services, Placement Logic , CPU stddev, RAM stddev, Avg Latency\n");

            Iterator<Map<String, Double>> resourceDataIterator = averagedResourceData.iterator();
            Iterator<Double> latencyDataIterator = averagedLatencyData.iterator();
            Iterator<SimulationConfig> configIterator = simConfigs.iterator();

            while (resourceDataIterator.hasNext() && latencyDataIterator.hasNext()) {
                Map<String, Double> data = resourceDataIterator.next();
                double cpuStdDev = data.getOrDefault("cpu", 0.0);
                double ramStdDev = data.getOrDefault("ram", 0.0);
                double latency = latencyDataIterator.next();
                SimulationConfig sc = configIterator.next();

                fileWriter.append(String.format(
                        "%d,%d,%d,%s,%f,%f,%f\n",
                        sc.getNumberOfEdge(),
                        sc.getNumberOfUser(),
                        sc.getAppLoopLength(),
                        heuristics.get(sc.getPlacementLogic()),
                        cpuStdDev,
                        ramStdDev,
                        latency
                ));
            }
        }
    }
}
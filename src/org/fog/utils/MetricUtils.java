package org.fog.utils;

import org.fog.entities.PlacementRequest;
import org.fog.placement.MyHeuristic;
import org.fog.placement.PlacementLogicFactory;
import org.fog.test.perfeval.SimulationConfig;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

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

    /**
     * Returns the standard deviation of the resources utilisation for a given
     * placement decision.
     *
     * @param edgeServers
     *
     * @return resource utilisation
     */
    private static double computeResourceUtilisation(List<MyHeuristic.DeviceState> edgeServers) {
        double resourceUtilisation = 0.0;
        double[] cpuUtil = new double[edgeServers.size()];
        double[] ramUtil = new double[edgeServers.size()];

        int[] j = { 0 };
        edgeServers.forEach(server->{
            cpuUtil[j[0]] = server.getCPUUtil();
            ramUtil[j[0]] = server.getRAMUtil();
            j[0]++;
        });

        DescriptiveStatistics cpuStats = new DescriptiveStatistics(cpuUtil);
        DescriptiveStatistics ramStats = new DescriptiveStatistics(ramUtil);

        resourceUtilisation = Math.sqrt(0.5 * Math.pow(cpuStats.getStandardDeviation(), 2)
                + 0.5 * Math.pow(ramStats.getStandardDeviation(), 2));
        return resourceUtilisation;
    }

//    private static Double calculateStandardDeviation(List<MyHeuristic.DeviceState> snapshot, String resourceType){
//        /**
//         * Calculates the standard deviation
//         * of a specified resource's usage
//         * across all edge servers in simulation
//         * at a single timestamp.
//         * The calculation is based on the usage of either CPU or RAM as specified by the {@code resourceType}.
//         *
//         * @param snapshot List of {@code DeviceState} instances representing the state of each edge server,
//         *                 including identifiers and resource statistics.
//         * @param resourceType The type of resource for which the standard deviation is calculated. This parameter
//         *                     should be "cpu" or "ram".
//         * @return The standard deviation of the resource utilization across all edge servers for the specified resource type.
//         *         Returns 0.0 if the input list is empty or all values are the same.
//         * @throws IllegalArgumentException if {@code resourceType} is neither "cpu" nor "ram".
//         */
//
//        if (!resourceType.equals("cpu") && !resourceType.equals("ram")) {
//            throw new IllegalArgumentException("Resource type must be 'cpu' or 'ram'");
//        }
//
//        List<Double> usagesForCycle = new ArrayList<>();
//        // Collect usages
//        for (MyHeuristic.DeviceState state : snapshot) {
//            double usage = 0;
//            if (resourceType.equals("cpu")) {
//                usage = state.getCPUUsage();
//            } else { //resourceType is ram
//                usage = state.getRAMUsage();
//            }
//            usagesForCycle.add(usage);
//        }
//
//        double mean = usagesForCycle.stream().mapToDouble(a -> a).average().orElse(0.0);
//        double variance = usagesForCycle.stream().mapToDouble(a -> Math.pow(a - mean, 2)).average().orElse(0.0);
//
//        return Math.sqrt(variance);
//    }

    /**
     * For a SINGLE simulation, computes all resource utilization values across all timestamps
     * @param snapshots A map where each key is a timestamp corresponding to a placement cycle,
     *                 and each value is a list of DeviceState instances
     * @return A list of all resource utilization values across all timestamps
     */
    public static List<Double> handleSimulationResource(Map<Double, List<MyHeuristic.DeviceState>> snapshots) {
        // Flatten the map and compute resource utilization for each timestamp's device states
        List<Double> allUtilizationValues = new ArrayList<>();
        
        for (List<MyHeuristic.DeviceState> snapshot : snapshots.values()) {
            double utilization = computeResourceUtilisation(snapshot);
            allUtilizationValues.add(utilization);
        }
        
        return allUtilizationValues;
    }

//    private static double calculateMapMean(Map<Double, Double> timestampToValue) {
//        return timestampToValue.values().stream()
//                .mapToDouble(Double::doubleValue)
//                .average()
//                .orElse(0.0);
//    }
//
//    public static double calculateMapStandardDeviation(Map<Double, Double> data, Double mean) {
//        double variance = data.values()
//                .stream()
//                .mapToDouble(i -> i)
//                .map(i -> Math.pow(i - mean, 2))
//                .average()
//                .orElse(0.0); // Returns 0 if there are no values
//        return Math.sqrt(variance);
//    }

    /**
     * Calculates descriptive statistics (mean, standard deviation) for a list of double values
     * @param values List of double values
     * @return Array with [mean, standardDeviation]
     */
    public static double[] calculateStatistics(List<Double> values) {
        if (values.isEmpty()) {
            return new double[]{0.0, 0.0};
        }
        
        double mean = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
                
        double variance = values.stream()
                .mapToDouble(Double::doubleValue)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);
                
        double stdDev = Math.sqrt(variance);
        
        return new double[]{mean, stdDev};
    }

    /**
     * For a SINGLE simulation, extracts all latency values across all timestamps
     * @param latencies A map where each key is a timestamp and each value is a map of placement requests to latencies
     * @return A list of all latency values across all timestamps
     */
    public static List<Double> handleSimulationLatency(Map<Double, Map<PlacementRequest, Double>> latencies) {
        // Flatten all latency values from all placement requests across all timestamps
        List<Double> allLatencyValues = new ArrayList<>();
        
        for (Map<PlacementRequest, Double> latencyMap : latencies.values()) {
            allLatencyValues.addAll(latencyMap.values());
        }
        
        return allLatencyValues;
    }

    public static void writeResourceDistributionToCSV(List<List<Double>> resourceData,
                                                      List<List<Double>> latencyData,
                                                      List<SimulationConfig> simConfigs,
                                                      String filePath) throws IOException {
        if (resourceData.size() != simConfigs.size() || latencyData.size() != simConfigs.size()) {
            throw new IllegalArgumentException(String.format(
                    "resourceData and latencyData size mismatch: both must have one element per simulation. %d %d %d",
                    resourceData.size(),
                    latencyData.size(),
                    simConfigs.size()));
        }

        List<double[]> resourceStats = resourceData.stream()
                .map(MetricUtils::calculateStatistics)
                .collect(Collectors.toList());
                
        List<double[]> latencyStats = latencyData.stream()
                .map(MetricUtils::calculateStatistics)
                .collect(Collectors.toList());

        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.append("Edge Servers, Users, Services, Placement Logic, Avg Resource, Resource stddev, Avg Latency, Latency stddev\n");

            for (int i = 0; i < simConfigs.size(); i++) {
                SimulationConfig sc = simConfigs.get(i);
                double[] resStats = resourceStats.get(i);
                double[] latStats = latencyStats.get(i);
                
                fileWriter.append(String.format(
                        "%d,%d,%d,%s,%f,%f,%f,%f\n",
                        sc.getNumberOfEdge(),
                        sc.getNumberOfUser(),
                        sc.getAppLoopLength(),
                        heuristics.get(sc.getPlacementLogic()),
                        resStats[0],  // mean resource utilization
                        resStats[1],  // stddev resource utilization
                        latStats[0],  // mean latency
                        latStats[1]   // stddev latency
                ));
            }
        }
    }
}
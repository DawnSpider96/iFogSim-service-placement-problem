package org.fog.utils;

import com.google.protobuf.MapEntry;
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
    public static double computeResourceUtilisation(List<MyHeuristic.DeviceState> edgeServers) {
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
     * @param utilizationValues A map where each key is a timestamp corresponding to a placement cycle,
     *                 and each value is a list of that cycle's utilisation values
     * @return A list of all resource utilization values across all timestamps
     */
    public static List<Double> handleSimulationResource(Map<Double, Map<PlacementRequest, Double>> utilizationValues) {
        // Flatten
        List<Double> allUtilizationValues = new ArrayList<>();
        for (Map<PlacementRequest, Double> prUtilizations : utilizationValues.values()) {
            allUtilizationValues.addAll(prUtilizations.values());
        }
        return allUtilizationValues;
    }


    public static Map<String, Object> handleSimulationFailedPRs(Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> failedPRs,
                                                                Map<Double, Integer> totalPRs) {
        Map<String, Object> stats = new HashMap<>();
        int totalFailures = 0;
        int totalSum = 0;
        Map<MicroservicePlacementConfig.FAILURE_REASON, Integer> failuresByReason = new HashMap<>();
        Map<Integer, Integer> failuresByDeviceId = new HashMap<>();
        Map<Double, Integer> failuresByTimestamp = new HashMap<>();
        Map<Double, Double> ratiosByTimestamp = new HashMap<>();

//        for (Map.Entry<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> timeFailures : failedPRs.entrySet()) {
        for (Map.Entry<Double, Integer> times : totalPRs.entrySet()) {
            double timestamp = times.getKey();
            // int value, cast to double
            double total = totalPRs.get(timestamp);
            totalSum += (int) total;
            Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON> m = failedPRs.getOrDefault(
                    timestamp,
                    Collections.emptyMap()
            );
            int failuresThisTimestamp = m.size();
            failuresByTimestamp.put(timestamp, failuresThisTimestamp);
            totalFailures += failuresThisTimestamp;
            ratiosByTimestamp.put(timestamp, (double) failuresThisTimestamp / total);

            for (Map.Entry<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON> entry : m.entrySet()) {
                // Count failures by reason
                MicroservicePlacementConfig.FAILURE_REASON reason = entry.getValue();
                failuresByReason.put(reason, failuresByReason.getOrDefault(reason, 0) + 1);

                // Count failures by device ID
                int sensorId = entry.getKey().getSensorId();
                failuresByDeviceId.put(sensorId, failuresByDeviceId.getOrDefault(sensorId, 0) + 1);
            }
        }

        stats.put("totalFailures", totalFailures);
        stats.put("totalPRs", totalSum);
        stats.put("failuresByReason", failuresByReason);
        stats.put("failuresByDeviceId", failuresByDeviceId);
        stats.put("failuresByTimestamp", failuresByTimestamp);
        stats.put("failureRatio", ratiosByTimestamp);

        return stats;
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

    public static double getFailureStats(Map<String, Object> m) {
        // NOTE always handle case of empty map gracefully.
//        Map<Double, Double> failureRatio = (Map<Double, Double>) m.get("failureRatio");
//        double greatestFailureRatio = 0;
//        for (Double ratio : failureRatio.values()) {
//            if (ratio > greatestFailureRatio) greatestFailureRatio = ratio;
//        }
//        return greatestFailureRatio;
        int totalSum = (int) m.get("totalPRs");
        int totalFailures = (int) m.get("totalFailures");
        System.out.println("Simulation Total PRs " + totalSum + ", Total Failures " + totalFailures);
        return (double) totalFailures / totalSum;
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

    public static void writeToCSV(List<List<Double>> resourceData,
                                  List<List<Double>> latencyData,
                                  List<Map<String, Object>> failedPRData,
                                  List<SimulationConfig> simConfigs,
                                  String filePath) throws IOException {
        if (resourceData.size() != simConfigs.size() || latencyData.size() != simConfigs.size()) {
            throw new IllegalArgumentException(String.format(
                    "size mismatch: all must have one element per simulation. %d %d %d %d",
                    resourceData.size(),
                    latencyData.size(),
                    failedPRData.size(),
                    simConfigs.size()));
        }

        List<double[]> resourceStats = resourceData.stream()
                .map(MetricUtils::calculateStatistics)
                .collect(Collectors.toList());
                
        List<double[]> latencyStats = latencyData.stream()
                .map(MetricUtils::calculateStatistics)
                .collect(Collectors.toList());

        List<Double> failureStats = failedPRData.stream()
                .map(MetricUtils::getFailureStats)
                .collect(Collectors.toList());

        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.append("Edge Servers, Users, Services, Placement Logic, Avg Resource, Resource stddev, Avg Latency, Latency stddev, Peak Failure ratio \n");

            for (int i = 0; i < simConfigs.size(); i++) {
                SimulationConfig sc = simConfigs.get(i);
                double[] resStats = resourceStats.get(i);
                double[] latStats = latencyStats.get(i);
                double failStats = failureStats.get(i);
                
                fileWriter.append(String.format(
                        "%d,%d,%s,%s,%f,%f,%f,%f,%f\n",
                        sc.getNumberOfEdge(),
                        sc.getNumberOfUser(),
                        sc.getAppLoopLengthPerType(),
                        heuristics.get(sc.getPlacementLogic()),
                        resStats[0],  // mean resource utilization
                        resStats[1],  // stddev resource utilization
                        latStats[0],  // mean latency
                        latStats[1],   // stddev latency
                        failStats
                ));
            }
        }
    }
}
package org.fog.utils;

import org.fog.entities.PlacementRequest;
import org.fog.entities.ContextPlacementRequest;
import org.fog.placement.SPPHeuristic;
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

    // Power metrics storage across simulations
    private static List<Double> cloudEnergyConsumptions = new ArrayList<>();
    private static List<Double> avgEdgeEnergyConsumptions = new ArrayList<>(); 
    private static List<Double> stdDevEdgeEnergyConsumptions = new ArrayList<>();

    // Add these fields after the existing power metrics storage
    private static Map<String, List<Double>> energyConsumptionsByUserType = new HashMap<>();
    private static Map<String, List<Double>> energyStdDevsByUserType = new HashMap<>();

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
     * Clears all stored power metrics, typically called before starting a new set of simulations
     */
    public static void clearPowerMetrics() {
        cloudEnergyConsumptions.clear();
        avgEdgeEnergyConsumptions.clear();
        stdDevEdgeEnergyConsumptions.clear();
        energyConsumptionsByUserType.clear();
        energyStdDevsByUserType.clear();
    }

    /**
     * Stores cloud energy consumption for the current simulation
     * @param energyConsumption Energy consumption in watt-seconds
     */
    public static void setCloudEnergyConsumption(double energyConsumption) {
        cloudEnergyConsumptions.add(energyConsumption);
    }

    /**
     * Stores average edge energy consumption for the current simulation
     * @param energyConsumption Average energy consumption in watt-seconds
     */
    public static void setAvgEdgeEnergyConsumption(double energyConsumption) {
        avgEdgeEnergyConsumptions.add(energyConsumption);
    }

    /**
     * Stores standard deviation of edge energy consumption for the current simulation
     * @param energyConsumption Standard deviation of energy consumption in watt-seconds
     */
    public static void setStdDevEdgeEnergyConsumption(double energyConsumption) {
        stdDevEdgeEnergyConsumptions.add(energyConsumption);
    }

    /**
     * Stores average energy consumption for a specific user type in the current simulation
     * @param userType The type of user device (GENERIC_USER, AMBULANCE_USER, etc.)
     * @param energyConsumption Average energy consumption in watt-seconds
     */
    public static void setUserTypeEnergyConsumption(String userType, double energyConsumption, double stdDev) {
        if (!energyConsumptionsByUserType.containsKey(userType)) {
            energyConsumptionsByUserType.put(userType, new ArrayList<>());
            energyStdDevsByUserType.put(userType, new ArrayList<>());
        }
        energyConsumptionsByUserType.get(userType).add(energyConsumption);
        energyStdDevsByUserType.get(userType).add(stdDev);
    }

    /**
     * Gets the cloud energy consumption for a specific simulation
     * @param simulationIndex Index of the simulation
     * @return Cloud energy consumption in watt-seconds, or 0.0 if not available
     */
    public static double getCloudEnergyConsumption(int simulationIndex) {
        if (simulationIndex >= 0 && simulationIndex < cloudEnergyConsumptions.size()) {
            return cloudEnergyConsumptions.get(simulationIndex);
        }
        return 0.0;
    }

    /**
     * Gets the average edge energy consumption for a specific simulation
     * @param simulationIndex Index of the simulation
     * @return Average edge energy consumption in watt-seconds, or 0.0 if not available
     */
    public static double getAvgEdgeEnergyConsumption(int simulationIndex) {
        if (simulationIndex >= 0 && simulationIndex < avgEdgeEnergyConsumptions.size()) {
            return avgEdgeEnergyConsumptions.get(simulationIndex);
        }
        return 0.0;
    }

    /**
     * Gets the standard deviation of edge energy consumption for a specific simulation
     * @param simulationIndex Index of the simulation
     * @return Standard deviation of edge energy consumption in watt-seconds, or 0.0 if not available
     */
    public static double getStdDevEdgeEnergyConsumption(int simulationIndex) {
        if (simulationIndex >= 0 && simulationIndex < stdDevEdgeEnergyConsumptions.size()) {
            return stdDevEdgeEnergyConsumptions.get(simulationIndex);
        }
        return 0.0;
    }

    /**
     * Gets the average energy consumption for a specific user type in a specific simulation
     * @param userType The type of user device
     * @param simulationIndex Index of the simulation
     * @return Average energy consumption in watt-seconds, or 0.0 if not available
     */
    public static double getUserTypeEnergyConsumption(String userType, int simulationIndex) {
        if (energyConsumptionsByUserType.containsKey(userType) && 
            simulationIndex >= 0 && 
            simulationIndex < energyConsumptionsByUserType.get(userType).size()) {
            return energyConsumptionsByUserType.get(userType).get(simulationIndex);
        }
        return 0.0;
    }

    /**
     * Gets the standard deviation of energy consumption for a specific user type in a specific simulation
     * @param userType The type of user device
     * @param simulationIndex Index of the simulation
     * @return Standard deviation of energy consumption in watt-seconds, or 0.0 if not available
     */
    public static double getUserTypeEnergyStdDev(String userType, int simulationIndex) {
        if (energyStdDevsByUserType.containsKey(userType) && 
            simulationIndex >= 0 && 
            simulationIndex < energyStdDevsByUserType.get(userType).size()) {
            return energyStdDevsByUserType.get(userType).get(simulationIndex);
        }
        return 0.0;
    }

    /**
     * Returns the standard deviation of the resources utilisation for a given
     * placement decision.
     *
     * @param edgeServers
     *
     * @return resource utilisation
     */
    public static double computeResourceUtilisation(List<SPPHeuristic.DeviceState> edgeServers) {
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
            fileWriter.append("edges, users, services, Placement Logic, Avg Resource, Resource stddev, Avg Latency, Latency stddev, Peak Failure ratio \n");

            for (int i = 0; i < simConfigs.size(); i++) {
                SimulationConfig sc = simConfigs.get(i);
                double[] resStats = resourceStats.get(i);
                double[] latStats = latencyStats.get(i);
                double failStats = failureStats.get(i);
                
                // Get the service count (app loop length) based on whether it's new or legacy format
                String serviceCount = sc.getNumberOfApplications() > 0 
                                     ? String.valueOf(sc.getAppLoopLength()) 
                                     : "Legacy";
                
                fileWriter.append(String.format(
                        "%d,%d,%s,%s,%f,%f,%f,%f,%f\n",
                        sc.getNumberOfEdge(),
                        sc.getNumberOfUser(),
                        serviceCount,
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

    /**
     * Groups resource utilization values by userType for more detailed analysis
     * @param utilizationValues A map where each key is a timestamp and each value is a map of placement requests to utilizations
     * @return A map where each key is a userType and each value is a list of utilization values for that type
     */
    public static Map<String, List<Double>> classifyResourceUtilizationByUserType(Map<Double, Map<PlacementRequest, Double>> utilizationValues) {
        Map<String, List<Double>> utilizationByUserType = new HashMap<>();
        
        for (Map<PlacementRequest, Double> prUtilizations : utilizationValues.values()) {
            for (Map.Entry<PlacementRequest, Double> entry : prUtilizations.entrySet()) {
                PlacementRequest pr = entry.getKey();
                Double value = entry.getValue();
                
                // Extract userType if it's a MyPlacementRequest
                String userType = "unknown";
                if (pr instanceof ContextPlacementRequest) {
                    userType = ((ContextPlacementRequest) pr).getUserType();
                }
                
                // Add the value to the appropriate list
                if (!utilizationByUserType.containsKey(userType)) {
                    utilizationByUserType.put(userType, new ArrayList<>());
                }
                utilizationByUserType.get(userType).add(value);
            }
        }
        
        return utilizationByUserType;
    }
    
    /**
     * Groups latency values by userType for more detailed analysis
     * @param latencies A map where each key is a timestamp and each value is a map of placement requests to latencies
     * @return A map where each key is a userType and each value is a list of latency values for that type
     */
    public static Map<String, List<Double>> classifyLatencyByUserType(Map<Double, Map<PlacementRequest, Double>> latencies) {
        Map<String, List<Double>> latencyByUserType = new HashMap<>();
        
        for (Map<PlacementRequest, Double> prLatencies : latencies.values()) {
            for (Map.Entry<PlacementRequest, Double> entry : prLatencies.entrySet()) {
                PlacementRequest pr = entry.getKey();
                Double value = entry.getValue();
                
                // Extract userType if it's a MyPlacementRequest
                String userType = "unknown";
                if (pr instanceof ContextPlacementRequest) {
                    userType = ((ContextPlacementRequest) pr).getUserType();
                }
                
                // Add the value to the appropriate list
                if (!latencyByUserType.containsKey(userType)) {
                    latencyByUserType.put(userType, new ArrayList<>());
                }
                latencyByUserType.get(userType).add(value);
            }
        }
        
        return latencyByUserType;
    }
    
    /**
     * Groups failed placement requests by userType
     * @param failedPRs A map where each key is a timestamp and each value is a map of failed placement requests to failure reasons
     * @return A map where each key is a userType and each value contains statistics about failures for that type
     */
    public static Map<String, Map<String, Object>> classifyFailedPRsByUserType(
            Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> failedPRs,
            Map<Double, Integer> totalPRs) {
        
        // First, group failed PRs by userType
        Map<String, Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>>> failedPRsByUserType = new HashMap<>();
        Map<String, Map<Double, Integer>> totalPRsByUserType = new HashMap<>();
        
        // Group failed PRs by userType
        for (Map.Entry<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> entry : failedPRs.entrySet()) {
            Double timestamp = entry.getKey();
            Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON> failuresMap = entry.getValue();
            
            for (Map.Entry<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON> failure : failuresMap.entrySet()) {
                PlacementRequest pr = failure.getKey();
                MicroservicePlacementConfig.FAILURE_REASON reason = failure.getValue();
                
                // Extract userType
                String userType = "unknown";
                if (pr instanceof ContextPlacementRequest) {
                    userType = ((ContextPlacementRequest) pr).getUserType();
                }
                
                // Initialize maps if needed
                if (!failedPRsByUserType.containsKey(userType)) {
                    failedPRsByUserType.put(userType, new HashMap<>());
                    totalPRsByUserType.put(userType, new HashMap<>());
                }
                
                // Initialize inner map if needed
                if (!failedPRsByUserType.get(userType).containsKey(timestamp)) {
                    failedPRsByUserType.get(userType).put(timestamp, new HashMap<>());
                }
                
                // Add the failure
                failedPRsByUserType.get(userType).get(timestamp).put(pr, reason);
            }
        }
        
        // Analyze total PRs by userType (this is a rough approximation since we don't have the userType for non-failed PRs)
        for (Map.Entry<Double, Integer> entry : totalPRs.entrySet()) {
            Double timestamp = entry.getKey();
            Integer total = entry.getValue();
            
            // For each userType that had failures at this timestamp
            for (String userType : failedPRsByUserType.keySet()) {
                if (failedPRsByUserType.get(userType).containsKey(timestamp)) {
                    Map<Double, Integer> userTypeTotals = totalPRsByUserType.get(userType);
                    
                    // Set a proportional estimate of total PRs for this userType
                    int failuresForUserType = failedPRsByUserType.get(userType).get(timestamp).size();
                    userTypeTotals.put(timestamp, total);
                }
            }
        }
        
        // Process statistics for each userType
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String userType : failedPRsByUserType.keySet()) {
            result.put(userType, handleSimulationFailedPRs(
                    failedPRsByUserType.get(userType),
                    totalPRsByUserType.get(userType)
            ));
        }
        
        return result;
    }

    /**
     * Writes detailed metrics classified by userType to a CSV file
     * @param resourceDataByType Resource utilization data classified by userType
     * @param latencyDataByType Latency data classified by userType 
     * @param failedPRDataByType Failed PR data classified by userType
     * @param simConfig The simulation configuration
     * @param filePath Path to the output file
     * @throws IOException If there's an error writing to the file
     */
    public static void writeClassifiedMetricsToCSV(
            Map<String, List<Double>> resourceDataByType,
            Map<String, List<Double>> latencyDataByType,
            Map<String, Map<String, Object>> failedPRDataByType,
            SimulationConfig simConfig,
            String filePath) throws IOException {
        
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.append("edges, users, services, Placement Logic, UserType, Avg Resource, Resource stddev, Avg Latency, Latency stddev, Failure ratio\n");
            
            // Get the service count (app loop length) based on whether it's new or legacy format
            String serviceCount = simConfig.getNumberOfApplications() > 0 
                                 ? String.valueOf(simConfig.getAppLoopLength()) 
                                 : "Legacy";
            
            // For each userType, write a row with the metrics
            for (String userType : resourceDataByType.keySet()) {
                List<Double> resourceValues = resourceDataByType.getOrDefault(userType, Collections.emptyList());
                List<Double> latencyValues = latencyDataByType.getOrDefault(userType, Collections.emptyList());
                
                double[] resStats = calculateStatistics(resourceValues);
                double[] latStats = calculateStatistics(latencyValues);
                
                // Calculate failure ratio for this userType (or use 0 if no data)
                double failStats = 0.0;
                if (failedPRDataByType.containsKey(userType)) {
                    Map<String, Object> failData = failedPRDataByType.get(userType);
                    int totalFailures = (int) failData.getOrDefault("totalFailures", 0);
                    int totalPRs = (int) failData.getOrDefault("totalPRs", 1); // Avoid division by zero
                    failStats = (double) totalFailures / totalPRs;
                }
                
                fileWriter.append(String.format(
                        "%d,%d,%s,%s,%s,%f,%f,%f,%f,%f\n",
                        simConfig.getNumberOfEdge(),
                        simConfig.getNumberOfUser(),
                        serviceCount,
                        heuristics.get(simConfig.getPlacementLogic()),
                        userType,
                        resStats[0],  // mean resource utilization
                        resStats[1],  // stddev resource utilization
                        latStats[0],  // mean latency
                        latStats[1],  // stddev latency
                        failStats     // failure ratio
                ));
            }
        }
    }

    /**
     * Gets the name of a placement logic heuristic by its code
     * 
     * @param placementLogicCode The code of the placement logic
     * @return The name of the placement logic, or "Unknown" if not found
     */
    public static String getHeuristicName(int placementLogicCode) {
        return heuristics.getOrDefault(placementLogicCode, "Unknown");
    }

    /**
     * Writes both aggregate metrics and metrics classified by userType to a single CSV file
     * 
     * @param resourceData Aggregate resource utilization data
     * @param latencyData Aggregate latency data
     * @param failedPRData Aggregate failed PR data
     * @param allUtilizations Raw utilization data by simulation index
     * @param allLatencies Raw latency data by simulation index
     * @param allFailedPRs Raw failed PR data by simulation index
     * @param allTotalPRs Raw total PR data by simulation index
     * @param simConfigs Simulation configurations
     * @param fileName Path to the output file
     * @param performanceMetrics List of performance metrics for each simulation
     * @throws IOException If there's an error writing to the file
     */
    public static void writeAllMetricsToCSV(
            List<List<Double>> resourceData,
            List<List<Double>> latencyData,
            List<Map<String, Object>> failedPRData,
            List<Map<Double, Map<PlacementRequest, Double>>> allUtilizations,
            List<Map<Double, Map<PlacementRequest, Double>>> allLatencies,
            List<Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>>> allFailedPRs,
            List<Map<Double, Integer>> allTotalPRs,
            List<SimulationConfig> simConfigs,
            String fileName,
            List<PerformanceMetrics> performanceMetrics) throws IOException {
        
        if (resourceData.size() != simConfigs.size() || latencyData.size() != simConfigs.size()) {
            throw new IllegalArgumentException(String.format(
                    "size mismatch: all must have one element per simulation. %d %d %d %d",
                    resourceData.size(), latencyData.size(), failedPRData.size(), simConfigs.size()));
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

        try (FileWriter fileWriter = new FileWriter(fileName)) {
            // Add power metrics to the header
            fileWriter.append("Simulation,edges,users,UserType,services,Placement Logic,Avg Resource,Resource stddev,Avg Latency,Latency stddev,Failure ratio,ExecutionTime_ms,PeakMemory_MB,BaselineMemory_MB,PostGCMemory_MB,MemoryGrowth_MB,MemoryRetention_MB,CloudEnergy_Ws,DeviceEnergy_Ws,DeviceEnergyStdDev_Ws\n");

            // First, write aggregate metrics for each simulation
            for (int i = 0; i < simConfigs.size(); i++) {
                SimulationConfig sc = simConfigs.get(i);
                double[] resStats = resourceStats.get(i);
                double[] latStats = latencyStats.get(i);
                double failStats = failureStats.get(i);
                
                // Get the service count (app loop length) based on whether it's new or legacy format
                String serviceCount = sc.getNumberOfApplications() > 0 
                                     ? String.valueOf(sc.getAppLoopLength()) 
                                     : "Legacy";
                
                // Get memory metrics
                long peakMemoryMB = performanceMetrics.get(i).getPeakMemoryBytes() / (1024 * 1024);
                long baselineMemoryMB = performanceMetrics.get(i).getBaselineMemoryBytes() / (1024 * 1024);
                long postGCMemoryMB = performanceMetrics.get(i).getPostGCMemoryBytes() / (1024 * 1024);
                long memoryGrowthMB = performanceMetrics.get(i).getMemoryGrowthBytes() / (1024 * 1024);
                long memoryRetentionMB = performanceMetrics.get(i).getMemoryRetentionBytes() / (1024 * 1024);
                
                // Get power metrics from the static lists
                double cloudEnergy = getCloudEnergyConsumption(i);
                double avgEdgeEnergy = getAvgEdgeEnergyConsumption(i);
                double stdDevEdgeEnergy = getStdDevEdgeEnergyConsumption(i);
                
                // For aggregate rows, show the total number of users
                fileWriter.append(String.format(
                        "%d,%d,%d,%s,%s,%s,%f,%f,%f,%f,%f,%d,%d,%d,%d,%d,%d,%f,%f,%f\n",
                        i,
                        sc.getNumberOfEdge(),
                        sc.getNumberOfUser(),
                        "Aggregate", // Mark these as aggregate metrics
                        serviceCount,
                        heuristics.get(sc.getPlacementLogic()),
                        resStats[0],  // mean resource utilization
                        resStats[1],  // stddev resource utilization
                        latStats[0],  // mean latency
                        latStats[1],  // stddev latency
                        failStats,    // failure ratio
                        performanceMetrics.get(i).executionTimeMs,
                        peakMemoryMB,
                        baselineMemoryMB,
                        postGCMemoryMB,
                        memoryGrowthMB,
                        memoryRetentionMB,
                        cloudEnergy,
                        avgEdgeEnergy,
                        stdDevEdgeEnergy
                ));
                
                // Add a new row specifically for EDGE_SERVERS
                fileWriter.append(String.format(
                        "%d,%d,%d,%s,%s,%s,%f,%f,%f,%f,%f,%d,%d,%d,%d,%d,%d,%f,%f,%f\n",
                        i,
                        sc.getNumberOfEdge(),
                        0,  // No users for edge servers row
                        "EDGE_SERVERS", // Specific row for edge servers
                        serviceCount,
                        heuristics.get(sc.getPlacementLogic()),
                        0.0,  // N/A for pure edge servers
                        0.0,  // N/A for pure edge servers
                        0.0,  // N/A for pure edge servers
                        0.0,  // N/A for pure edge servers
                        0.0,  // N/A for pure edge servers
                        0,    // N/A for pure edge servers
                        0,    // N/A for pure edge servers
                        0,    // N/A for pure edge servers
                        0,    // N/A for pure edge servers
                        0,    // N/A for pure edge servers
                        0,    // N/A for pure edge servers
                        0.0,  // No cloud energy for edge servers row
                        avgEdgeEnergy,     // Edge server energy
                        stdDevEdgeEnergy   // Edge server energy stddev
                ));
            }
            
            // Then, write metrics classified by userType for each simulation
            for (int i = 0; i < simConfigs.size(); i++) {
                if (i >= allUtilizations.size() || i >= allLatencies.size() || 
                    i >= allFailedPRs.size() || i >= allTotalPRs.size()) {
                    System.err.println("Warning: Insufficient data for simulation " + i);
                    continue;
                }
                
                SimulationConfig sc = simConfigs.get(i);
                
                // Process and classify metrics by userType for this simulation
                Map<Double, Map<PlacementRequest, Double>> utilizationValues = allUtilizations.get(i);
                Map<Double, Map<PlacementRequest, Double>> latencyValues = allLatencies.get(i);
                Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> failedPRs = allFailedPRs.get(i);
                Map<Double, Integer> totalPRs = allTotalPRs.get(i);
                
                Map<String, List<Double>> resourceDataByType = classifyResourceUtilizationByUserType(utilizationValues);
                Map<String, List<Double>> latencyDataByType = classifyLatencyByUserType(latencyValues);
                Map<String, Map<String, Object>> failedPRDataByType = classifyFailedPRsByUserType(failedPRs, totalPRs);
                
                // Get the service count (app loop length) based on whether it's new or legacy format
                String serviceCount = sc.getNumberOfApplications() > 0 
                                     ? String.valueOf(sc.getAppLoopLength()) 
                                     : "Legacy";
                
                // Get memory metrics
                long peakMemoryMB = performanceMetrics.get(i).getPeakMemoryBytes() / (1024 * 1024);
                long baselineMemoryMB = performanceMetrics.get(i).getBaselineMemoryBytes() / (1024 * 1024);
                long postGCMemoryMB = performanceMetrics.get(i).getPostGCMemoryBytes() / (1024 * 1024);
                long memoryGrowthMB = performanceMetrics.get(i).getMemoryGrowthBytes() / (1024 * 1024);
                long memoryRetentionMB = performanceMetrics.get(i).getMemoryRetentionBytes() / (1024 * 1024);
                
                for (String userType : resourceDataByType.keySet()) {
                    List<Double> resourceValues = resourceDataByType.getOrDefault(userType, Collections.emptyList());
                    List<Double> latencyValues1 = latencyDataByType.getOrDefault(userType, Collections.emptyList());
                    
                    double[] resStats = calculateStatistics(resourceValues);
                    double[] latStats = calculateStatistics(latencyValues1);
                    
                    double failStats = 0.0;
                    if (failedPRDataByType.containsKey(userType)) {
                        Map<String, Object> failData = failedPRDataByType.get(userType);
                        int totalFailures = (int) failData.getOrDefault("totalFailures", 0);
                        int totalPRCount = (int) failData.getOrDefault("totalPRs", 1); // Avoid division by zero
                        failStats = (double) totalFailures / totalPRCount;
                    }
                    
                    // Get the number of users for this specific user type
                    int usersOfThisType = sc.getUsersPerType().getOrDefault(userType, 0);
                    
                    // Get cloud energy - this is shared across all types
                    double cloudEnergy = getCloudEnergyConsumption(i);
                    
                    // Get user-type specific energy metrics if available
                    double userTypeEnergy = getUserTypeEnergyConsumption(userType, i);
                    double userTypeEnergyStdDev = getUserTypeEnergyStdDev(userType, i);
                    
                    // Fall back to aggregate edge metrics if type-specific ones aren't available
                    if (userTypeEnergy == 0.0 && !userType.equals("Aggregate")) {
                        userTypeEnergy = getAvgEdgeEnergyConsumption(i);
                        userTypeEnergyStdDev = getStdDevEdgeEnergyConsumption(i);
                    }
                    
                    fileWriter.append(String.format(
                            "%d,%d,%d,%s,%s,%s,%f,%f,%f,%f,%f,%d,%d,%d,%d,%d,%d,%f,%f,%f\n",
                            i,
                            sc.getNumberOfEdge(),
                            usersOfThisType,
                            userType,
                            serviceCount,
                            heuristics.get(sc.getPlacementLogic()),
                            resStats[0],
                            resStats[1],
                            latStats[0],
                            latStats[1],
                            failStats,
                            performanceMetrics.get(i).executionTimeMs,
                            peakMemoryMB,
                            baselineMemoryMB,
                            postGCMemoryMB,
                            memoryGrowthMB,
                            memoryRetentionMB,
                            cloudEnergy,
                            userTypeEnergy,          // User device energy (now clearly named in header)
                            userTypeEnergyStdDev     // User device energy stddev (now clearly named in header)
                    ));
                }
            }
        }
    }

    public static class PerformanceMetrics {
        SimulationConfig config;
        long executionTimeMs;
        long peakMemoryBytes;
        long baselineMemoryBytes; // Memory before simulation starts
        long postGCMemoryBytes;   // Memory after simulation and forced GC
        
        // New power-related fields
        double cloudEnergyConsumption;     // Total energy consumption by cloud in watt-seconds
        double avgEdgeEnergyConsumption;   // Average energy consumption by edge servers in watt-seconds
        double stdDevEdgeEnergyConsumption; // Standard deviation of edge energy consumption
        
        public PerformanceMetrics(SimulationConfig config) {
            this.config = config;
        }

        public void setExecutionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
        }

        public void setPeakMemoryBytes(long peakMemoryBytes) {
            this.peakMemoryBytes = peakMemoryBytes;
        }
        
        public void setBaselineMemoryBytes(long baselineMemoryBytes) {
            this.baselineMemoryBytes = baselineMemoryBytes;
        }
        
        public void setPostGCMemoryBytes(long postGCMemoryBytes) {
            this.postGCMemoryBytes = postGCMemoryBytes;
        }
        
        // New setters for power metrics
        public void setCloudEnergyConsumption(double cloudEnergyConsumption) {
            this.cloudEnergyConsumption = cloudEnergyConsumption;
        }
        
        public void setAvgEdgeEnergyConsumption(double avgEdgeEnergyConsumption) {
            this.avgEdgeEnergyConsumption = avgEdgeEnergyConsumption;
        }
        
        public void setStdDevEdgeEnergyConsumption(double stdDevEdgeEnergyConsumption) {
            this.stdDevEdgeEnergyConsumption = stdDevEdgeEnergyConsumption;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public long getPeakMemoryBytes() {
            return peakMemoryBytes;
        }
        
        public long getBaselineMemoryBytes() {
            return baselineMemoryBytes;
        }
        
        public long getPostGCMemoryBytes() {
            return postGCMemoryBytes;
        }
        
        // New getters for power metrics
        public double getCloudEnergyConsumption() {
            return cloudEnergyConsumption;
        }
        
        public double getAvgEdgeEnergyConsumption() {
            return avgEdgeEnergyConsumption;
        }
        
        public double getStdDevEdgeEnergyConsumption() {
            return stdDevEdgeEnergyConsumption;
        }
        
        /**
         * Calculates memory growth during simulation
         * (difference between peak and baseline)
         */
        public long getMemoryGrowthBytes() {
            return peakMemoryBytes - baselineMemoryBytes;
        }
        
        /**
         * Calculates memory retention after GC
         * (difference between post-GC and baseline)
         */
        public long getMemoryRetentionBytes() {
            return postGCMemoryBytes - baselineMemoryBytes;
        }
    }
}
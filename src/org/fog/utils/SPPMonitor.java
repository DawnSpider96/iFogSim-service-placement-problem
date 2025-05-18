package org.fog.utils;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.PlacementRequest;
import org.fog.entities.ContextPlacementRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SPPMonitor {

    private int simulationRoundNumber = 0;
    
    // Temporary storage for current simulation metrics
    private static Map<Double, Map<PlacementRequest, Double>> currentUtilizations = new HashMap<>();
    private static Map<Double, Map<PlacementRequest, Double>> currentLatencies = new HashMap<>();
    private static Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> currentFailedPRs = new HashMap<>();
    private static Map<Double, Integer> currentTotalPRs = new HashMap<>();
    
    // Path for temporary CSV files
    private static String tempDir = "./temp_metrics/";
    private static String tempMetricsFile = null;
    private static String tempFailedPRsFile = null;
    
    // Temporary storage for utilization values that will be combined with latency later
    private static Map<PlacementRequest, Double> tempUtilizations = new HashMap<>();

    private SPPMonitor() {
        // Initialize temp directory
        File dir = new File(tempDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static class MyMonitorHolder {
        private static final SPPMonitor INSTANCE = new SPPMonitor();
    }

    public static SPPMonitor getInstance() {
        return MyMonitorHolder.INSTANCE;
    }

    /**
     * Sets up temporary file paths for the current simulation
     * @param simId Unique identifier for the current simulation
     */
    public void initializeSimulation(int simId) {
        // Clear any previous data
        clearCurrentSimulationData();
        
        // Set up temporary file paths
        tempMetricsFile = tempDir + "sim_" + simId + "_metrics.csv";
        tempFailedPRsFile = tempDir + "sim_" + simId + "_failed.csv";
        
        // Create metrics CSV header
        try (FileWriter writer = new FileWriter(tempMetricsFile)) {
            writer.write("timestamp,prId,sensorId,userType,utilization,latency\n");
        } catch (IOException e) {
            System.err.println("Error creating temporary metrics file: " + e.getMessage());
        }
        
        // Create failed PRs CSV header
        try (FileWriter writer = new FileWriter(tempFailedPRsFile)) {
            writer.write("timestamp,prId,sensorId,userType,failureReason,totalPRs\n");
        } catch (IOException e) {
            System.err.println("Error creating temporary failed PRs file: " + e.getMessage());
        }
    }
    
    /**
     * Clears all data from the current simulation
     */
    public void clearCurrentSimulationData() {
        currentUtilizations.clear();
        currentLatencies.clear();
        currentFailedPRs.clear();
        currentTotalPRs.clear();
        tempUtilizations.clear();
    }

    /**
     * Temporarily stores utilization for a PR to be recorded later with latency
     * @param pr The placement request
     * @param utilization The utilization value
     */
    public void storeUtilizationForPR(PlacementRequest pr, double utilization) {
        tempUtilizations.put(pr, utilization);
    }

    /**
     * Records both utilization and latency for a PR at the same timestamp
     * Uses utilization values previously stored with storeUtilizationForPR
     * @param pr The placement request
     * @param timestamp The timestamp
     * @param latency The latency value
     */
    public void recordMetricsForPR(PlacementRequest pr, double timestamp, double latency) {
        if (tempUtilizations.containsKey(pr)) {
            double utilization = tempUtilizations.get(pr);
            
            // Store in memory for current simulation
            if (!currentUtilizations.containsKey(timestamp)) {
                currentUtilizations.put(timestamp, new HashMap<>());
            }
            currentUtilizations.get(timestamp).put(pr, utilization);
            
            if (!currentLatencies.containsKey(timestamp)) {
                currentLatencies.put(timestamp, new HashMap<>());
            }
            currentLatencies.get(timestamp).put(pr, latency);
            
            // Write to temporary CSV file
            appendMetricsToCSV(pr, timestamp, utilization, latency);
            
            // Remove from temporary storage
            tempUtilizations.remove(pr);
        }
        else {
            throw new NullPointerException("No utilization found for PR: " + pr.getSensorId());
        }
    }
    
    /**
     * Writes metrics for a single PR to the temporary CSV file
     */
    private void appendMetricsToCSV(PlacementRequest pr, double timestamp, double utilization, double latency) {
        if (tempMetricsFile == null) {
            System.err.println("Error: Temporary metrics file not initialized. Call initializeSimulation first.");
            return;
        }
        
        try (FileWriter writer = new FileWriter(tempMetricsFile, true)) {
            String userType = "unknown";
            if (pr instanceof ContextPlacementRequest) {
                userType = ((ContextPlacementRequest) pr).getUserType();
            }
            
            writer.write(String.format("%.2f,%d,%d,%s,%.6f,%.6f\n", 
                timestamp, 
                ((ContextPlacementRequest) pr).getPrIndex(),
                pr.getSensorId(), 
                userType,
                utilization, 
                latency));
        } catch (IOException e) {
            System.err.println("Error writing to temporary metrics file: " + e.getMessage());
        }
    }

    /**
     * Records a failed placement request with the current simulation time and failure reason
     *
     * @param pr The placement request that failed
     * @param reason A string describing the reason for failure
     */
    public void recordFailedPR(PlacementRequest pr, MicroservicePlacementConfig.FAILURE_REASON reason) {
        double currentTime = CloudSim.clock();
        
        // Store in memory for current simulation
        if (!currentFailedPRs.containsKey(currentTime)) {
            currentFailedPRs.put(currentTime, new HashMap<>());
        }
        currentFailedPRs.get(currentTime).put(pr, reason);
        
        // Write to temporary CSV file
        appendFailedPRToCSV(pr, currentTime, reason);
    }
    
    /**
     * Writes failed PR data to the temporary CSV file
     */
    private void appendFailedPRToCSV(PlacementRequest pr, double timestamp, MicroservicePlacementConfig.FAILURE_REASON reason) {
        if (tempFailedPRsFile == null) {
            System.err.println("Error: Temporary failed PRs file not initialized. Call initializeSimulation first.");
            return;
        }
        
        try (FileWriter writer = new FileWriter(tempFailedPRsFile, true)) {
            String userType = "unknown";
            if (pr instanceof ContextPlacementRequest) {
                userType = ((ContextPlacementRequest) pr).getUserType();
            }
            
            int totalPRsAtTime = currentTotalPRs.getOrDefault(timestamp, 0);
            
            writer.write(String.format("%.2f,%d,%d,%s,%s,%d\n", 
                timestamp, 
                ((ContextPlacementRequest) pr).getPrIndex(),
                pr.getSensorId(), 
                userType,
                reason.toString(),
                totalPRsAtTime));
        } catch (IOException e) {
            System.err.println("Error writing to temporary failed PRs file: " + e.getMessage());
        }
    }

    /**
     * Records the total number of PRs at a given timestamp
     * @param total The total number of PRs
     * @param timestamp The timestamp
     */
    public void recordTotalPRs(int total, double timestamp) {
        // Store in memory for current simulation
        currentTotalPRs.put(timestamp, total);
    }
    
    /**
     * Gets the path to the temporary metrics file for the current simulation
     * @return Path to the temporary metrics file
     */
    public String getTempMetricsFile() {
        return tempMetricsFile;
    }
    
    /**
     * Gets the path to the temporary failed PRs file for the current simulation
     * @return Path to the temporary failed PRs file
     */
    public String getTempFailedPRsFile() {
        return tempFailedPRsFile;
    }
    
    /**
     * Get a map of the current simulation's utilization metrics
     * @return Map of timestamp to PR utilization values
     */
    public Map<Double, Map<PlacementRequest, Double>> getCurrentUtilizations() {
        return currentUtilizations;
    }
    
    /**
     * Get a map of the current simulation's latency metrics
     * @return Map of timestamp to PR latency values
     */
    public Map<Double, Map<PlacementRequest, Double>> getCurrentLatencies() {
        return currentLatencies;
    }
    
    /**
     * Get a map of the current simulation's failed PR metrics
     * @return Map of timestamp to failed PRs
     */
    public Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> getCurrentFailedPRs() {
        return currentFailedPRs;
    }
    
    /**
     * Get a map of the current simulation's total PR counts
     * @return Map of timestamp to total PR count
     */
    public Map<Double, Integer> getCurrentTotalPRs() {
        return currentTotalPRs;
    }

    public void incrementSimulationRoundNumber() {
        simulationRoundNumber++;
    }
}

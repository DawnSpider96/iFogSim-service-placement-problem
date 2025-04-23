package org.fog.utils;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.PlacementRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyMonitor {

    // TODO Simon says we want the CPU and RAM usage of EVERY FogDevice after EVERY Placement-execution cycle

    private int simulationRoundNumber = 0;
    // timestamp -> List of utilization's for that cycle
    private static List<Map<Double, Map<PlacementRequest, Double>>> utilizations = new ArrayList<>();
    // timestamp -> (PR -> latency of that PR)
    private static List<Map<Double, Map<PlacementRequest, Double>>> latencies = new ArrayList<>();
    // timestamp -> (PR -> reason for failure)
    // Either "Insufficient resources on User Device" or "Placement Failed"
    private static List<Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>>> failedPRs = new ArrayList<>();
    private static List<Map<Double, Integer>> totalPRs = new ArrayList<>();


    //    private static Map<> ramUsages = new HashMap<>();

    private MyMonitor(){
    }

    private static class MyMonitorHolder {
        private static final MyMonitor INSTANCE = new MyMonitor();
    }

    public static MyMonitor getInstance() {
        return MyMonitorHolder.INSTANCE;
    }

//    public static void setCpuUsages(Map cpuUsages) {
//        MyMonitor.cpuUsages = cpuUsages;
//    }

//    public Map<Double, List<MyHeuristic.DeviceState>> getSnapshots() {
//        while (snapshots.size() <= getInstance().simulationRoundNumber) {
//            snapshots.add(new HashMap<>()); // Simon (170225) says it should only add 1
//        }
//        return snapshots.get(getInstance().simulationRoundNumber);
//    }
//
//    public List<Map<Double, List<MyHeuristic.DeviceState>>> getAllSnapshots() {
//        return snapshots;
//    }

    public Map<Double, Map<PlacementRequest, Double>> getUtilizations() {
        while (utilizations.size() <= getInstance().simulationRoundNumber) {
            utilizations.add(new HashMap<>()); // Simon (170225) says it should only add 1
        }
        return utilizations.get(getInstance().simulationRoundNumber);
    }

    public void recordUtilizationForPR(PlacementRequest pr, double timestamp, double utilization) {
        Map<Double, Map<PlacementRequest, Double>> currentUtilizations = getUtilizations();
        if (!currentUtilizations.containsKey(timestamp)) {
            currentUtilizations.put(timestamp, new HashMap<>());
        }
        currentUtilizations.get(timestamp).put(pr, utilization);
    }

    public List<Map<Double, Map<PlacementRequest, Double>>> getAllUtilizations() {
        return utilizations;
    }

    public Map<Double, Map<PlacementRequest, Double>> getLatencies() {
        while (latencies.size() <= getInstance().simulationRoundNumber) {
            latencies.add(new HashMap<>()); // Simon (170225) says it should only add 1
        }
        return latencies.get(getInstance().simulationRoundNumber);
    }

    public List<Map<Double, Map<PlacementRequest, Double>>> getAllLatencies() {
        return latencies;
    }

    public Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> getFailedPRs() {
        while (failedPRs.size() <= getInstance().simulationRoundNumber) {
            failedPRs.add(new HashMap<>());
        }
        return failedPRs.get(getInstance().simulationRoundNumber);
    }

    public List<Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>>> getAllFailedPRs() {
        // Case: Last few simulations have no failed PRs. Note the strictly less than operator.
        while (failedPRs.size() < getInstance().simulationRoundNumber) {
            failedPRs.add(new HashMap<>());
        }
        return failedPRs;
    }

    public List<Map<Double, Integer>> getAllTotalPRs() {
        return totalPRs;
    }

    /**
     * Records a failed placement request with the current simulation time and failure reason
     *
     * @param pr The placement request that failed
     * @param reason A string describing the reason for failure
     */
    public void recordFailedPR(PlacementRequest pr, MicroservicePlacementConfig.FAILURE_REASON reason) {
        Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> currentFailedPRs = getFailedPRs();

        double currentTime = CloudSim.clock();
        if (!currentFailedPRs.containsKey(currentTime)) {
            currentFailedPRs.put(currentTime, new HashMap<>());
        }

        currentFailedPRs.get(currentTime).put(pr, reason);
    }

    public void recordTotalPRs(int total, double timestamp) {
        while (totalPRs.size() <= getInstance().simulationRoundNumber) {
            totalPRs.add(new HashMap<>()); // Simon (170225) says it should only add 1
        }
        totalPRs.get(getInstance().simulationRoundNumber).put(timestamp, total);
    }



    public void incrementSimulationRoundNumber() {
        simulationRoundNumber++;
    }

//    public static void setSnapshots(Map<Double, List<MyHeuristic.DeviceState>> snapshots) {
//        MyMonitor.snapshots = snapshots;
//    }

    //    public static Map getRamUsages() {
//        return ramUsages;
//    }
//
//    public static void setRamUsages(Map ramUsages) {
//        MyMonitor.ramUsages = ramUsages;
//    }
}

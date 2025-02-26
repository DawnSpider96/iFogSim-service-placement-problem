package org.fog.utils;

import org.cloudbus.cloudsim.Cloudlet;
import org.fog.entities.PlacementRequest;
import org.fog.placement.MyHeuristic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyMonitor {

    // TODO Simon says we want the CPU and RAM usage of EVERY FogDevice after EVERY Placement-execution cycle

    private int simulationRoundNumber = 0;
    // device -> (time -> Amount used)
    private static List<Map<Integer, Map<Double, Cloudlet>>> cpuUsages = new ArrayList<>();
    // timestamp -> List<DeviceState> (DeviceState snapshot at that time)
    private static List<Map<Double, List<MyHeuristic.DeviceState>>> snapshots = new ArrayList<>();
    // timestamp -> (PR -> latency of that PR)
    private static List<Map<Double, Map<PlacementRequest, Double>>> latencies = new ArrayList<>();

    //    private static Map<> ramUsages = new HashMap<>();

    private MyMonitor(){
    }

    private static class MyMonitorHolder {
        private static final MyMonitor INSTANCE = new MyMonitor();
    }

    public static MyMonitor getInstance() {
        return MyMonitorHolder.INSTANCE;
    }

    public Map getCpuUsages() {
        while (cpuUsages.size() <= getInstance().simulationRoundNumber) {
            cpuUsages.add(new HashMap<>()); // Simon (170225) says it should only add 1
        }
        return cpuUsages.get(getInstance().simulationRoundNumber);
    }

//    public static void setCpuUsages(Map cpuUsages) {
//        MyMonitor.cpuUsages = cpuUsages;
//    }

    public Map<Double, List<MyHeuristic.DeviceState>> getSnapshots() {
        while (snapshots.size() <= getInstance().simulationRoundNumber) {
            snapshots.add(new HashMap<>()); // Simon (170225) says it should only add 1
        }
        return snapshots.get(getInstance().simulationRoundNumber);
    }

    public List<Map<Double, List<MyHeuristic.DeviceState>>> getAllSnapshots() {
        return snapshots;
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

package org.fog.utils;

import org.cloudbus.cloudsim.Cloudlet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyMonitor {

    // TODO Simon says we want the CPU and RAM usage of EVERY FogDevice after EVERY Placement-execution cycle

    // device -> (time -> Amount used)
    private static Map<Integer, Map<Double, Cloudlet>> cpuUsages = new HashMap<>();

//    private static Map<> ramUsages = new HashMap<>();

    public static Map getCpuUsages() {
        return cpuUsages;
    }

    public static void setCpuUsages(Map cpuUsages) {
        MyMonitor.cpuUsages = cpuUsages;
    }

//    public static Map getRamUsages() {
//        return ramUsages;
//    }
//
//    public static void setRamUsages(Map ramUsages) {
//        MyMonitor.ramUsages = ramUsages;
//    }
}

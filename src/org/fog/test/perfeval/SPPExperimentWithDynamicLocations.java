//package org.fog.test.perfeval;
//
//import org.fog.entities.SPPFogDevice;
//import org.fog.placement.PlacementLogicFactory;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Demo class showing how to use SPPExperiment with dynamic location generation.
// * This will generate locations on-the-fly based on the grid and random distribution
// * models from the other simulation system.
// */
//public class SPPExperimentWithDynamicLocations {
//
//    public static void main(String[] args) {
//        // Enable dynamic location generation
//        SPPExperiment.setUseDynamicLocations(true);
//
//        // Set custom output directory for generated files (optional)
//        SPPExperiment.setOutputDirectory("./dataset/simon");
//
//        // Create a simple configuration for testing
//        SimulationConfig config = createTestConfig();
//
//        // Run the simulation with dynamic locations
//        runSimulation(config);
//    }
//
//    /**
//     * Creates a test configuration with a small number of devices
//     */
//    private static SimulationConfig createTestConfig() {
//        // User types and counts
//        Map<String, Integer> usersPerType = new HashMap<>();
//        usersPerType.put(SPPFogDevice.GENERIC_USER, 10);  // 10 generic users
//        usersPerType.put(SPPFogDevice.AMBULANCE_USER, 2); // 2 ambulance users
//        usersPerType.put(SPPFogDevice.OPERA_USER, 3);     // 3 opera users
//
//        // PR generation intervals (in seconds)
//        Map<String, Integer> intervalValues = new HashMap<>();
//        intervalValues.put(SPPFogDevice.GENERIC_USER, 60);     // Every minute
//        intervalValues.put(SPPFogDevice.AMBULANCE_USER, 30);   // Every 30 seconds
//        intervalValues.put(SPPFogDevice.OPERA_USER, 45);       // Every 45 seconds
//
//        // Get placement logic code from string name
//        int placementLogicCode = PlacementLogicFactory.getPlacementLogicCode("CLOSEST_FIT");
//
//        // Create a configuration with 9 edge servers
//        // This will result in a 3x3 grid placement
//        return new SimulationConfig(
//            9,                  // numberOfEdge
//            placementLogicCode, // placementLogic (as int code)
//            2,                  // numberOfApplications
//            3,                  // appLoopLength
//            usersPerType,       // usersPerType
//            intervalValues,     // intervalValues
//            42,                 // experimentSeed
//            123,                // locationSeed
//            456                 // mobilityStrategySeed
//        );
//    }
//
//    /**
//     * Runs the SPPExperiment with the given configuration
//     */
//    private static void runSimulation(SimulationConfig config) {
//        // We can directly use the main class's run method
//        System.out.println("Starting simulation with dynamic locations...");
//        System.out.println("Configuration: " + config);
//
//        // This will internally use dynamic location generation
//        try {
//            SPPExperiment.main(new String[0]);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
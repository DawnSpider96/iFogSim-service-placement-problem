package org.fog.utils;

public class MicroservicePlacementConfig {

    // simulation modes - STATIC - 1(initial placement happens before simulation start and placement related delays are not simulated)
    // DYNAMIC - 2(placement happens after simulation starts and placement related delays are simulated)
    public static String SIMULATION_MODE = "DYNAMIC";

    //Placement Request Processing Mode
    public static String PERIODIC = "Periodic";
    public static String SEQUENTIAL = "Sequential";
    public static String PR_PROCESSING_MODE = PERIODIC;

    // For periodic placement
    public static final double PLACEMENT_PROCESS_INTERVAL = 10;
    public static final double PLACEMENT_GENERATE_INTERVAL = 70;

    //Resource info sharing among cluster nodes
    public static Boolean ENABLE_RESOURCE_DATA_SHARING = false;
    public static double MODULE_DEPLOYMENT_TIME = 0.0;
    public static double EXECUTION_TIMEOUT_TIME = 30.0;

    // Certain Placement Logic will behave differently according to the Topology of the network
    // TODO Simon says this is not the proper way to configure. This configuration needs to sync with the actual topology as defined in the simulation file
    public static final String CENTRALISED = "centralised";
    public static final String FEDERATED = "federated";
    public static final String DECENTRALISED = "decentralised";
    public static String NETWORK_TOPOLOGY = CENTRALISED;
}

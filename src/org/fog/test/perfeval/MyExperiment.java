package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.example.policies.VmSchedulerTimeSharedEnergy;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.MyApplication;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.mobilitydata.Location;
import org.fog.mobilitydata.References;
import org.fog.placement.MyMicroservicesMobilityController;
import org.fog.placement.PlacementLogicFactory;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.*;
import org.fog.utils.distribution.DeterministicDistribution;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Platform to run the OnlinePOC simulation under variable parameters:
 *      1. Number of edge servers
 *      2. Length of AppLoop
 *      3. Placement Logic
 *
 * Metrics:
 *      1. Average Latency
 *      2. CPU Utilization
 *
 * @author Joseph Poon
 */

/**
 * Config properties
 * SIMULATION_MODE -> dynamic or static
 * PR_PROCESSING_MODE -> PERIODIC
 * ENABLE_RESOURCE_DATA_SHARING -> false (not needed as FONs placed at the highest level.
 * DYNAMIC_CLUSTERING -> true (for clustered) and false (for not clustered) * (also compatible with static clustering)
 */
public class MyExperiment {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    // Constants for CSV file paths and data configurations
    private static final String USERS_LOCATION_PATH = "./dataset/usersLocation-melbCBD_Experiments.csv";
    private static final String RESOURCES_LOCATION_PATH = "./dataset/edgeResources-melbCBD_Experiments.csv";
    static double SENSOR_TRANSMISSION_TIME = 10;

    public static void main(String[] args) {
        List<SimulationConfig> configs = Arrays.asList(
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.ACO),
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.ILP),
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(100, 196, 1, PlacementLogicFactory.SIMULATED_ANNEALING),

                new SimulationConfig(200, 196, 1, PlacementLogicFactory.ACO),
                new SimulationConfig(200, 196, 1, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(200, 196, 1, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(200, 196, 1, PlacementLogicFactory.ILP),
                new SimulationConfig(200, 196, 1, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(200, 196, 1, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(200, 196, 1, PlacementLogicFactory.SIMULATED_ANNEALING),

                new SimulationConfig(300, 196, 1, PlacementLogicFactory.ACO),
                new SimulationConfig(300, 196, 1, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(300, 196, 1, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(300, 196, 1, PlacementLogicFactory.ILP),
                new SimulationConfig(300, 196, 1, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(300, 196, 1, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(300, 196, 1, PlacementLogicFactory.SIMULATED_ANNEALING),
                //
                //
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.ACO),
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.ILP),
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(100, 196, 3, PlacementLogicFactory.SIMULATED_ANNEALING),

                new SimulationConfig(200, 196, 3, PlacementLogicFactory.ACO),
                new SimulationConfig(200, 196, 3, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(200, 196, 3, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(200, 196, 3, PlacementLogicFactory.ILP),
                new SimulationConfig(200, 196, 3, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(200, 196, 3, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(200, 196, 3, PlacementLogicFactory.SIMULATED_ANNEALING),

                new SimulationConfig(300, 196, 3, PlacementLogicFactory.ACO),
                new SimulationConfig(300, 196, 3, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(300, 196, 3, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(300, 196, 3, PlacementLogicFactory.ILP),
                new SimulationConfig(300, 196, 3, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(300, 196, 3, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(300, 196, 3, PlacementLogicFactory.SIMULATED_ANNEALING),
                //
                //
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.ACO),
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.ILP),
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(100, 196, 5, PlacementLogicFactory.SIMULATED_ANNEALING),

                new SimulationConfig(200, 196, 5, PlacementLogicFactory.ACO),
                new SimulationConfig(200, 196, 5, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(200, 196, 5, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(200, 196, 5, PlacementLogicFactory.ILP),
                new SimulationConfig(200, 196, 5, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(200, 196, 5, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(200, 196, 5, PlacementLogicFactory.SIMULATED_ANNEALING),

                new SimulationConfig(300, 196, 5, PlacementLogicFactory.ACO),
                new SimulationConfig(300, 196, 5, PlacementLogicFactory.BEST_FIT),
                new SimulationConfig(300, 196, 5, PlacementLogicFactory.CLOSEST_FIT),
                new SimulationConfig(300, 196, 5, PlacementLogicFactory.ILP),
                new SimulationConfig(300, 196, 5, PlacementLogicFactory.MAX_FIT),
                new SimulationConfig(300, 196, 5, PlacementLogicFactory.MULTI_OPT),
                new SimulationConfig(300, 196, 5, PlacementLogicFactory.SIMULATED_ANNEALING)
        );

        for (SimulationConfig config : configs) {
            run(config);
        }
        
        // Print final entity ID information
        System.out.println("\n========= FINAL SUMMARY =========");
        System.out.println("Final ENTITY_ID: " + FogUtils.getCurrentEntityId());
        System.out.println("Final TUPLE_ID: " + FogUtils.getCurrentTupleId());
        System.out.println("Final ACTUAL_TUPLE_ID: " + FogUtils.getCurrentActualTupleId());
        System.out.println("=================================\n");

        // (170225) For ease of debugging only
        MyMonitor mm = MyMonitor.getInstance();

        try {
//            List<Map<String, Map<Double, Double>>> resourceData =
//                    MyMonitor.getInstance().getAllSnapshots().stream()
//                    .map(MetricUtils::handleSimulationResource)
//                    .collect(Collectors.toList());
            List<List<Double>> resourceData =
                    mm.getAllSnapshots().stream()
                            .map(MetricUtils::handleSimulationResource)
                            .collect(Collectors.toList());
            List<List<Double>> latencyData =
                    mm.getAllLatencies().stream()
                            .map(MetricUtils::handleSimulationLatency)
                            .collect(Collectors.toList());
            MetricUtils.writeResourceDistributionToCSV(resourceData, latencyData, configs, "./output/resourceDist_Crowded_U00.csv");
            System.out.println("CSV file has been created successfully.");
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the CSV file.");
            e.printStackTrace();
        }
    }


    private static void run(SimulationConfig simulationConfig) {
        System.out.println("Starting Simon's Experiment...");
        System.out.println(simulationConfig.toString());

        // Debug: Print entity and tuple IDs before reset
        System.out.println("Before reset - ENTITY_ID: " + FogUtils.getCurrentEntityId() + 
                           ", TUPLE_ID: " + FogUtils.getCurrentTupleId());

        try {
            CloudSim.stopSimulation();
        } catch (Exception e) {
            // Ignore errors if no simulation is running
        }

        // Reset ENTITY_ID and related counters FIRST
        FogUtils.clear();
        org.cloudbus.cloudsim.network.datacenter.NetworkConstants.clear();
        TimeKeeper.deleteInstance();
        FogBroker.clear();

        // Debug: Print entity and tuple IDs after reset
        System.out.println("After reset - ENTITY_ID: " + FogUtils.getCurrentEntityId() + 
                           ", TUPLE_ID: " + FogUtils.getCurrentTupleId());

        // Reset THIS class's temporary state
        // Simon (040225) says MyMonitor is NOT reset
        fogDevices.clear();
        sensors.clear();
        actuators.clear();
        
        try {
            Log.enable();
            Logger.ENABLED = true;
            int numberOfEdge = simulationConfig.numberOfEdge;
            int numberOfUser = simulationConfig.numberOfUser;
            int appLoopLength = simulationConfig.appLoopLength;
            int placementLogicType = simulationConfig.placementLogic;
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);
            
            // Debug: Print entity IDs after CloudSim.init
            System.out.println("After CloudSim.init - ENTITY_ID: " + FogUtils.getCurrentEntityId());

            String appId = "SimonApp"; // identifier of the application

            FogBroker broker = new FogBroker("broker");
            CloudSim.setFogBrokerId(broker.getId());
            
            // Debug: Print broker ID
            System.out.println("FogBroker ID: " + broker.getId());

            MyApplication application = createApplication(appId, broker.getId(), appLoopLength);
            application.setUserId(broker.getId());

            // Simon (140125) says tuples will be sent to FogDevices and executed under mService1
            // because source module is clientModule but dest module is mService1
            // TODO Change accordingly if the AppLoop ever changes (or there are more AppLoops)
            FogBroker.getApplicationToFirstMicroserviceMap().put(application, "clientModule");
            List<String> simonAppSecondMicroservices = new ArrayList<>();
            simonAppSecondMicroservices.add("mService1");
            FogBroker.getApplicationToSecondMicroservicesMap().put(application, simonAppSecondMicroservices);

            // Create fog devices (cloud, gateways, and mobile devices)
            createFogDevices(broker.getId(), application, numberOfEdge, numberOfUser);

            /**
             * Central controller for performing preprocessing functions
             */
            List<Application> appList = new ArrayList<>();
            appList.add(application);

            MyMicroservicesMobilityController microservicesController = new MyMicroservicesMobilityController(
                "controller", fogDevices, sensors, appList, placementLogicType);
                
            // Initialize location data from CSV files
            try {
                System.out.println("Initializing location data from CSV files...");
                microservicesController.initializeLocationData(
                    RESOURCES_LOCATION_PATH,
                    USERS_LOCATION_PATH,
                    numberOfEdge,
                    numberOfUser
                );
                System.out.println("Location data initialization complete.");
                
                // Complete initialization now that location data is loaded
                microservicesController.completeInitialization();
                System.out.println("Controller initialization completed with proximity-based connections.");
            } catch (IOException e) {
                System.out.println("Failed to initialize location data: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // todo Simon (100425) says we do this in controller.connectWithLatencies instead
//            for (FogDevice device : fogDevices) {
//                if (((MyFogDevice)device).getDeviceType().equals(MyFogDevice.GENERIC_USER) ||
//                        ((MyFogDevice)device).getDeviceType().equals(MyFogDevice.AMBULANCE_USER) ||
//                        ((MyFogDevice)device).getDeviceType().equals(MyFogDevice.OPERA_USER)) {
//                    microservicesController.registerUserDevice((MyFogDevice)device);
//                    ((MyFogDevice)device).setMicroservicesControllerId(microservicesController.getId());
//                }
//            }

            // generate placement requests
            List<PlacementRequest> placementRequests = new ArrayList<>();
            for (Sensor sensor : sensors) {
                Map<String, Integer> placedMicroservicesMap = new LinkedHashMap<>();
                placedMicroservicesMap.put("clientModule", sensor.getGatewayDeviceId());

                // Use MyMicroservicesController to create PROTOTYPE placement request
                //  They will not be processed, but used as template for periodic generation.
                PlacementRequest prototypePR = microservicesController.createPlacementRequest(
                        sensor, placedMicroservicesMap);
                placementRequests.add(prototypePR);
            }

            // TODO Simon says now we need to give them time intervals to send periodically across the Simulation
            microservicesController.submitPlacementRequests(placementRequests, 1);

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            Log.printLine("Simon app START!");
            Log.printLine(String.format("Placement Logic: %d", placementLogicType));

            CloudSim.startSimulation();
//            CloudSim.stopSimulation();
            // TODO Possible mega cleanup/metric collection function
            MyMonitor.getInstance().incrementSimulationRoundNumber();
            System.out.println("Simon app finished!");
            
            // Reset controller's sequence counters after simulation finishes
            microservicesController.resetSequenceCounters();
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates fog devices for the simulation.
     * This method creates a cloud device, gateway devices, and user devices.
     * It now handles device creation without relying on the LocationHandler.
     *
     * @param userId User ID for the broker
     * @param app Application to be deployed
     * @param numberOfEdge Number of edge devices to create
     * @param numberOfUser Number of user devices to create
     */
    private static void createFogDevices(int userId, Application app, int numberOfEdge, int numberOfUser) {
        // Create cloud device at the top of the hierarchy
        MyFogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0.01, 16 * 103, 16 * 83.25, MyFogDevice.CLOUD);
        cloud.setParentId(References.NOT_SET);
        cloud.setLevel(0);
        fogDevices.add(cloud);

        // Create gateway devices
        for (int i = 0; i < numberOfEdge; i++) {
            MyFogDevice gateway = createFogDevice("gateway_" + i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333, MyFogDevice.FCN);
            gateway.setParentId(cloud.getId());
            // Let latency be set by LocationManager in connectWithLatencies
            gateway.setUplinkLatency(100); // Default latency, will be updated by LocationManager
            gateway.setLevel(1);
            fogDevices.add(gateway);
        }

        // Create user devices
        int usersPerGateway = numberOfUser / numberOfEdge;
        int remainingUsers = numberOfUser % numberOfEdge;
        
        int userCount = 0;
        for (int i = 0; i < numberOfEdge; i++) {
            int usersToCreate = usersPerGateway + (i < remainingUsers ? 1 : 0);
            
            for (int j = 0; j < usersToCreate && userCount < numberOfUser; j++) {
                FogDevice mobile = addImmobile("immobile_" + userCount, userId, app, References.NOT_SET);
                // Don't set uplink latency, it will be set by LocationManager based on distance
                mobile.setUplinkLatency(-1);
                mobile.setLevel(2);
                
                fogDevices.add(mobile);
                userCount++;
            }
        }
    }

    /**
     * Creates a vanilla fog device
     *
     * @param nodeName    name of the device to be used in simulation
     * @param mips        MIPS
     * @param ram         RAM
     * @param upBw        uplink bandwidth
     * @param downBw      downlink bandwidth
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    private static MyFogDevice createFogDevice(String nodeName, long mips,
                                               int ram, long upBw, long downBw, double ratePerMips, double busyPower, double idlePower, String deviceType) {

        // Debug: Print entity ID before creating device
        System.out.println("Creating fog device '" + nodeName + "', ENTITY_ID before: " + FogUtils.getCurrentEntityId());
        
        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        System.out.println("Created host with ID: " + hostId);
        
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new VmSchedulerTimeSharedEnergy(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        MyFogDevice fogdevice = null;
        try {
            fogdevice = new MyFogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 10000, 0, ratePerMips, deviceType);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fogdevice;
    }

    private static FogDevice addImmobile(String name, int userId, Application app, int parentId) {
        MyFogDevice mobile = createFogDevice(name, 200, 200, 10000, 270, 0, 87.53, 82.44, MyFogDevice.GENERIC_USER);
        mobile.setParentId(parentId);
        
        Sensor mobileSensor = new MySensor("s-" + name, "SENSOR", userId, app.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
        mobileSensor.setApp(app);
        sensors.add(mobileSensor);
        
        Actuator mobileDisplay = new Actuator("a-" + name, userId, app.getAppId(), "DISPLAY");
        actuators.add(mobileDisplay);

        mobileSensor.setGatewayDeviceId(mobile.getId());
        mobileSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms

        mobileDisplay.setGatewayDeviceId(mobile.getId());
        mobileDisplay.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
        mobileDisplay.setApp(app);

        return mobile;
    }


    private static MyApplication createApplication(String appId, int userId, int numServices) {
        MyApplication application = MyApplication.createMyApplication(appId, userId);

        application.addAppModule("clientModule", 4, 4, 50);
        for (int i = 1; i <= numServices; i++) {
            // Use consistent MIPS for all services (as seen in existing implementations)
            application.addAppModule("mService" + i, 100, 100, 500);
        }

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("SENSOR", "clientModule", 14, 50, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        // TODO Always make tupleCPULength here same value as below. It determines how long mService1 runs.
        application.addAppEdge("clientModule", "mService1", 2000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);

        // Connect microservices in sequence
        for (int i = 1; i < numServices; i++) {
            String sourceModule = "mService" + i;
            String destModule = "mService" + (i+1);
            String tupleType = "FILTERED_DATA" + i;

            application.addAppEdge(sourceModule, destModule, 2000, 500, tupleType, Tuple.UP, AppEdge.MODULE);
        }

        application.addAppEdge("mService" + numServices, "clientModule", 4, 500, "RESULT", Tuple.DOWN, AppEdge.MODULE);
        // NOTE tupleCpuLength and tupleNwLength don't matter; actuator doesn't execute.
        application.addAppEdge("clientModule", "DISPLAY", 4, 50, "RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("clientModule", "SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
        for (int i = 1; i < numServices; i++) {
            String sourceModule = "mService" + i;
            String inputTupleType = (i == 1) ? "RAW_DATA" : "FILTERED_DATA" + (i-1);
            String outputTupleType = "FILTERED_DATA" + i;

            application.addTupleMapping(sourceModule, inputTupleType, outputTupleType, new FractionalSelectivity(1.0));
        }
        String lastInputTupleType = (numServices == 1) ? "RAW_DATA" : "FILTERED_DATA" + (numServices-1);
        application.addTupleMapping("mService" + numServices, lastInputTupleType, "RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "RESULT", "RESULT_DISPLAY", new FractionalSelectivity(1.0));

        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("clientModule");

            for (int i = 1; i <= numServices; i++) {
                add("mService" + i);
            }

            add("clientModule");
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop);
        }};
        application.setLoops(loops);

        return application;
    }
}


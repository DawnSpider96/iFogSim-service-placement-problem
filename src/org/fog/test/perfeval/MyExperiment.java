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
import org.fog.mobilitydata.DataParser;
import org.fog.mobilitydata.ExperimentDataParser;
import org.fog.mobilitydata.References;
import org.fog.placement.LocationHandler;
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

    //    static Map<Integer, Integer> userMobilityPattern = new HashMap<Integer, Integer>();
    static LocationHandler locator;

//    static boolean CLOUD = false;

    static double SENSOR_TRANSMISSION_TIME = 10;
//    static int numberOfUser = 196;

    // TODO: 8/8/2021  not required for this scenario
    // if random mobility generator for users is True, new random dataset will be created for each user
//    static boolean randomMobility_generator = true; // To use random datasets
//    static boolean renewDataset = false; // To overwrite existing random datasets
//    static List<Integer> clusteringLevels = new ArrayList<Integer>(); // The selected fog layers for clustering

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

        // (170225) For ease of debugging only
        MyMonitor mm = MyMonitor.getInstance();

        try {
//            List<Map<String, Map<Double, Double>>> resourceData =
//                    MyMonitor.getInstance().getAllSnapshots().stream()
//                    .map(MetricUtils::handleSimulationResource)
//                    .collect(Collectors.toList());
            List<Map<Double, Double>> resourceData =
                    MyMonitor.getInstance().getAllSnapshots().stream()
                            .map(MetricUtils::handleSimulationResource)
                            .collect(Collectors.toList());
            List<Map<Double, Double>> latencyData =
                    MyMonitor.getInstance().getAllLatencies().stream()
                            .map(MetricUtils::handleSimulationLatency)
                            .collect(Collectors.toList());
            MetricUtils.writeResourceDistributionToCSV(resourceData, latencyData, configs, "./output/resourceDistCrowdedScenario.csv");
            System.out.println("CSV file has been created successfully.");
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the CSV file.");
            e.printStackTrace();
        }
    }


    private static void run(SimulationConfig simulationConfig) {

        System.out.println("Starting Simon's Experiment...");
        System.out.println(simulationConfig.toString());

        // Reset THIS class's temporary state
        // Simon (040225) says MyMonitor is NOT reset
        fogDevices.clear();
        sensors.clear();
        actuators.clear();
        locator = null;
        TimeKeeper.deleteInstance();
        FogBroker.clear();
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

            String appId = "SimonApp"; // identifier of the application

            FogBroker broker = new FogBroker("broker");
            CloudSim.setFogBrokerId(broker.getId());

            MyApplication application = createApplication(appId, broker.getId(), appLoopLength);
            application.setUserId(broker.getId());
            // Simon (140125) says tuples will be sent to FogDevices and executed under mService1
            // because source module is clientModule but dest module is mService1
            // TODO Change accordingly if the AppLoop ever changes (or there are more AppLoops)
            FogBroker.getApplicationToFirstMicroserviceMap().put(application, "clientModule");
            List<String> simonAppSecondMicroservices = new ArrayList<>();
            simonAppSecondMicroservices.add("mService1");
            FogBroker.getApplicationToSecondMicroservicesMap().put(application, simonAppSecondMicroservices);
            DataParser dataObject = new ExperimentDataParser();
            locator = new LocationHandler(dataObject);

//            if (randomMobility_generator) {
//                datasetReference = References.dataset_random;
//                createRandomMobilityDatasets(References.random_walk_mobility_model, datasetReference, renewDataset);
//            }

            createImmobileUsers(broker.getId(), application, numberOfUser);
            createFogDevices(broker.getId(), application, numberOfEdge);


            /**
             * Central controller for performing preprocessing functions
             */
            List<Application> appList = new ArrayList<>();
            appList.add(application);

            MyMicroservicesMobilityController microservicesController = new MyMicroservicesMobilityController("controller", fogDevices, sensors, appList, placementLogicType, locator);

            // Register user devices
            for (FogDevice device : fogDevices) {
                if (((MyFogDevice)device).getDeviceType().equals(MyFogDevice.GENERIC_USER) ||
                        ((MyFogDevice)device).getDeviceType().equals(MyFogDevice.AMBULANCE_USER) ||
                        ((MyFogDevice)device).getDeviceType().equals(MyFogDevice.OPERA_USER)) {
                    microservicesController.registerUserDevice((MyFogDevice)device);
                    ((MyFogDevice)device).setMicroservicesControllerId(microservicesController.getId());
                }
            }

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

            MyMonitor.getInstance().incrementSimulationRoundNumber();
            System.out.println("Simon app finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates variable number of fog devices in the physical topology of the simulation.
     *
     * @param userId
     */
    private static void createFogDevices(int userId, Application app, int numberOfEdge) throws NumberFormatException, IOException {
        locator.parseResourceInfo(numberOfEdge);

        if (locator.getLevelWiseResources(locator.getLevelID("Cloud")).size() == 1) {

            FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0.01, 16 * 103, 16 * 83.25, MyFogDevice.CLOUD); // creates the fog device Cloud at the apex of the hierarchy with level=0
            cloud.setParentId(References.NOT_SET);
            locator.linkDataWithInstance(cloud.getId(), locator.getLevelWiseResources(locator.getLevelID("Cloud")).get(0));
            cloud.setLevel(0);
            fogDevices.add(cloud);

//            for (int i = 0; i < locator.getLevelWiseResources(locator.getLevelID("Proxy")).size(); i++) {
//
//                FogDevice proxy = createFogDevice("proxy-server_" + i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333, MyFogDevice.FON); // creates the fog device Proxy Server (level=1)
//                locator.linkDataWithInstance(proxy.getId(), locator.getLevelWiseResources(locator.getLevelID("Proxy")).get(i));
//                proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
//                proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
//                proxy.setLevel(1);
//                fogDevices.add(proxy);
//
//            }

            for (int i = 0; i < locator.getLevelWiseResources(locator.getLevelID("Gateway")).size(); i++) {
                // TODO (NOT Simon says): Depending on the Placement Logic maybe these should be FON instead of FCN
                // I kept the comment to reflect the creator's thought process about Placement Logic.
                // But Simon says these should definitely be FCN. The cloud will sort everything out.
                FogDevice gateway = createFogDevice("gateway_" + i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333, MyFogDevice.FCN);
                locator.linkDataWithInstance(gateway.getId(), locator.getLevelWiseResources(locator.getLevelID("Gateway")).get(i));
                gateway.setParentId(cloud.getId());
                // Simon (020225) says we will determine distance
                gateway.setUplinkLatency(locator.calculateLatencyUsingDistance(cloud.getId(), gateway.getId()));
                gateway.setLevel(1);
                fogDevices.add(gateway);
            }
        }
    }

    private static void createImmobileUsers(int userId, Application app, int numberOfUser) throws IOException {
        // Just keep this empty, I'm too lazy to override the LocationHandler as well
        Map<Integer, Integer> userMobilityPattern = new HashMap<Integer, Integer>();

        locator.parseUserInfo(userMobilityPattern, "./dataset/usersLocation-melbCBD_Experiments.csv", numberOfUser);

        List<String> mobileUserDataIds = locator.getMobileUserDataId();

        for (int i = 0; i < numberOfUser; i++) {
            FogDevice mobile = addImmobile("immobile_" + i, userId, app, References.NOT_SET); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
            // Simon (020225) says we set uplink latency of users in MyMicroservicesMobilityController.connectWithLatencies
            mobile.setUplinkLatency(-1);
            locator.linkDataWithInstance(mobile.getId(), mobileUserDataIds.get(i));
            mobile.setLevel(2);

            fogDevices.add(mobile);
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

        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
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
        MyFogDevice mobile = createFogDevice(name, 200, 2048, 10000, 270, 0, 87.53, 82.44, MyFogDevice.GENERIC_USER);
        mobile.setParentId(parentId);
        //locator.setInitialLocation(name,drone.getId());
        Sensor mobileSensor = new MySensor("s-" + name, "SENSOR", userId, app.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
        mobileSensor.setApp(app);
        sensors.add(mobileSensor);
        Actuator mobileDisplay = new Actuator("a-" + name, userId, app.getAppId(), "DISPLAY");
        actuators.add(mobileDisplay);

        mobileSensor.setGatewayDeviceId(mobile.getId());
        // TODO Simon says maybe change this. Very unclean. Originally the User Device should have no children
        //  This is solely so that User Device can send EXECUTION_START_REQUEST to its sensor
        mobile.setSensorID(mobileSensor.getId());

        mobileSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms

        mobileDisplay.setGatewayDeviceId(mobile.getId());
        mobileDisplay.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
        mobileDisplay.setApp(app);

        return mobile;
    }


    private static MyApplication createApplication(String appId, int userId, int appLoopLength) {
        switch (appLoopLength){
            case 1:
                return createApplicationLength1(appId, userId);
            case 3:
                return createApplicationLength3(appId, userId);
            case 5:
                return createApplicationLength5(appId, userId);
            default:
                return null;
        }
    }

    private static MyApplication createApplicationLength1(String appId, int userId) {

        MyApplication application = MyApplication.createMyApplication(appId, userId);

        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule("clientModule", 32, 32, 50);
        application.addAppModule("mService1", 400, 400, 200);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("SENSOR", "clientModule", 100, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("clientModule", "mService1", 2000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService1", "clientModule", 2000, 500, "RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("clientModule", "DISPLAY", 14, 500, "RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("clientModule", "SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService1", "RAW_DATA", "RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "RESULT", "RESULT_DISPLAY", new FractionalSelectivity(1.0));


        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("clientModule");
            add("mService1");
            add("clientModule");
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};
        application.setLoops(loops);


//        application.createDAG();

        return application;
    }

    private static MyApplication createApplicationLength3 (String appId, int userId) {

        MyApplication application = MyApplication.createMyApplication(appId, userId);

        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule("clientModule", 32, 32, 50);
        application.addAppModule("mService1", 400, 400, 200);
        application.addAppModule("mService2", 400, 400, 400);
        application.addAppModule("mService3", 400, 400, 400);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("SENSOR", "clientModule", 1000, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("clientModule", "mService1", 2000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService1", "mService2", 2000, 500, "FILTERED_DATA1", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService2", "mService3", 2000, 500, "FILTERED_DATA2", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService3", "clientModule", 2000, 500, "RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("clientModule", "DISPLAY", 14, 500, "RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);
//        application.addAppEdge("clientModule", "DISPLAY", 14, 500, "RESULT2_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);


        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("clientModule", "SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService1", "RAW_DATA", "FILTERED_DATA1", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService2", "FILTERED_DATA1", "FILTERED_DATA2", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService3", "FILTERED_DATA2", "RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "RESULT", "RESULT_DISPLAY", new FractionalSelectivity(1.0));

//        application.setSpecialPlacementInfo("mService3", "cloud");
//        if (CLOUD) {
//            application.setSpecialPlacementInfo("mService1", "cloud");
//            application.setSpecialPlacementInfo("mService2", "cloud");
//        }

        final AppLoop loop3 = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("clientModule");
            add("mService1");
            add("mService2");
            add("mService3");
            add("clientModule");
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop3);
        }};
        application.setLoops(loops);


        // TODO Simon says maybe we look into this?
//        application.createDAG();

        return application;
    }

    private static MyApplication createApplicationLength5 (String appId, int userId) {

        MyApplication application = MyApplication.createMyApplication(appId, userId);

        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule("clientModule", 32, 32, 50);
        application.addAppModule("mService1", 400, 400, 500);
        application.addAppModule("mService2", 400, 400, 500);
        application.addAppModule("mService3", 400, 400, 500);
        application.addAppModule("mService4", 400, 400, 500);
        application.addAppModule("mService5", 400, 400, 500);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */

        application.addAppEdge("SENSOR", "clientModule", 2000, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("clientModule", "mService1", 2000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService1", "mService2", 2000, 500, "FILTERED_DATA1", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService2", "mService3", 2000, 500, "FILTERED_DATA2", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService3", "mService4", 2000, 500, "FILTERED_DATA3", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService4", "mService5", 2000, 500, "FILTERED_DATA4", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mService5", "clientModule", 28, 500, "RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("clientModule", "DISPLAY", 14, 500, "RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);
//        application.addAppEdge("clientModule", "DISPLAY", 14, 500, "RESULT2_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);


        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("clientModule", "SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService1", "RAW_DATA", "FILTERED_DATA1", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService2", "FILTERED_DATA1", "FILTERED_DATA2", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService3", "FILTERED_DATA2", "FILTERED_DATA3", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService4", "FILTERED_DATA3", "FILTERED_DATA4", new FractionalSelectivity(1.0));
        application.addTupleMapping("mService5", "FILTERED_DATA4", "RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "RESULT", "RESULT_DISPLAY", new FractionalSelectivity(1.0));


        final AppLoop loop5 = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("clientModule");
            add("mService1");
            add("mService2");
            add("mService3");
            add("mService4");
            add("mService5");
            add("clientModule");
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop5);
        }};
        application.setLoops(loops);

        // TODO Simon says maybe we look into this?
//        application.createDAG();

        return application;
    }
}


package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
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
import org.fog.placement.MyMicroservicesController;
import org.fog.placement.PlacementLogicFactory;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.*;
import org.fog.utils.distribution.DeterministicDistribution;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
//    private static final String outputFile = "./output/resourceDist_Comfortable_R_Beta.csv";
    private static final String outputFile = "./output/resourceDist_TEST2.csv";
    // The configuration file now uses string-based placement logic identifiers instead of integers
    private static final String CONFIG_FILE = "./dataset/MyExperimentConfigs.yaml";

    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    // Constants for CSV file paths and data configurations
    private static final String USERS_LOCATION_PATH = "./dataset/usersLocation-melbCBD_Experiments.csv";
    private static final String RESOURCES_LOCATION_PATH = "./dataset/edgeResources-melbCBD_Experiments.csv";
    static double SENSOR_TRANSMISSION_TIME = 10;

    public static void main(String[] args) {
        List<SimulationConfig> configs = loadConfigurationsFromYaml();
        
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
            // Traditional metric collection (original code)
            List<List<Double>> resourceData =
                    mm.getAllUtilizations().stream()
                            .map(MetricUtils::handleSimulationResource)
                            .collect(Collectors.toList());
            List<List<Double>> latencyData =
                    mm.getAllLatencies().stream()
                            .map(MetricUtils::handleSimulationLatency)
                            .collect(Collectors.toList());

            List<Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>>> list1 = mm.getAllFailedPRs();
            List<Map<Double, Integer>> list2 = mm.getAllTotalPRs();
            List<Map<String, Object>> failedPRData = IntStream.range(0, Math.min(list1.size(), list2.size()))
                    .mapToObj(i -> {
                        Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> map1 = list1.get(i);
                        Map<Double, Integer> map2 = list2.get(i);
                        return MetricUtils.handleSimulationFailedPRs(map1, map2);
                    })
                    .collect(Collectors.toList());

            // Write traditional aggregate metrics
            MetricUtils.writeToCSV(resourceData, latencyData, failedPRData, configs, outputFile);
            System.out.println("Aggregate CSV file has been created successfully at: " + outputFile);

            // NEW: Classified metrics by userType
            // Process each simulation's data
            for (int simIndex = 0; simIndex < configs.size(); simIndex++) {
                if (simIndex >= mm.getAllUtilizations().size() || 
                    simIndex >= mm.getAllLatencies().size() || 
                    simIndex >= list1.size() || 
                    simIndex >= list2.size()) {
                    System.err.println("Warning: Insufficient data for simulation " + simIndex);
                    continue;
                }
                
                // Extract raw data for this simulation
                Map<Double, Map<PlacementRequest, Double>> utilizationValues = mm.getAllUtilizations().get(simIndex);
                Map<Double, Map<PlacementRequest, Double>> latencyValues = mm.getAllLatencies().get(simIndex);
                Map<Double, Map<PlacementRequest, MicroservicePlacementConfig.FAILURE_REASON>> failedPRs = list1.get(simIndex);
                Map<Double, Integer> totalPRs = list2.get(simIndex);
                SimulationConfig config = configs.get(simIndex);
                
                // Classify metrics by userType
                Map<String, List<Double>> resourceDataByType = MetricUtils.classifyResourceUtilizationByUserType(utilizationValues);
                Map<String, List<Double>> latencyDataByType = MetricUtils.classifyLatencyByUserType(latencyValues);
                Map<String, Map<String, Object>> failedPRDataByType = MetricUtils.classifyFailedPRsByUserType(failedPRs, totalPRs);
                
                // Write classified metrics to a separate CSV
                String userTypeOutputFile = outputFile.replace(".csv", "_userType.csv");
                MetricUtils.writeClassifiedMetricsToCSV(
                    resourceDataByType, 
                    latencyDataByType, 
                    failedPRDataByType, 
                    config, 
                    userTypeOutputFile
                );
                System.out.println("UserType-classified CSV file for simulation " + simIndex + 
                                   " has been created successfully at: " + userTypeOutputFile);
            }
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the CSV files.");
            e.printStackTrace();
        }
    }

    /**
     * Loads simulation configurations from a YAML file
     *
     * @return List of SimulationConfig objects
     */
    private static List<SimulationConfig> loadConfigurationsFromYaml() {
        List<SimulationConfig> configs = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(MyExperiment.CONFIG_FILE)) {
            Yaml yaml = new Yaml();
            List<Map<String, Object>> yamlConfigs = yaml.load(inputStream);

            for (Map<String, Object> configMap : yamlConfigs) {
                int numberOfEdge = ((Number) configMap.get("numberOfEdge")).intValue();
                
                int placementLogic;
                Object placementLogicObj = configMap.get("placementLogic");
                if (placementLogicObj instanceof String) {
                    placementLogic = PlacementLogicFactory.getPlacementLogicCode((String) placementLogicObj);
                    if (placementLogic == -1) {
                        System.err.println("Unknown placement logic name: " + placementLogicObj + ", skipping configuration");
                        continue;
                    }
                } else {
                    placementLogic = ((Number) configMap.get("placementLogic")).intValue();
                }

                // Parse user types map
                Map<String, Integer> usersPerType = new HashMap<>();
                Map<String, Object> userTypeMap = (Map<String, Object>) configMap.get("usersPerType");
                for (Map.Entry<String, Object> entry : userTypeMap.entrySet()) {
                    usersPerType.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                }

                // Parse app loop lengths map
                Map<String, Integer> appLoopLengthPerType = new HashMap<>();
                Map<String, Object> loopLengthMap = (Map<String, Object>) configMap.get("appLoopLengthPerType");
                for (Map.Entry<String, Object> entry : loopLengthMap.entrySet()) {
                    appLoopLengthPerType.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                }

                // Read random seed values if present
                int experimentSeed = 33; // Default value
                int locationSeed = 42; // Default value
                int mobilityStrategySeed = 123; // Default value
                
                if (configMap.containsKey("randomSeeds")) {
                    Map<String, Object> randomSeedsMap = (Map<String, Object>) configMap.get("randomSeeds");
                    if (randomSeedsMap.containsKey("experimentSeed")) {
                        experimentSeed = ((Number) randomSeedsMap.get("experimentSeed")).intValue();
                    }
                    if (randomSeedsMap.containsKey("locationSeed")) {
                        locationSeed = ((Number) randomSeedsMap.get("locationSeed")).intValue();
                    }
                    if (randomSeedsMap.containsKey("mobilityStrategySeed")) {
                        mobilityStrategySeed = ((Number) randomSeedsMap.get("mobilityStrategySeed")).intValue();
                    }
                }

                configs.add(new SimulationConfig(
                    numberOfEdge, 
                    placementLogic, 
                    usersPerType, 
                    appLoopLengthPerType,
                    experimentSeed,
                    locationSeed,
                    mobilityStrategySeed
                ));
            }

            System.out.println("Loaded " + configs.size() + " configurations from " + MyExperiment.CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error loading configurations from " + MyExperiment.CONFIG_FILE);
            e.printStackTrace();
        }

        return configs;
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
        
        // Get the experiment seed from configuration
        int experimentSeed = simulationConfig.getExperimentSeed();
        System.out.println("Using experiment seed: " + experimentSeed);
        
        try {
            Log.enable();
            Logger.ENABLED = true;
            Map<String, Integer> usersPerType = simulationConfig.usersPerType;
            int numberOfEdge = simulationConfig.numberOfEdge;
            int numberOfUser = simulationConfig.numberOfUser;
            int placementLogicType = simulationConfig.placementLogic;
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);
            
            // Debug: Print entity IDs after CloudSim.init
            System.out.println("After CloudSim.init - ENTITY_ID: " + FogUtils.getCurrentEntityId());


            FogBroker broker = new FogBroker("broker");
            CloudSim.setFogBrokerId(broker.getId());
            System.out.println("FogBroker ID: " + broker.getId());

            // TODO Create multiple applications.
            //  According to usersPerType. Each user type gets an application. The matching is naturally performed here.
//            String appId = "SimonApp"; // identifier of the application
//            MyApplication application = createApplication(appId, broker.getId(), appLoopLength);
//            application.setUserId(broker.getId());

            // Simon (140125) says tuples will be sent to FogDevices and executed under mService1
            // because source module is clientModule but dest module is mService1
            // TODO Change accordingly if the AppLoop ever changes (or there are more AppLoops)
//            FogBroker.getApplicationToFirstMicroserviceMap().put(application, "clientModule");
//            List<String> simonAppSecondMicroservices = new ArrayList<>();
//            simonAppSecondMicroservices.add("mService1");
//            FogBroker.getApplicationToSecondMicroservicesMap().put(application, simonAppSecondMicroservices);

            Map<String, Application> applicationsPerType = new HashMap<>();
            for (String userType : simulationConfig.usersPerType.keySet()) {
                int appLoopLength = simulationConfig.appLoopLengthPerType.get(userType);
                Application application = createApplication(userType, broker.getId(), appLoopLength, experimentSeed);
                application.setUserId(broker.getId());
                applicationsPerType.put(userType, application);

                String clientModuleName = userType + "_clientModule"; // todo NOTE hardcoded.
                FogBroker.getApplicationToFirstMicroserviceMap().put(application, clientModuleName);

                List<String> secondMicroservices = new ArrayList<>();
                secondMicroservices.add(userType + "_Service1");
                FogBroker.getApplicationToSecondMicroservicesMap().put(application, secondMicroservices);
            }

            // Create fog devices (cloud, gateways, and mobile devices)
            createFogDevices(broker.getId(), applicationsPerType, numberOfEdge, usersPerType, simulationConfig);

            /**
             * Central controller for performing preprocessing functions
             */
            List<Application> appList = new ArrayList<>(applicationsPerType.values());

            // Set the location seed for random location generation
            Location.setDefaultRandomSeed(simulationConfig.getLocationSeed());
            System.out.println("Set Location default seed to: " + simulationConfig.getLocationSeed());

            MyMicroservicesController microservicesController = new MyMicroservicesController(
                "controller",
                    fogDevices,
                    sensors,
                    appList,
                    placementLogicType
            );
                
            try {
                System.out.println("Initializing location data from CSV files...");
                microservicesController.initializeLocationData(
                    RESOURCES_LOCATION_PATH,
                    USERS_LOCATION_PATH,
                    numberOfEdge + 1, // cloud
                    numberOfUser,
                    experimentSeed
                );
                System.out.println("Location data initialization complete.");

                // microservicesController.enableMobility(simulationConfig.getMobilityStrategySeed());
                // System.out.println("Mobility enabled with seed: " + simulationConfig.getMobilityStrategySeed());
                
                microservicesController.completeInitialization();
                System.out.println("Controller initialization completed with proximity-based connections.");
            } catch (IOException e) {
                System.out.println("Failed to initialize location data: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            List<PlacementRequest> placementRequests = new ArrayList<>();
            for (Sensor s : sensors) {
                MySensor sensor = (MySensor) s;
                String userType = sensor.getUserType();

                Map<String, Integer> placedMicroservicesMap = new LinkedHashMap<>();

                placedMicroservicesMap.put(
                        FogBroker.getApplicationToFirstMicroserviceMap().get(sensor.getApp()),
                        sensor.getGatewayDeviceId()
                );

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

            // Schedule the opera accident event
            double operaExplosionTime = 7200.0; // Or read from config
            CloudSim.send(microservicesController.getId(), microservicesController.getId(), operaExplosionTime, 
                          FogEvents.OPERA_ACCIDENT_EVENT, null);

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
     * @param brokerId User ID for the broker
     * @param numberOfEdge Number of edge devices to create
     * @param simulationConfig Configuration containing random seeds
     */
    private static void createFogDevices(int brokerId, Map<String, Application> applicationsPerType,
                                         int numberOfEdge, Map<String, Integer> usersPerType, 
                                         SimulationConfig simulationConfig) {
        // Create cloud device at the top of the hierarchy
        MyFogDevice cloud = createFogDevice("cloud", 44800, -1, 40000, 100, 10000, 0.01, 16 * 103, 16 * 83.25, MyFogDevice.CLOUD);
        cloud.setParentId(References.NOT_SET);
        cloud.setLevel(0);
        fogDevices.add(cloud);
        Random random = new Random(simulationConfig.getExperimentSeed());

        // Create gateway devices
        for (int i = 0; i < numberOfEdge; i++) {
            // todo 1000-4000 mips, 1000 - 32000 mb ram (mb because value must be int)
            MyFogDevice gateway = createFogDevice(
                    "gateway_" + i,
                    random.nextInt(3001) + 1000,
                    -1, // Undefined. Let Location Manager determine.
                    random.nextInt(31001) + 1000,
                    10000,
                    10000,
                    0.0,
                    107.339,
                    83.4333,
                    MyFogDevice.FCN
            );
            gateway.setParentId(cloud.getId());
            // Let latency be set by LocationManager in connectWithLatencies
            gateway.setLevel(1);
            fogDevices.add(gateway);
        }

        for (String userType : usersPerType.keySet()) {
            // todo This is a hardcoded check. Edit based on your user types.
            if (!(Objects.equals(userType, MyFogDevice.GENERIC_USER) ||
                Objects.equals(userType, MyFogDevice.AMBULANCE_USER) ||
                Objects.equals(userType, MyFogDevice.OPERA_USER))) {
                throw new NullPointerException("Invalid Type");
            }

            Application appForThisType = applicationsPerType.get(userType);
            int numUsersOfThisType = usersPerType.get(userType);

            for (int i = 0; i < numUsersOfThisType; i++) {
                // Use the specific application for this user type
                FogDevice mobile = addUser(userType, userType + "_" + i, brokerId, appForThisType, References.NOT_SET);
                mobile.setLevel(2);

                fogDevices.add(mobile);
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
    private static MyFogDevice createFogDevice(String nodeName, long mips, double upLinkLatency,
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
            fogdevice = new MyFogDevice(
                    nodeName,
                    characteristics,
                    new AppModuleAllocationPolicy(hostList),
                    storageList,
                    10,
                    upBw,
                    downBw,
                    10000,
                    upLinkLatency,
                    ratePerMips,
                    deviceType
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fogdevice;
    }

    private static FogDevice addUser(String userType, String name, int brokerId, Application app, int parentId) {
        // NOTE: Assumes userType has been verified before calling addUser.
        MyFogDevice mobile = createFogDevice(name, 200, -1, 200, 10000, 270, 0, 87.53, 82.44, userType);
        mobile.setParentId(parentId);
        
        Sensor mobileSensor = new MySensor("s-" + name, "SENSOR", brokerId, app.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
        mobileSensor.setApp(app);
        sensors.add(mobileSensor);
        
        Actuator mobileDisplay = new Actuator("a-" + name, brokerId, app.getAppId(), "DISPLAY");
        actuators.add(mobileDisplay);

        mobileSensor.setGatewayDeviceId(mobile.getId());
        mobileSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms

        mobileDisplay.setGatewayDeviceId(mobile.getId());
        mobileDisplay.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
        mobileDisplay.setApp(app);

        return mobile;
    }


//    private static MyApplication createApplication(String appId, int userId, int numServices) {
//        MyApplication application = MyApplication.createMyApplication(appId, userId);
//        Random random = new Random(33);
//
//        application.addAppModule("clientModule", 4, 4, 50);
//        for (int i = 1; i <= numServices; i++) {
//            // Services 250-750 mips, 250-4000 MB ram (MB because values are int)
//            application.addAppModule("mService" + i, random.nextInt(3751) + 250, random.nextInt(501) + 250, 500);
//        }
//
//        /*
//         * Connecting the application modules (vertices) in the application model (directed graph) with edges
//         */
//        application.addAppEdge("SENSOR", "clientModule", 14, 50, "SENSOR", Tuple.UP, AppEdge.SENSOR);
//        //  Execution time 300 seconds average and 500 mips average => 150000
//        //  We halve it to 100 seconds execution time, for scenario 2. Slowest case 50000/250 = 200 seconds.
//        application.addAppEdge("clientModule", "mService1", 10000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
//
//        // Connect microservices in sequence
//        for (int i = 1; i < numServices; i++) {
//            String sourceModule = "mService" + i;
//            String destModule = "mService" + (i+1);
//            String tupleType = "FILTERED_DATA" + i;
//
//            application.addAppEdge(sourceModule, destModule, 10000, 500, tupleType, Tuple.UP, AppEdge.MODULE);
//        }
//
//        application.addAppEdge("mService" + numServices, "clientModule", 4, 500, "RESULT", Tuple.DOWN, AppEdge.MODULE);
//        // NOTE tupleCpuLength and tupleNwLength don't matter; actuator doesn't execute.
//        application.addAppEdge("clientModule", "DISPLAY", 4, 50, "RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);
//
//        /*
//         * Defining the input-output relationships (represented by selectivity) of the application modules.
//         */
//        application.addTupleMapping("clientModule", "SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
//        for (int i = 1; i < numServices; i++) {
//            String sourceModule = "mService" + i;
//            String inputTupleType = (i == 1) ? "RAW_DATA" : "FILTERED_DATA" + (i-1);
//            String outputTupleType = "FILTERED_DATA" + i;
//
//            application.addTupleMapping(sourceModule, inputTupleType, outputTupleType, new FractionalSelectivity(1.0));
//        }
//        String lastInputTupleType = (numServices == 1) ? "RAW_DATA" : "FILTERED_DATA" + (numServices-1);
//        application.addTupleMapping("mService" + numServices, lastInputTupleType, "RESULT", new FractionalSelectivity(1.0));
//        application.addTupleMapping("clientModule", "RESULT", "RESULT_DISPLAY", new FractionalSelectivity(1.0));
//
//        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
//            add("SENSOR");
//            add("clientModule");
//
//            for (int i = 1; i <= numServices; i++) {
//                add("mService" + i);
//            }
//
//            add("clientModule");
//            add("DISPLAY");
//        }});
//
//        List<AppLoop> loops = new ArrayList<AppLoop>() {{
//            add(loop);
//        }};
//        application.setLoops(loops);
//
//        return application;
//    }
    private static MyApplication createApplication(String userType, int brokerId, int numServices) {
        return createApplication(userType, brokerId, numServices, 33); // Use default seed value
    }

    private static MyApplication createApplication(String userType, int brokerId, int numServices, int randomSeed) {
        String appId = userType + "App";
        MyApplication application = MyApplication.createMyApplication(appId, brokerId);
        Random random = new Random(randomSeed);

        // Prefix all module names with the user type FOR UNIQUENESS OF MODULE NAMES
        String clientModuleName = userType + "_clientModule";
        application.addAppModule(clientModuleName, 4, 4, 50);

        for (int i = 1; i <= numServices; i++) {
            String serviceName = userType + "_Service" + i;
            application.addAppModule(serviceName, random.nextInt(3751) + 250, random.nextInt(501) + 250, 500);
        }

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("SENSOR", clientModuleName, 14, 50, "SENSOR", Tuple.UP, AppEdge.SENSOR);

        String firstServiceName = userType + "_Service1";
        application.addAppEdge(clientModuleName, firstServiceName, 10000, 500,
                userType + "_RAW_DATA", Tuple.UP, AppEdge.MODULE);
        for (int i = 1; i < numServices; i++) {
            String sourceModule = userType + "_Service" + i;
            String destModule = userType + "_Service" + (i+1);
            String tupleType = userType + "_FILTERED_DATA" + i;

            application.addAppEdge(sourceModule, destModule, 10000, 500, tupleType, Tuple.UP, AppEdge.MODULE);
        }

        String lastServiceName = userType + "_Service" + numServices;
        application.addAppEdge(lastServiceName, clientModuleName, 4, 500,
                userType + "_RESULT", Tuple.DOWN, AppEdge.MODULE);

        // Connect to actuator
        application.addAppEdge(clientModuleName, "DISPLAY", 4, 50,
                userType + "_RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping(clientModuleName, "SENSOR", userType + "_RAW_DATA",
                new FractionalSelectivity(1.0));

        for (int i = 1; i < numServices; i++) {
            String sourceModule = userType + "_Service" + i;
            String inputTupleType = (i == 1) ? userType + "_RAW_DATA" : userType + "_FILTERED_DATA" + (i-1);
            String outputTupleType = userType + "_FILTERED_DATA" + i;

            application.addTupleMapping(sourceModule, inputTupleType, outputTupleType,
                    new FractionalSelectivity(1.0));
        }

        String lastInputTupleType = (numServices == 1) ? userType + "_RAW_DATA" : userType + "_FILTERED_DATA" + (numServices-1);
        application.addTupleMapping(userType + "_Service" + numServices, lastInputTupleType,
                userType + "_RESULT", new FractionalSelectivity(1.0));

        application.addTupleMapping(clientModuleName, userType + "_RESULT",
                userType + "_RESULT_DISPLAY", new FractionalSelectivity(1.0));

        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add(clientModuleName);

            for (int i = 1; i <= numServices; i++) {
                add(userType + "_Service" + i);
            }

            add(clientModuleName);
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop);
        }};
        application.setLoops(loops);

        return application;
    }
}


package org.fog.test.perfeval;

import org.cloudbus.cloudsim.sdn.example.policies.VmSchedulerTimeSharedEnergy;
import org.fog.application.MyApplication;
import org.fog.mobilitydata.*;
import org.fog.placement.PlacementSimulationController;
import org.fog.utils.*;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.entities.PlacementRequest;
import org.fog.placement.PlacementLogicFactory;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.distribution.DeterministicDistribution;

import java.io.IOException;
import java.util.*;

/**
 * Proof of concept for integration of the following iFogSim elements:
 1. Multiple IMMOBILE users
 2. Multiple edge servers + 1 cloud server
 3. Application (multiple Microservices in 1 AppLoop)
 4. Simple Microservice Placement Logic
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
public class OnlinePOC {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    // Path to the CSV files with location data
    private static final String USERS_LOCATION_PATH = "./dataset/usersLocation-melbCBD_OnlinePOC.csv";
    private static final String RESOURCES_LOCATION_PATH = "./dataset/edgeResources-melbCBD_OfflinePOC.csv";

    static double SENSOR_TRANSMISSION_TIME = 10;
    static int numberOfUser = 5;

    //cluster link latency 2ms
    static Double clusterLatency = 2.0;

    public static void main(String[] args) {

        Log.printLine("Starting Simon's Online Application...");

        try {

            Log.enable();
            Logger.ENABLED = true;
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "immobileUserApp_0"; // identifier of the application

            FogBroker broker = new FogBroker("broker");
            CloudSim.setFogBrokerId(broker.getId());

            MyApplication application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            // Create fog devices (including user devices)
            createFogDevices(broker.getId(), application);

            /**
             * Central controller for performing preprocessing functions
             */
            List<Application> appList = new ArrayList<>();
            appList.add(application);

            int placementAlgo = PlacementLogicFactory.BEST_FIT;
            Map<String, Integer> intervalValues = new HashMap<>();
            intervalValues.put("immobileUser", 300);
            PlacementSimulationController microservicesController = new PlacementSimulationController(
                    "controller",
                    fogDevices,
                    sensors,
                    actuators,
                    appList,
                    placementAlgo,
                    intervalValues,
                    100,
                    200,
                    30
            );

            FogBroker.getApplicationInfo().put(application.getAppId(), application);

            String clientModuleName = appId + "_clientModule";
            FogBroker.getApplicationToFirstServiceMap().put(application, clientModuleName);

            List<String> secondMicroservices = new ArrayList<>();
            secondMicroservices.add(appId + "_mService1");
            FogBroker.getApplicationToSecondServicesMap().put(application, secondMicroservices);

            SPPMonitor.getInstance().initializeSimulation(0);

            try {
                System.out.println("Initializing location data from CSV files...");
                microservicesController.enableMobility();
                microservicesController.initializeLocationData(
                    RESOURCES_LOCATION_PATH,
                    USERS_LOCATION_PATH,
                    6, // 5 edge servers + 1 cloud
                    numberOfUser
                );
                System.out.println("Location data initialization complete.");

                microservicesController.setPathingSeeds(33);
                System.out.println("Mobility enabled using the Strategy Pattern.");
                
                microservicesController.completeInitialization();
                System.out.println("Controller initialization completed with proximity-based connections.");
            } catch (IOException e) {
                System.out.println("Failed to initialize location data: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            List<PlacementRequest> placementRequests = new ArrayList<>();
            for (Sensor sensor : sensors) {
                Map<String, Integer> placedMicroservicesMap = new LinkedHashMap<>();
                placedMicroservicesMap.put("clientModule", sensor.getGatewayDeviceId());

                // Use MyMicroservicesController to create PROTOTYPE placement request
                //  They will not be processed, but used as template for periodic generation.
                PlacementRequest prototypePR = microservicesController.createPlacementRequest(
                        sensor, placedMicroservicesMap, -1.0);
                placementRequests.add(prototypePR);
            }

            microservicesController.submitPlacementRequests(placementRequests, 1);

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            Log.printLine("Simon app START!");
            Log.printLine(String.format("Placement Logic: %d", placementAlgo));

            CloudSim.startSimulation();

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates fog devices in the physical topology of the simulation.
     * This method creates a cloud device and gateway devices.
     *
     * @param userId User ID for the broker
     * @param app Application to be deployed
     */
    private static void createFogDevices(int userId, Application app) throws NumberFormatException, IOException {
        // Create cloud device at the top of the hierarchy
        SPPFogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0.01, 16 * 103, 16 * 83.25, SPPFogDevice.CLOUD);
        cloud.setParentId(References.NOT_SET);
        cloud.setLevel(0);
        fogDevices.add(cloud);

        for (int i = 0; i < numberOfUser; i++) {
            SPPFogDevice gateway = createFogDevice("gateway_" + i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333, SPPFogDevice.FCN);
            gateway.setParentId(cloud.getId());
            gateway.setLevel(1);
            fogDevices.add(gateway);
        }

        for (int i = 0; i < numberOfUser; i++) {
            FogDevice mobile = addImmobile("immobileUser_" + i, userId, app, References.NOT_SET);
            // Don't set uplink latency, it will be set by LocationManager based on distance
            mobile.setUplinkLatency(-1);
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
    private static SPPFogDevice createFogDevice(String nodeName, long mips,
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

        SPPFogDevice fogdevice = null;
        try {
            fogdevice = new SPPFogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 10000, 0, ratePerMips, deviceType);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fogdevice;
    }

    private static FogDevice addImmobile(String name, int userId, Application app, int parentId) {
        SPPFogDevice mobile = createFogDevice(name, 200, 2048, 10000, 270, 0, 87.53, 82.44, SPPFogDevice.IMMOBILE_USER);
        mobile.setParentId(parentId);
        
        Sensor mobileSensor = new PassiveSensor("s-" + name, "SENSOR", userId, app.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
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


    @SuppressWarnings({"serial"})
    private static MyApplication createApplication(String appId, int userId) {

        MyApplication application = MyApplication.createMyApplication(appId, userId);

        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule(application.getAppId()+"_clientModule", 4, 4, 100);
        application.addAppModule(application.getAppId()+"_mService1", 128, 250, 200);
        application.addAppModule(application.getAppId()+"_mService2", 128, 350, 500);
        application.addAppModule(application.getAppId()+"_mService3", 128, 450, 1000);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */

        application.addAppEdge("SENSOR", "clientModule", 1000, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge(application.getAppId()+"_clientModule", application.getAppId()+"_mService1", 2000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge(application.getAppId()+"_mService1", application.getAppId()+"_mService2", 2500, 500, "FILTERED_DATA1", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge(application.getAppId()+"_mService2", application.getAppId()+"_mService3", 4000, 500, "FILTERED_DATA2", Tuple.UP, AppEdge.MODULE);

//        application.addAppEdge("mService2", "clientModule", 14, 500, "RESULT1", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge(application.getAppId()+"_mService3", application.getAppId()+"_clientModule", 28, 500, "RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge(application.getAppId()+"_clientModule", "DISPLAY", 14, 500, "RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);
//        application.addAppEdge("clientModule", "DISPLAY", 14, 500, "RESULT2_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);


        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping(application.getAppId()+"_clientModule", "SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping(application.getAppId()+"_mService1", "RAW_DATA", "FILTERED_DATA1", new FractionalSelectivity(1.0));
        application.addTupleMapping(application.getAppId()+"_mService2", "FILTERED_DATA1", "FILTERED_DATA2", new FractionalSelectivity(1.0));
        application.addTupleMapping(application.getAppId()+"_mService3", "FILTERED_DATA2", "RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping(application.getAppId()+"_clientModule", "RESULT", "RESULT_DISPLAY", new FractionalSelectivity(1.0));

        final AppLoop loop3 = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add(application.getAppId()+"_clientModule");
            add(application.getAppId()+"_mService1");
            add(application.getAppId()+"_mService2");
            add(application.getAppId()+"_mService3");
            add(application.getAppId()+"_clientModule");
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop3);
        }};
        application.setLoops(loops);

        return application;
    }
}
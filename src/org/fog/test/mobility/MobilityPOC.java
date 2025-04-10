package org.fog.test.mobility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

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
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MyFogDevice;
import org.fog.mobility.*;
import org.fog.mobilitydata.Location;
import org.fog.placement.MyMicroservicesController;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.FogEvents;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;

/**
 * A proof of concept (POC) that demonstrates how to use the mobility framework
 * in iFogSim-placement. This POC shows how to:
 * 
 * 1. Create a mobility strategy (BeelinePathingStrategy)
 * 2. Create mobility states for different user device types
 * 3. Register devices with a mobility controller
 * 4. Trigger and handle mobility events without placement requests
 * 
 * This version uses the Strategy Pattern to enable mobility in the base controller.
 */
public class MobilityPOC {
    
    // Path to the CSV file with user locations
    private static final String LOCATIONS_CSV_PATH = "./dataset/usersLocation-melbCBD_Experiments.csv";
    private static final String RESOURCES_CSV_PATH = "./dataset/edgeResources-melbCBD_Experiments.csv";
    private static final int NUM_USERS = 196; // Number of users to create
    private static final int NUM_GATEWAYS = 50; // Number of gateway devices to create
    
    public static void main(String[] args) {
        Log.printLine("Starting Mobility Proof of Concept...");
        
        try {
            // Initialize the CloudSim library
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            Log.enable();
            Logger.ENABLED = true;
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create the fog devices
            List<FogDevice> fogDevices = createFogDevices();
            
            // Create a base controller without sensors and applications
            MyMicroservicesController controller = new MyMicroservicesController(
                "MobilityController", 
                fogDevices, 
                new ArrayList<>(), // no sensors
                new ArrayList<>(), // no applications
                0 // placementLogic
            );
            
            try {
                controller.initializeLocationData(
                    RESOURCES_CSV_PATH,
                    LOCATIONS_CSV_PATH,
                    NUM_GATEWAYS,
                    NUM_USERS
                );
                Log.printLine("Successfully initialized location data from CSV files.");

                // Enable mobility by activating the full mobility strategy\
                controller.enableMobility();
                Log.printLine("Mobility enabled using the Strategy Pattern.");

                // Complete initialization now that location data is loaded
                controller.completeInitialization();
                Log.printLine("Controller initialization completed with proximity-based connections.");
                


            } catch (IOException e) {
                Log.printLine("Failed to initialize location data: " + e.getMessage());
                return;
            }

//            for (int deviceId : controller.getDeviceMobilityStates().keySet()) {
//                DeviceMobilityState mobilityState = controller.getDeviceMobilityState(deviceId);
//
//                if (mobilityState != null) {
//                    // Create an initial attraction point template
//                    Location currentLocation = mobilityState.getCurrentLocation();
//                    Attractor initialAttraction = new Attractor(
//                            currentLocation, // Initial location (will be replaced with random by createAttractionPoint)
//                            "Initial Destination",
//                            0.1, // min pause time
//                            3.0, // max pause time
//                            new PauseTimeStrategy()
//                    );
//
//                    mobilityState.updateAttractionPoint(initialAttraction);
//                    controller.startDeviceMobility(deviceId);
//
////                    CloudSim.send(controller.getId(), deviceId, 100, FogEvents.MOBILITY_MANAGEMENT, device);
//
//                    System.out.println("Started mobility for device: " + CloudSim.getEntityName(deviceId) +
//                            " at location: " + currentLocation.latitude + ", " + currentLocation.longitude);
//                } else {
//                    System.out.println("WARNING: No mobility state found for device " + CloudSim.getEntityName(deviceId));
//                }
//            }
            
            CloudSim.startSimulation();
            
            Log.printLine("Mobility Proof of Concept finished!");
            
        } catch (Throwable e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened: " + e.getMessage());
        }
    }
    
    /**
     * Creates fog devices for the simulation
     * 
     * @return list of fog devices
     */
    private static List<FogDevice> createFogDevices() {
        List<FogDevice> devices = new ArrayList<>();
        
        // Create cloud device
        MyFogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25, MyFogDevice.CLOUD);
        cloud.setParentId(-1);
        devices.add(cloud);
        
        // Create gateway devices (no proxy level as per requirement)
        for (int i = 0; i < NUM_GATEWAYS; i++) {
            MyFogDevice gateway = createFogDevice("gateway_" + i, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333, MyFogDevice.FCN);
            gateway.setParentId(cloud.getId());
            gateway.setUplinkLatency(100); // ms
            devices.add(gateway);
        }
            
        // Create user devices connected to each gateway
        for (int j = 0; j < NUM_USERS; j++) {
            MyFogDevice userDevice = createFogDevice(
                    "user_" + j,
                    1000,
                    1000,
                    10000,
                    270,
                    2,
                    0.0,
                    87.53,
                    82.44,
                    getRandomUserType() // Only returns generic user for now
            );
            userDevice.setUplinkLatency(2); // ms
            devices.add(userDevice);
        }
        
        return devices;
    }
    
    /**
     * Randomly selects one of the user device types
     */
    private static String getRandomUserType() {
        return MyFogDevice.GENERIC_USER;
    }
    
    /**
     * Creates a fog device with the specified configuration
     */
    private static MyFogDevice createFogDevice(String name, double mips, int ram, long upBw, long downBw, 
            int level, double ratePerMips, double busyPower, double idlePower, String deviceType) {
        
        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;
        
        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new VmSchedulerTimeSharedEnergy(peList), // Using VmSchedulerTimeSharedEnergy as requested
                new FogLinearPowerModel(busyPower, idlePower)
            );
        
        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);
        
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<Storage>();
        
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);
        
        MyFogDevice fogdevice = null;
        try {
            fogdevice = new MyFogDevice(name, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 10000, 0, ratePerMips, deviceType);
            fogdevice.setLevel(level);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return fogdevice;
    }
} 
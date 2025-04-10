package org.fog.test.mobility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
import org.fog.mobility.Attractor;
import org.fog.mobility.BeelineMobilityStrategy;
import org.fog.mobility.GenericUserMobilityState;
import org.fog.mobility.PauseTimeStrategy;
import org.fog.mobilitydata.Location;
import org.fog.mobilitydata.References;
import org.fog.placement.MyMicroservicesMobilityController;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.FogEvents;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

/**
 * A proof of concept (POC) that demonstrates how to use the mobility framework
 * in iFogSim-placement. This POC shows how to:
 * 
 * 1. Create a mobility strategy (BeelineMobilityStrategy)
 * 2. Create mobility states for different user device types
 * 3. Register devices with a mobility controller
 * 4. Trigger and handle mobility events without placement requests
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
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create the fog devices
            List<FogDevice> fogDevices = createFogDevices();
            
            // Create a mobility controller without sensors and applications
            MyMicroservicesMobilityController controller = new MyMicroservicesMobilityController(
                "MobilityController", 
                fogDevices, 
                new ArrayList<>(), // no sensors
                new ArrayList<>(), // no applications
                0 // placementLogic
            );
            
            // Initialize location data from CSV files
            try {
                controller.initializeLocationData(
                    RESOURCES_CSV_PATH,
                    LOCATIONS_CSV_PATH,
                    NUM_GATEWAYS,
                    NUM_USERS
                );
                Log.printLine("Successfully initialized location data from CSV files.");
                
                // Complete initialization now that location data is loaded
                controller.completeInitialization();
                Log.printLine("Controller initialization completed with proximity-based connections.");
            } catch (IOException e) {
                Log.printLine("Failed to initialize location data: " + e.getMessage());
                return;
            }
            
            // Create a mobility strategy
            BeelineMobilityStrategy mobilityStrategy = new BeelineMobilityStrategy();
            
            // For each mobile device, create attraction points and start mobility
            for (FogDevice device : fogDevices) {
                if (device instanceof MyFogDevice) {
                    MyFogDevice myDevice = (MyFogDevice) device;
                    String deviceType = myDevice.getDeviceType();
                    
                    // Check if this is a user device type
                    if (deviceType.equals(MyFogDevice.GENERIC_USER) || 
                        deviceType.equals(MyFogDevice.AMBULANCE_USER) || 
                        deviceType.equals(MyFogDevice.OPERA_USER)) {
                        
                        // Get the device's current mobility state
                        GenericUserMobilityState mobilityState = 
                            (GenericUserMobilityState) controller.getDeviceMobilityState(device.getId());
                        
                        if (mobilityState != null) {
                            // Create an initial attraction point template
                            Location currentLocation = mobilityState.getCurrentLocation();
                            Attractor initialAttraction = new Attractor(
                                currentLocation, // Initial location (will be replaced with random by createAttractionPoint)
                                "Initial Destination",
                                0.1, // min pause time
                                3.0, // max pause time
                                new PauseTimeStrategy()
                            );
                            
                            // Set initial attraction point
                            mobilityState.createAttractionPoint(initialAttraction);
                            
                            // Start device mobility
                            controller.startDeviceMobility(device.getId());
                            
                            // Schedule some future mobility events for demonstration
                            CloudSim.send(controller.getId(), device.getId(), 100, FogEvents.MOBILITY_MANAGEMENT, device);
                            
                            System.out.println("Started mobility for device: " + device.getName() + 
                                              " (Type: " + deviceType + ") at location: " + 
                                              currentLocation.latitude + ", " + currentLocation.longitude);
                        } else {
                            System.out.println("WARNING: No mobility state found for device " + device.getName());
                        }
                    }
                }
            }
            
            // Start the simulation
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
            
            // Calculate how many users to assign to this gateway
            int usersPerGateway = NUM_USERS / NUM_GATEWAYS;
            int extraUsers = (i < NUM_USERS % NUM_GATEWAYS) ? 1 : 0;
            int numUsers = usersPerGateway + extraUsers;
            
            // Calculate starting index for users assigned to this gateway
            int startIndex = i * usersPerGateway + Math.min(i, NUM_USERS % NUM_GATEWAYS);
            
            // Create user devices connected to each gateway
            for (int j = 0; j < numUsers; j++) {
                int userIndex = startIndex + j;
                if (userIndex < NUM_USERS) {
                    MyFogDevice userDevice = createFogDevice(
                        "user_" + i + "_" + j, 
                        1000, 
                        1000, 
                        10000, 
                        270, 
                        2, 
                        0.0, 
                        87.53, 
                        82.44,
                        getRandomUserType()
                    );
                    userDevice.setParentId(gateway.getId());
                    userDevice.setUplinkLatency(2); // ms
                    devices.add(userDevice);
                }
            }
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
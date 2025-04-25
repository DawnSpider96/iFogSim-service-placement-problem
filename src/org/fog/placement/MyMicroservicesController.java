package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.mobility.*;
import org.fog.utils.*;
import org.json.simple.JSONObject;
import org.fog.mobilitydata.Location;

import java.io.IOException;
import java.util.*;
import java.util.Comparator;


public class MyMicroservicesController extends SimEntity {

    protected List<FogDevice> fogDevices;
    protected List<Sensor> sensors;
    private Map<Integer, Integer> sensorToSequenceNumber = new HashMap<>();
    protected Map<String, Application> applications = new HashMap<>();
    protected PlacementLogicFactory placementLogicFactory = new PlacementLogicFactory();
    protected Object placementLogic; // Can be either int or String depending on configuration source
    private boolean mobilityEnabled = false;
    //    protected List<Integer> clustering_levels;
    /**
     * A permanent set of Placement Requests, initialized with one per user device.
     * For PR generation, simulation will always make copies of these.
     */
    protected Map<PlacementRequest, Integer> placementRequestDelayMap = new HashMap<>();

    // For PR generation
    private List<MyFogDevice> userDevices = new ArrayList<>();
    private Map<Integer, Map<String, Double>> userResourceAvailability = new HashMap<>();
    /**
     * Interval (in simulation time units) at which periodic placement requests are generated.
     * This value is static and shared across all instances.
     */
    private double prGenerationInterval = MicroservicePlacementConfig.PLACEMENT_GENERATE_INTERVAL;

    // New fields for location management
    private DataLoader dataLoader;
    protected LocationManager locationManager;
    private Map<Integer, DeviceMobilityState> deviceMobilityStates = new HashMap<>();
    private boolean locationDataInitialized = false;
    
    // Mobility strategy
    protected MobilityStrategy mobilityStrategy;
    
    /**
     * Constructor supporting both integer and string placement logic identifiers.
     * 
     * @param name Controller name
     * @param fogDevices List of fog devices
     * @param sensors List of sensors
     * @param applications List of applications
     * @param placementLogic Placement logic identifier (can be Integer or String)
     */
    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, 
                                    List<Application> applications, Object placementLogic) {
        super(name);
        this.fogDevices = fogDevices;
        this.sensors = sensors;
        this.placementLogic = placementLogic;
        for (Application app : applications) {
            this.applications.put(app.getAppId(), app);
        }

        // Initialize the location management components
        initializeLocationComponents();
        
        // Initialize with the no-op mobility strategy by default
        this.mobilityStrategy = new NoMobilityStrategy();
    }

    /**
     * Alternative constructor supporting legacy integer placement logic.
     * 
     * @param name Controller name
     * @param fogDevices List of fog devices
     * @param sensors List of sensors
     * @param applications List of applications
     * @param placementLogic Integer placement logic type
     */
    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, 
                                    List<Application> applications, int placementLogic) {
        this(name, fogDevices, sensors, applications, (Object)placementLogic);
    }

    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, 
                                     List<Application> applications, Object placementLogic, 
                                     Map<Integer, List<FogDevice>> monitored) {
        super(name);
        this.fogDevices = fogDevices;
        this.sensors = sensors;
        this.placementLogic = placementLogic;
        for (Application app : applications) {
            this.applications.put(app.getAppId(), app);
        }
        
        // Initialize the location management components
        initializeLocationComponents();
        
        // Initialize with the no-op mobility strategy by default
        this.mobilityStrategy = new NoMobilityStrategy();
    }

    /**
     * Initializes the location management components
     */
    private void initializeLocationComponents() {
        this.dataLoader = new DataLoader();
        this.locationManager = new LocationManager(
            dataLoader.getLevelID(),
            dataLoader.getLevelwiseResources(),
            deviceMobilityStates
        );
    }

    /**
     * Completes the initialization process after location data has been loaded.
     * This method should be called after initializeLocationData() to ensure 
     * proximity-based connections are established correctly.
     */
    public void completeInitialization() {
        init();
        
        Map<Integer, Integer> initialParentReferences = new HashMap<>();
        for (FogDevice device : fogDevices) {
            initialParentReferences.put(device.getId(), device.getParentId());
        }
        mobilityStrategy.initialize(fogDevices, initialParentReferences);
    }

    /**
     * Completes the initialization process with monitored devices after location data has been loaded.
     * This method should be called after initializeLocationData() to ensure 
     * proximity-based connections are established correctly.
     * 
     * @param monitored map of monitored devices
     */
    // Simon (100425) says not used for now.
    public void completeInitialization(Map<Integer, List<FogDevice>> monitored) {
        init(monitored);
        
        // Initialize parent references for mobility strategy
        Map<Integer, Integer> initialParentReferences = new HashMap<>();
        for (FogDevice device : fogDevices) {
            initialParentReferences.put(device.getId(), device.getParentId());
        }
        mobilityStrategy.initialize(fogDevices, initialParentReferences);
    }

    protected void init() {
        connectWithLatencies();
        initializeControllers(placementLogic);
        generateRoutingTable();
    }

    protected void init(Map<Integer, List<FogDevice>> monitored) {
        connectWithLatencies();
        initializeControllers(placementLogic, monitored);
        generateRoutingTable();
    }

    protected void initializeControllers(Object placementLogic) {
        for (FogDevice device : fogDevices) {
            LoadBalancer loadBalancer = new UselessLoadBalancer(); // Simon (100425) says this is useless, but for backwards compatibility
            MyFogDevice cdevice = (MyFogDevice) device;

            // responsible for placement decision-making
            if (cdevice.getDeviceType().equals(MyFogDevice.FON) || cdevice.getDeviceType().equals(MyFogDevice.CLOUD)) {
                List<FogDevice> monitoredDevices = getDevicesForFON(cdevice);
                MicroservicePlacementLogic microservicePlacementLogic;
                
                // Handle either string or int placementLogic
                if (placementLogic instanceof String) {
                    microservicePlacementLogic = placementLogicFactory.getPlacementLogic((String)placementLogic, cdevice.getId());
                } else if (placementLogic instanceof Number) {
                    microservicePlacementLogic = placementLogicFactory.getPlacementLogic(((Number)placementLogic).intValue(), cdevice.getId());
                } else {
                    Logger.error("Placement Logic Error", "Unknown placement logic type: " + placementLogic.getClass().getName());
                    microservicePlacementLogic = null;
                }
                
                cdevice.initializeController(loadBalancer, microservicePlacementLogic, getResourceInfo(monitoredDevices), applications, monitoredDevices);
            } else if (cdevice.getDeviceType().equals(MyFogDevice.FCN) || cdevice.isUserDevice()) {
                cdevice.initializeController(loadBalancer);
            }
            else {
                System.out.println("UNKNOWN FOGDEVICE TYPE DETECTED");
            }
        }
    }

    protected void initializeControllers(Object placementLogic, Map<Integer, List<FogDevice>> monitored) {
        for (FogDevice device : fogDevices) {
            LoadBalancer loadBalancer = new RRLoadBalancer();
            MyFogDevice cdevice = (MyFogDevice) device;

            //responsible for placement decision making
            if (cdevice.getDeviceType().equals(MyFogDevice.FON) || cdevice.getDeviceType().equals(MyFogDevice.CLOUD)) {
                List<FogDevice> monitoredDevices = monitored.get(cdevice.getFonId());
                MicroservicePlacementLogic microservicePlacementLogic;
                
                // Handle either string or int placementLogic
                if (placementLogic instanceof String) {
                    microservicePlacementLogic = placementLogicFactory.getPlacementLogic((String)placementLogic, cdevice.getId());
                } else if (placementLogic instanceof Number) {
                    microservicePlacementLogic = placementLogicFactory.getPlacementLogic(((Number)placementLogic).intValue(), cdevice.getId());
                } else {
                    Logger.error("Placement Logic Error", "Unknown placement logic type: " + placementLogic.getClass().getName());
                    microservicePlacementLogic = null;
                }
                
                cdevice.initializeController(loadBalancer, microservicePlacementLogic, getResourceInfo(monitoredDevices), applications, monitoredDevices);
            } else if (cdevice.getDeviceType().equals(MyFogDevice.FCN) || cdevice.isUserDevice()) {
                cdevice.initializeController(loadBalancer);
            }
            else {
                System.out.println("UNKNOWN FOGDEVICE TYPE DETECTED");
            }
        }
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.CONTROLLER_RESOURCE_MANAGE:
                manageResources();
                break;
            case FogEvents.GENERATE_PERIODIC_PR:
                generatePeriodicPlacementRequests();
                break;
            case FogEvents.USER_RESOURCE_UPDATE:
                processUserResourceUpdate(ev);
                break;
            case FogEvents.STOP_SIMULATION:
                CloudSim.stopSimulation();
                System.out.println("=========================================");
                System.out.println("============== METRICS ==================");
                System.out.println("=========================================");
                printTimeDetails();
//                printResourceConsumptionDetails();
                printPowerDetails();
                printCostDetails();
                printNetworkUsageDetails();
                printQoSDetails();
                endSimulation();
                // TODO Simon says don't System.exit
//                System.exit(0);
                break;
            // Handle mobility events
            case FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE:
                handleMovementUpdate((int) ev.getData());
                break;
            case FogEvents.MAKE_PATH:
                makePath((int) ev.getData());
                break;
            case FogEvents.OPERA_ACCIDENT_EVENT:
                handleAccidentEvent(ev);
                break;
        }
    }

    public void endSimulation() {
        CloudSim.stopSimulation();  // Stops the simulation internally
        CloudSim.clearQueues();  // A hypothetical method to clear static variables if implemented
    }

    /**
     * Enables mobility functionality by switching to the FullMobilityStrategy
     * Call in main Sim file after location data is loaded
     */
    public void enableMobility() {
        enableMobility(System.currentTimeMillis());
    }

    /**
     * Enables mobility for devices with a specific random seed.
     *
     * @param seed seed for random number generation
     */
    public void enableMobility(long seed) {
        this.mobilityEnabled = true;
        this.mobilityStrategy = new FullMobilityStrategy();
        
        // Initialize the strategy with current state
        // Map<Integer, Integer> initialParentReferences = new HashMap<>();
        // for (FogDevice device : fogDevices) {
        //     initialParentReferences.put(device.getId(), device.getParentId());
        // }
        // mobilityStrategy.initialize(fogDevices, initialParentReferences);
        
        System.out.println("Mobility enabled with seed: " + seed);
        
        // Make sure all mobility states use pathing strategies with the proper seed
        for (DeviceMobilityState state : deviceMobilityStates.values()) {
            PathingStrategy strategy = state.getStrategy();
            if (strategy instanceof AbstractPathingStrategy) {
                ((AbstractPathingStrategy)strategy).setSeed(seed);
                System.out.println("Set seed for " + strategy.getClass().getSimpleName());
            }
        }
    }
    
    /**
     * Handles a movement update event for a device
     * 
     * @param deviceId the device ID
     */
    protected void handleMovementUpdate(int deviceId) {
        DeviceMobilityState mobilityState = getDeviceMobilityState(deviceId);
        if (mobilityState == null) {
            Logger.error("Mobility Error", "No mobility state found for device " + deviceId);
            return;
        }
        
        double nextEventDelay = mobilityStrategy.handleMovementUpdate(deviceId, mobilityState, locationManager);
        
        if (nextEventDelay > 0) {
            // If there are more waypoints, schedule the next movement update
            if (!mobilityState.getPath().isEmpty()) {
                send(getId(), nextEventDelay, FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE, deviceId);
            } else {
                // If the device reached its destination, schedule the next path creation
                send(getId(), nextEventDelay, FogEvents.MAKE_PATH, deviceId);
            }
        }
        else {
            throw new NullPointerException("Negative delay time");
        }
    }
    
    /**
     * Creates a new path for a device to follow
     * 
     * @param deviceId the device ID
     */
    protected void makePath(int deviceId) {
        DeviceMobilityState mobilityState = getDeviceMobilityState(deviceId);
        if (mobilityState == null) {
            Logger.error("Mobility Error", "No mobility state found for device " + deviceId);
            return;
        }
        
        double delay = mobilityStrategy.makePath(deviceId, mobilityState);
        
        if (delay > 0) {
            send(getId(), delay, FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE, deviceId);
        }
    }
    
    /**
     * Starts mobility for a device by creating an initial path
     * 
     * @param deviceId the device to start moving
     */
    public void startDeviceMobility(int deviceId) {
        DeviceMobilityState mobilityState = getDeviceMobilityState(deviceId);
        if (mobilityState == null) {
            throw new NullPointerException("CRITICAL ERROR: No device mobility state found for device " + deviceId);
        }
        
        // Get the delay for the first movement from the strategy
        double delay = mobilityStrategy.startDeviceMobility(deviceId, mobilityState);
        
        // Schedule the movement update event if a valid delay was returned
        if (delay > 0) {
            send(getId(), delay, FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE, deviceId);
        }
        else Logger.error("Delay WARNING", "This user is NOT scheduled to move again. Check that user will call makePath in future.");
    }
    
    /**
     * Adds a landmark (point of interest) to the simulation
     * 
     * @param landmark the landmark to add
     */
    public void addLandmark(Attractor landmark) {
        mobilityStrategy.addLandmark(landmark);
    }
    
    /**
     * Gets all landmarks in the simulation
     * 
     * @return list of landmarks
     */
    public List<Attractor> getLandmarks() {
        return mobilityStrategy.getLandmarks();
    }

    /**
     * Register a device's mobility state with the location manager
     * 
     * @param deviceId the device ID
     * @param mobilityState the mobility state
     */
    public void registerDeviceMobilityState(int deviceId, DeviceMobilityState mobilityState) {
        deviceMobilityStates.put(deviceId, mobilityState);
    }

    /**
     * Gets a device's mobility state
     * 
     * @param deviceId the device ID
     * @return the device's mobility state, or null if not registered
     */
    public DeviceMobilityState getDeviceMobilityState(int deviceId) {
        return deviceMobilityStates.get(deviceId);
    }

    public Map<Integer, DeviceMobilityState> getDeviceMobilityStates() {
        return deviceMobilityStates;
    }

    /**
     * Initializes location data for resources and users.
     *
     * @param resourceFilename the filename for resource locations
     * @param userFilename the filename for user locations
     * @param numberOfResources the number of resources
     * @param numberOfUsers the number of users
     * @throws IOException if there's an error reading the files
     */
    public void initializeLocationData(String resourceFilename, String userFilename,
                                       int numberOfResources, int numberOfUsers) throws IOException {
        initializeLocationData(resourceFilename, userFilename, numberOfResources, numberOfUsers, 33);
    }

    /**
     * Initializes location data for resources and users with a specific random seed.
     *
     * @param resourceFilename the filename for resource locations
     * @param userFilename the filename for user locations
     * @param numberOfResources the number of resources
     * @param numberOfUsers the number of users
     * @param seed seed for random number generation
     * @throws IOException if there's an error reading the files
     */
    public void initializeLocationData(String resourceFilename, String userFilename, 
                                      int numberOfResources, int numberOfUsers, long seed) throws IOException {
        Map<String, Location> resourceLocations = dataLoader.loadResourceLocations(resourceFilename, numberOfResources);
        Map<Integer, Location> userLocations = dataLoader.loadInitialUserLocations(userFilename, numberOfUsers);
        
        Random random = new Random(seed);
        BeelinePathingStrategy beelinePathingStrategy = new BeelinePathingStrategy(seed);
        GraphHopperPathingStrategy graphHopperPathingStrategy = new GraphHopperPathingStrategy(seed);
        JitterBugPathingStrategy jitterBugPathingStrategy = new JitterBugPathingStrategy(seed);

        List<MyFogDevice> resourceDevices = new ArrayList<>();
        List<MyFogDevice> userDevices = new ArrayList<>();
        
        for (FogDevice device : fogDevices) {
            MyFogDevice fogDevice = (MyFogDevice) device;
            if (!fogDevice.isUserDevice()) {
                resourceDevices.add(fogDevice);
            } else {
                userDevices.add(fogDevice);
            }
        }
        
        // Sort devices for consistent mapping
        resourceDevices.sort(Comparator.comparingInt(FogDevice::getId));
        userDevices.sort(Comparator.comparingInt(FogDevice::getId));
        
        // Process resource devices - direct mapping from index to device
        for (int i = 0; i < Math.min(numberOfResources, resourceDevices.size()); i++) {
            MyFogDevice fogDevice = resourceDevices.get(i);
            // CSV indices start at 1
            String dataId = "res_" + i;
            int level = fogDevice.getLevel();
            
            if (resourceLocations.containsKey(dataId)) {
                locationManager.registerResourceLocation(
                        fogDevice.getId(),
                        resourceLocations.get(dataId),
                        dataId,
                        level
                );
                System.out.println("Mapped resource CSV index " + i + " to device ID " + fogDevice.getId());
            }
        }
        
        // Process user devices - direct mapping from index to device
        for (int i = 0; i < Math.min(numberOfUsers, userDevices.size()); i++) {
            MyFogDevice fogDevice = userDevices.get(i);
            int csvIndex = i + 1; // CSV indices start at 1
            
            // Register the device
            // Simon (100425) says we do NOT register in main Sim file anymore.
            registerUserDevice(fogDevice);
            fogDevice.setMicroservicesControllerId(getId());
            
            if (userLocations.containsKey(csvIndex)) {
                System.out.println("Mapped user CSV index " + csvIndex + " to device ID " + fogDevice.getId());
                
                if (fogDevice.getDeviceType().equals(MyFogDevice.GENERIC_USER)) {
                    DeviceMobilityState mobilityState = new GenericUserMobilityState(
                            userLocations.get(csvIndex),
                            beelinePathingStrategy,
                            1 + random.nextDouble() * 1.5 // m/s
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else if (fogDevice.getDeviceType().equals(MyFogDevice.AMBULANCE_USER)) {
                    DeviceMobilityState mobilityState = new AmbulanceUserMobilityState(
                            userLocations.get(csvIndex), // We don't use this, instead spawn user at hospital.
                            graphHopperPathingStrategy,
                            random.nextDouble() * 20 + 10 // 10 to 30 m/s
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else if (fogDevice.getDeviceType().equals(MyFogDevice.OPERA_USER)) {
                    DeviceMobilityState mobilityState = new OperaUserMobilityState(
                        userLocations.get(csvIndex), 
                        jitterBugPathingStrategy,
                        0.5 + random.nextDouble() * 1.5, // m/s
                        3600.0 // 1 hour
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else {
                    Logger.error("Invalid deviceType Error", "DeviceType is not Generic/Ambulance/Opera");
                }
            }
        }
        
        locationDataInitialized = true;
        System.out.println("Location data initialization complete with direct mapping.");
    }

    protected void generateRoutingTable() {
        Map<Integer, Map<Integer, Integer>> routing = ShortestPathRoutingGenerator.generateRoutingTable(fogDevices);

        for (FogDevice f : fogDevices) {
            ((MyFogDevice) f).addRoutingTable(routing.get(f.getId()));
        }
    }

    public void startEntity() {
        if (mobilityEnabled) {
            for (int deviceId : deviceMobilityStates.keySet()) {
                DeviceMobilityState mobilityState = deviceMobilityStates.get(deviceId);

                if (mobilityState != null) {
                    startDeviceMobility(deviceId);

                    System.out.println("Started mobility for device: " + CloudSim.getEntityName(deviceId) +
                            " at location: " + mobilityState.getCurrentLocation().latitude + ", " + mobilityState.getCurrentLocation().longitude);
                } else {
                    System.out.println("WARNING: No mobility state found for device " + CloudSim.getEntityName(deviceId));
                }
            }
        }

        // Keep track of user resources, then 
        // schedule first periodic PR generation
        initializeUserResources();
        send(getId(), prGenerationInterval, FogEvents.GENERATE_PERIODIC_PR);

        // todo Simon says Placement Decisions will be made dynamically, but only by the Cloud!
        // todo Hence no need for an initialisation
        if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC")
            Logger.error("Simulation not Dynamic error", "Simulation mode should be dynamic");
        if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC")
            initiatePlacementRequestProcessingDynamic();

        send(getId(), Config.CONTROLLER_RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
        send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);
    }

    public void initializeUserResources() {
        for (MyFogDevice device : userDevices) {
            Map<String, Double> resources = new HashMap<>();
            resources.put(ControllerComponent.CPU, (double) device.getHost().getTotalMips());
            resources.put(ControllerComponent.RAM, (double) device.getHost().getRam());
            resources.put(ControllerComponent.STORAGE, (double) device.getHost().getStorage());
            userResourceAvailability.put(device.getId(), resources);
        }
    }

    public void updateUserResourceUsage(int userId, String resourceType, double usage, boolean isIncrease) {
        if (userResourceAvailability.containsKey(userId)) {
            Map<String, Double> resources = userResourceAvailability.get(userId);
            double currentValue = resources.get(resourceType);
            if (isIncrease) {
                resources.put(resourceType, currentValue + usage);
            } else {
                resources.put(resourceType, currentValue - usage);
            }
        }
        else{
            Logger.error("Control Flow Error", "Tried to update user resource usage of a non-user device");
        }
    }

    /**
     * Checks whether a user device has sufficient resources to host a service.
     * <p>
     * This method is intended for use with user devices only. It verifies whether the user device
     * identified by {@code userId} has enough available CPU (MIPS), RAM, and storage to host
     * the first service (service) in the application loop defined by the given
     * {@link PlacementRequest}.
     * <p>
     * The specific microservice to check must already be specified in the {@code placedMicroservices}
     * field of the {@code PlacementRequest}. If no microservice is placed on the user device in
     * the request, the method assumes the device is not involved and returns {@code true}.
     * <p>
     * The method also validates that the {@link Application} instance corresponding to the request
     * is present and that resource requirements are accurately matched against current availability.
     *
     * @param userId the ID of the user device
     * @param pr the placement request containing the application and placement information
     * @return {@code true} if the user device can host the microservice; {@code false} otherwise
     */
    public boolean userCanFit(int userId, PlacementRequest pr) {
        if (!userResourceAvailability.containsKey(userId)) {
            Logger.error("Control Flow Error", "Tried to check resources of a non-user device.");
            return false;
        }
        
        String moduleToCheck = null;
        for (String module : pr.getPlacedMicroservices().keySet()) {
            if (pr.getPlacedMicroservices().get(module) == userId) {
                moduleToCheck = module;
                break;
            }
        }
        
        if (moduleToCheck == null) {
            return true; // No modules placed on this user
        }

        Application app = getApplicationById(pr.getApplicationId());

        if (app == null) {
            return false;
        }
        AppModule appModule = app.getModuleByName(moduleToCheck);
        Map<String, Double> resources = userResourceAvailability.get(userId);
        
        return resources.get(ControllerComponent.CPU) >= appModule.getMips() &&
            resources.get(ControllerComponent.RAM) >= appModule.getRam() &&
            resources.get(ControllerComponent.STORAGE) >= appModule.getSize();
    }

    public Application getApplicationById(String appId) {
        return applications.get(appId);
    }

    /**
     * Registers a user device for periodic PR generation
     */
    public void registerUserDevice(MyFogDevice device) {
        userDevices.add(device);
    }

    public int getNextSequenceNumber(int sensorId) {
        // Simon (020425) says the -1 initialization is because main sim file
        //  should initializes PRs once as PROTOTYPES for periodic generation.
        //  Those PRs are not processed, hence index should be -1.
        // TODO Potential problems with the -1 initialization if
        //  for example sensors get added mid-simulation, their PRs will start at -1
        int currentSeq = sensorToSequenceNumber.getOrDefault(sensorId, -1);
        int nextSeq = currentSeq + 1;
        sensorToSequenceNumber.put(sensorId, nextSeq);
        return nextSeq;
    }

    public void resetSequenceCounters() {
        sensorToSequenceNumber.clear();
    }

    public MyPlacementRequest createPlacementRequest(Sensor sensor, Map<String, Integer> placedMicroservices) {
        int sequenceNumber = getNextSequenceNumber(sensor.getId());
        String userType = ((MySensor) sensor).getUserType();

        // Create the placement request with the unique sequence number as the prId, including userType
        return new MyPlacementRequest(
                sensor.getAppId(),  // applicationId
                sensor.getId(),
                sequenceNumber,     // prId - now using a unique sequence per sensor
                sensor.getGatewayDeviceId(), // parent user device
                userType,           // userType from sensor
                placedMicroservices
        );
    }

    /**
     * Periodically generates and sends placement requests from user devices based on a predefined set.
     * <p>
     * This method iterates over all user devices and attempts to generate new placement requests
     * by creating deep copies of corresponding {@link PlacementRequest} objects stored in
     * {@code placementRequestDelayMap}. These requests are permanent templates and remain unchanged;
     * the newly generated copies are entirely independent, with no shared references (e.g., new
     * {@code LinkedHashMap} is created for microservice placements).
     * <p>
     * Before sending a request, the method checks whether the user device has sufficient resources
     * using {@code userCanFit(...)}, ensuring that CPU, RAM, and storage requirements are met.
     * If the user device is eligible, the request is transmitted to it along with the corresponding
     * {@link Application} object.
     * <p>
     * This function is periodically scheduled using the static {@code prGenerationInterval}, which
     * defines the interval between consecutive invocations. In future versions, this interval may
     * be varied per-request by using the delay values from {@code placementRequestDelayMap}.
     */
    public void generatePeriodicPlacementRequests() {
        if (placementRequestDelayMap.isEmpty()) {
            Logger.debug("WARNING: placementRequestDelayMap", "No placements were initialized in Sim file! Ensure that this is intentional.");
            return;
        }

        MicroservicePlacementConfig.FAILURE_REASON reason = MicroservicePlacementConfig.FAILURE_REASON.USER_LACKED_RESOURCES;

        for (MyFogDevice userDevice : userDevices) {
            MyPlacementRequest existingPR = null;
            for (PlacementRequest pr : placementRequestDelayMap.keySet()) {
                if (pr.getRequester() == userDevice.getId()) {
                    existingPR = (MyPlacementRequest) pr;
                    break;
                }
            }
            
            if (existingPR != null) {
                // Requester's new parent is accounted for because we send TRANSMIT_PR to requester,
                //  and requester knows its new parent.
                MyPlacementRequest newPR = new MyPlacementRequest(
                    existingPR.getApplicationId(),
                    existingPR.getSensorId(),
                    getNextSequenceNumber(existingPR.getSensorId()),
                    userDevice.getId(),
                    existingPR.getUserType(),
                    new LinkedHashMap<>(existingPR.getPlacedMicroservices())
                );

                if (userCanFit(userDevice.getId(), newPR)) {
                    JSONObject jsonSend = new JSONObject();
                    jsonSend.put("PR", newPR);
                    jsonSend.put("app", applications.get(newPR.getApplicationId()));
                    // TODO Should we use the delay from placementRequestDelayMap instead?
                    sendNow(userDevice.getId(), FogEvents.TRANSMIT_PR, jsonSend);
                } else {
                    Logger.error("PR failed because", reason + userDevice.getName());
                    MyMonitor.getInstance().recordFailedPR(newPR, reason);
                }
            }
            else{
                throw new NullPointerException("PR doesn't exist");
            }
        }
        
        send(getId(), prGenerationInterval, FogEvents.GENERATE_PERIODIC_PR);
    }

    protected void initiatePlacementRequestProcessingDynamic() {
        if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC) {
            for (FogDevice f : fogDevices) {
                // todo Simon says for the Offline POC there are no proxy servers, so the cloud processes all PRs
                //  The second OR condition was added for Offline POC, whether it stays tbc
                if (((MyFogDevice) f).getDeviceType() == MyFogDevice.FON || ((MyFogDevice) f).getDeviceType() == MyFogDevice.CLOUD) {
                    sendNow(f.getId(), FogEvents.PROCESS_PRS);
                }
            }
        }
        else {
            throw new NullPointerException("(100425) Only have implementation for periodic Microservice placement");
        }
    }

    private void processUserResourceUpdate(SimEvent ev) {
        JSONObject objj = (JSONObject) ev.getData();
        int userId = (int) objj.get("id");
        AppModule module = (AppModule) objj.get("module");
        boolean isDecrease = (boolean) objj.get("isDecrease");

        // Update resource availability for user device
        if (userResourceAvailability.containsKey(userId)) {
            updateUserResourceUsage(userId, ControllerComponent.CPU, module.getMips(), !isDecrease);
            updateUserResourceUsage(userId, ControllerComponent.RAM, module.getRam(), !isDecrease);
            updateUserResourceUsage(userId, ControllerComponent.STORAGE, module.getSize(), !isDecrease);

            String action = isDecrease ? "Decreased" : "Restored";
            Logger.debug("Resource Management",
                    action + " resources for user " + CloudSim.getEntityName(userId) + " for module " + module.getName());
        } else {
            Logger.error("Resource Management Error",
                    "Tried to update resources for non-user device " + CloudSim.getEntityName(userId));
        }
    }

    protected void printQoSDetails() {
        System.out.println("=========================================");
        System.out.println("APPLICATION QOS SATISFACTION");
        System.out.println("=========================================");
        double success = 0;
        double total = 0;
        // TODO simon says make this work or remove completely
        for (Integer loopId : TimeKeeper.getInstance().getLoopIdToLatencyQoSSuccessCount().keySet()) {
            success += TimeKeeper.getInstance().getLoopIdToLatencyQoSSuccessCount().get(loopId);
            total += TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loopId);
        }

        double successPercentage = success / total * 100;
        System.out.println("Makespan" + " ---> " + successPercentage);
    }

    protected void printCostDetails() {
        System.out.println("Cost of execution in cloud = " + getCloud().getTotalCost());
    }

    @Override
    public void shutdownEntity() {
    }

    protected void manageResources() {
        // todo Simon says this does nothing, doesnt it???
        send(getId(), Config.CONTROLLER_RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
    }

    protected void printNetworkUsageDetails() {
        System.out.println("Total network usage = " + NetworkUsageMonitor.getNetworkUsage() / Config.MAX_SIMULATION_TIME);
    }

    protected FogDevice getCloud() {
        for (FogDevice dev : fogDevices)
            if (dev.getName().equals("cloud"))
                return dev;
        return null;
    }

    protected void printPowerDetails() {
        StringBuilder energyInfo = new StringBuilder();
        for (FogDevice fogDevice : fogDevices) {
            String energyPerDevice = fogDevice.getName() + " : Energy Consumed = " + fogDevice.getEnergyConsumption() + "\n";
            energyInfo.append(energyPerDevice);
        }
        System.out.println(energyInfo.toString());
    }

    protected String getStringForLoopId(int loopId) {
        for (String appId : applications.keySet()) {
            Application app = applications.get(appId);
            for (AppLoop loop : app.getLoops()) {
                if (loop.getLoopId() == loopId)
                    return loop.getModules().toString();
            }
        }
        return null;
    }

    protected void printTimeDetails() {
        TimeKeeper t = TimeKeeper.getInstance();
        System.out.println("Simon START TIME : " + TimeKeeper.getInstance().getSimulationStartTime());
        System.out.println("Simon END TIME : " + Calendar.getInstance().getTimeInMillis());
        System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        for (Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()) {
            System.out.println(getStringForLoopId(loopId) + " ---> " + TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
        }
        System.out.println("=========================================");
        System.out.println("AVERAGE CPU EXECUTION DELAY PER TUPLE TYPE");
        System.out.println("=========================================");

        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }

        System.out.println("=========================================");
    }

//    protected void printResourceConsumptionDetails() {
//        // TODO Simon (040225) says print something useful instead of the device IDs
//        //  Like utilisation? Standard deviation of utilisation? Ask Dr Cabrera
//        //  Also NOTE that the entries are not sorted in key (timestamp) order, annoying
//        //  Maybe need TreeMap
//        Map<Double, List<MyHeuristic.DeviceState>> ss = MyMonitor.getInstance().getSnapshots();
//        ss.forEach((key, value) ->
//                value.forEach((deviceState) -> System.out.println(key + ": " + deviceState.getId())));
//    }

    /**
     * Extracts and returns resource availability information (CPU, RAM, Storage)
     * for a filtered list of {@link FogDevice} instances.
     * <p>
     * This method is typically called after selecting devices to be monitored
     * for resource availability, such as those managed by a FON or the cloud.
     * </p>
     *
     * @param fogDevices the list of {@link FogDevice} instances to include.
     * @return a map from device ID to a map of resource types and their capacities.
     */
    protected Map<Integer, Map<String, Double>> getResourceInfo(List<FogDevice> fogDevices) {
        Map<Integer, Map<String, Double>> resources = new HashMap<>();
        for (FogDevice device : fogDevices) {
            if (Objects.equals(((MyFogDevice) device).getDeviceType(), MyFogDevice.FCN)) {
                Map<String, Double> perDevice = new HashMap<>();
                perDevice.put(ControllerComponent.CPU, (double) device.getHost().getTotalMips());
                perDevice.put(ControllerComponent.RAM, (double) device.getHost().getRam());
                perDevice.put(ControllerComponent.STORAGE, (double) device.getHost().getStorage());
                resources.put(device.getId(), perDevice);
            }
        }
        return resources;
    }

    public void submitPlacementRequests(List<PlacementRequest> placementRequests, int delay) {
        for (PlacementRequest p : placementRequests) {
            placementRequestDelayMap.put(p, delay);
        }
    }

    protected FogDevice getFogDeviceById(int id) {
        // Simono (090425) says Consider cloudsim?
        for (FogDevice f : fogDevices) {
            if (f.getId() == id)
                return f;
        }
        return null;
    }

    /**
     * Modified connectWithLatencies to use LocationManager for determining parent-child relationships
     * based on proximity for user devices
     */
    protected void connectWithLatencies() {
        // If location data hasn't been initialized yet, fall back to default behavior
        if (!locationDataInitialized) {
            System.out.println("WARNING: Location data not initialized. Using default parent-child relationships.");
            for (FogDevice fogDevice : fogDevices) {
                if (fogDevice.getParentId() >= 0) {
                    FogDevice parent = getFogDeviceById(fogDevice.getParentId());
                    if (parent == null)
                        continue;
                    double latency = fogDevice.getUplinkLatency();
                    parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
                    parent.getChildrenIds().add(fogDevice.getId());
                }
            }
            return;
        }

        int cloudId = getCloud().getId();
        
        for (FogDevice device : fogDevices) {
            MyFogDevice fogDevice = (MyFogDevice) device;
            
            // Connect non-user devices using existing parent IDs
            if (!fogDevice.isUserDevice()) {
                // Cloud to edge
                if (fogDevice.getParentId() > -1) {
                    FogDevice parent = getFogDeviceById(fogDevice.getParentId());
                    if (parent.getId() != cloudId) throw new NullPointerException("Invalid parent. Must be cloud.");

                    double latency = locationManager.calculateDirectLatency(fogDevice.getId(), cloudId);
                    fogDevice.setUplinkLatency(latency);
                    parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
                    parent.getChildrenIds().add(fogDevice.getId());
                    System.out.println("Connected device " + fogDevice.getName() + " to parent " + parent.getName());
                }
            }
            // Connect user devices based on proximity
            else {
                // Find the nearest parent for this user device
                int parentId = locationManager.determineParentByProximity(fogDevice.getId(), fogDevices);

                if (parentId > -1) {
                    FogDevice parent = getFogDeviceById(parentId);
                    fogDevice.setParentId(parentId);

                    // Calculate latency based on distance
                    double latency = locationManager.calculateDirectLatency(fogDevice.getId(), parentId);
                    fogDevice.setUplinkLatency(latency);

                    parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
                    parent.getChildrenIds().add(fogDevice.getId());

                    System.out.println("Connected user device " + fogDevice.getName() +
                            " to parent " + parent.getName() +
                            " with latency " + latency);
                }
                else {
                    System.out.println("WARNING: Could not find a parent for user device " + fogDevice.getName());
                }
            }
        }
    }

    /**
     * Returns the list of {@link FogDevice} instances managed by the given root device
     * This implementation uses Cloud, but can be adjusted for FON.
     * <p>
     * Collects all Fog Computation Nodes (FCNs) in the hierarchy beneath the root and assigns
     * them the FON ID of the root. If the root is not the cloud but has the cloud as its parent, the cloud
     * is also included.
     * </p>
     * <p>
     * Used to determine the devices a Fog Orchestration Node (FON) is responsible for. Can be extended for
     * custom clustering strategies.
     * </p>
     *
     * @param f the root {@link FogDevice}, in this case the cloud.
     * @return list of {@link FogDevice} instances associated with the root.
     */
    protected List<FogDevice> getDevicesForFON(FogDevice f) {
        List<FogDevice> fogDevices = new ArrayList<>();
        fogDevices.add(f);
        ((MyFogDevice) f).setFonID(f.getId());
        List<FogDevice> connected = new ArrayList<>();
        connected.add(f);
        boolean changed = true;
        boolean isCloud = ((MyFogDevice) f).getDeviceType().equals(MyFogDevice.CLOUD);
        
        while (changed) {
            changed = false;
            List<FogDevice> rootNodes = new ArrayList<>();
            for (FogDevice d : connected)
                rootNodes.add(d);
            for (FogDevice rootD : rootNodes) {
                for (int child : rootD.getChildrenIds()) {
                    FogDevice device = getFogDeviceById(child);
                    connected.add(device);
                    if (!fogDevices.contains(device)) {
                        MyFogDevice mfd = (MyFogDevice) device;
                        fogDevices.add(mfd);
                        mfd.setFonID(f.getId());
                        changed = true;
                    }
                }
                connected.remove(rootD);
            }
        }
        
        // Cloud doesn't need its parent in the list
        if (!isCloud) {
            int parentId = f.getParentId();
            if (parentId != -1) {
                MyFogDevice fogDevice = (MyFogDevice) getFogDeviceById(parentId);
                if (fogDevice.getDeviceType().equals(MyFogDevice.CLOUD))
                    fogDevices.add(fogDevice);
            }
        }
        return fogDevices;
    }

    public Map<Integer, Map<String, Double>> getUserResourceAvailability() {
        return userResourceAvailability;
    }

    // Old methods for backward compatibility
    @Deprecated
    protected void initializeControllers(int placementLogic) {
        initializeControllers((Object)placementLogic);
    }
    
    @Deprecated
    protected void initializeControllers(int placementLogic, Map<Integer, List<FogDevice>> monitored) {
        initializeControllers((Object)placementLogic, monitored);
    }

    /**
     * Handles the opera accident event and notifies all relevant mobility states
     * @param ev The simulation event
     */
    private void handleAccidentEvent(SimEvent ev) {
        System.out.println("⚠️ ACCIDENT EVENT AT OPERA HOUSE at time: " + CloudSim.clock());
        
        // Extract any event data if needed
        Object eventData = ev.getData();
        
        // Forward the event to all relevant mobility states
        for (Map.Entry<Integer, DeviceMobilityState> entry : deviceMobilityStates.entrySet()) {
            DeviceMobilityState state = entry.getValue();
            if (state.handleEvent(FogEvents.OPERA_ACCIDENT_EVENT, eventData)) {
                System.out.println("Device " + CloudSim.getEntityName(entry.getKey()) + 
                                   " responded to accident event");
            }
        }
    }
}
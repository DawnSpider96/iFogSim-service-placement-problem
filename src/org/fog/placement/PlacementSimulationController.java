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
import org.fog.utils.distribution.PoissonDistribution;

import java.io.IOException;
import java.util.*;
import java.util.Comparator;


public class PlacementSimulationController extends SimEntity {

    protected List<FogDevice> fogDevices;
    protected List<Sensor> sensors;
    protected List<Actuator> actuators;
    private Map<Integer, Integer> sensorToSequenceNumber = new HashMap<>();
    protected Map<String, Application> applications = new HashMap<>();
    protected PlacementLogicFactory placementLogicFactory = new PlacementLogicFactory();
    protected Object placementLogic; // Can be either int or String depending on configuration source
    private boolean mobilityEnabled = false;
    private Random applicationSelectionRandom; // For seeded random application selection
    //    protected List<Integer> clustering_levels;
    /**
     * A permanent set of Placement Requests, initialized with one per user device.
     * For PR generation, simulation will always make copies of these.
     */
    protected Map<PlacementRequest, Integer> placementRequestDelayMap = new HashMap<>();

    // For PR generation
    private List<SPPFogDevice> userDevices = new ArrayList<>();
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
    
    // Add these new fields after the existing field declarations (around line 35-40)
    private Map<Integer, List<PassiveSensor>> deviceToSensors = new HashMap<>();
    private Map<Integer, List<Actuator>> deviceToActuators = new HashMap<>();

    // Add a field for the Poisson distribution
    private PoissonDistribution poissonDistribution;

    // Field to store the heuristic seed
    private int heuristicSeed = 456; // Default value

    /**
     * Resets all instance state that should not persist between experiments.
     * Call this method at the beginning of each new experiment.
     */
    public void reset() {
        // Clear sequence numbers
        sensorToSequenceNumber.clear();
        
        // Clear user devices and resource tracking
        userDevices.clear();
        userResourceAvailability.clear();
        
        // Clear device mappings
        deviceToSensors.clear();
        deviceToActuators.clear();
        
        // Clear placement requests
        placementRequestDelayMap.clear();
        
        // Clear mobility states
        deviceMobilityStates.clear();
        
        // Reset mobility strategy
        mobilityStrategy = new NoMobilityStrategy();
        
        // Location data initialization flag
        locationDataInitialized = false;
    }

    /**
     * Constructor supporting both integer and string placement logic identifiers.
     * 
     * @param name Controller name
     * @param fogDevices List of fog devices
     * @param sensors List of sensors
     * @param actuators List of actuators
     * @param applications List of applications
     * @param placementLogic Placement logic identifier (can be Integer or String)
     * @param experimentSeed Seed for random number generation
     */
    public PlacementSimulationController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                                         List<Actuator> actuators, List<Application> applications,
                                         Object placementLogic, int experimentSeed) {
        super(name);
        // Reset any previous state
        reset();
        
        this.fogDevices = fogDevices;
        this.sensors = sensors;
        this.actuators = actuators;
        this.placementLogic = placementLogic;
        for (Application app : applications) {
            this.applications.put(app.getAppId(), app);
        }

        // Initialize the location management components
        initializeLocationComponents();
        
        // Initialize with the no-op mobility strategy by default
        this.mobilityStrategy = new NoMobilityStrategy();
        
        // Initialize application selection random with provided seed
        this.applicationSelectionRandom = new Random(experimentSeed);
    }

    /**
     * Constructor supporting both integer and string placement logic identifiers (without seed).
     * 
     * @param name Controller name
     * @param fogDevices List of fog devices
     * @param sensors List of sensors
     * @param actuators List of actuators
     * @param applications List of applications
     * @param placementLogic Placement logic identifier (can be Integer or String)
     */
    public PlacementSimulationController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                                         List<Actuator> actuators, List<Application> applications,
                                         Object placementLogic) {
        this(name, fogDevices, sensors, actuators, applications, placementLogic, 33); // Default seed
    }

    /**
     * Alternative constructor supporting legacy integer placement logic.
     * 
     * @param name Controller name
     * @param fogDevices List of fog devices
     * @param sensors List of sensors
     * @param actuators List of actuators
     * @param applications List of applications
     * @param placementLogic Integer placement logic type
     */
    public PlacementSimulationController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                                         List<Actuator> actuators, List<Application> applications,
                                         int placementLogic) {
        this(name, fogDevices, sensors, actuators, applications, (Object)placementLogic, 33); // Default seed
    }

    // Add a constructor with monitored devices and seed
//    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
//                                     List<Actuator> actuators, List<Application> applications,
//                                     Object placementLogic, Map<Integer, List<FogDevice>> monitored,
//                                     int experimentSeed) {
//        super(name);
//        this.fogDevices = fogDevices;
//        this.sensors = sensors;
//        this.actuators = actuators;
//        this.placementLogic = placementLogic;
//        for (Application app : applications) {
//            this.applications.put(app.getAppId(), app);
//        }
//
//        // Initialize the location management components
//        initializeLocationComponents();
//
//        // Initialize with the no-op mobility strategy by default
//        this.mobilityStrategy = new NoMobilityStrategy();
//
//        // Initialize application selection random with provided seed
//        this.applicationSelectionRandom = new Random(experimentSeed);
//    }

    // Update the existing constructor with monitored devices to use the new one with seed
//    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
//                                     List<Actuator> actuators, List<Application> applications,
//                                     Object placementLogic, Map<Integer, List<FogDevice>> monitored) {
//        this(name, fogDevices, sensors, actuators, applications, placementLogic, monitored, 33); // Default seed
//    }

    /**
     * Constructor that takes a map of interval values in seconds for the Poisson distribution
     * Interval values are automatically converted to lambda values (lambda = 1/interval)
     */
    public PlacementSimulationController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                                         List<Actuator> actuators, List<Application> applications,
                                         Object placementLogic, Map<String, Integer> intervalValues,
                                         int experimentSeed, int heuristicSeed) {
        this(name, fogDevices, sensors, actuators, applications, placementLogic, 
             intervalValues, experimentSeed, heuristicSeed, MicroservicePlacementConfig.PLACEMENT_PROCESS_INTERVAL);
    }

    /**
     * Constructor that takes a map of interval values in seconds and placement process interval
     */
    public PlacementSimulationController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                                         List<Actuator> actuators, List<Application> applications,
                                         Object placementLogic, Map<String, Integer> intervalValues,
                                         int experimentSeed, int heuristicSeed, double placementProcessInterval) {
        this(name, fogDevices, sensors, actuators, applications, placementLogic, experimentSeed);
        
        // Store the heuristic seed
        this.heuristicSeed = heuristicSeed;
        
        // The placement process interval is now stored directly on the fog devices
        // that need it, so we don't need to store it here anymore
        
        // Initialize the Poisson distribution with interval values
        if (intervalValues != null && !intervalValues.isEmpty()) {
            initializePoissonDistributionWithIntervals(intervalValues, experimentSeed);
        }
    }

    /**
     * This constructor has been deprecated and should not be used.
     * Use the constructor with intervalValues instead.
     */
    @Deprecated
    public PlacementSimulationController withLambdaValues(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                                                          List<Actuator> actuators, List<Application> applications,
                                                          Object placementLogic, Map<String, Double> lambdaValues, int experimentSeed) {
        // This method is kept only for compatibility but should not be used
        throw new UnsupportedOperationException("This method is deprecated. Use constructor with interval values instead.");
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
            SPPFogDevice cdevice = (SPPFogDevice) device;
            
            // Note: We're keeping the microservicesControllerId as it might still be needed for other purposes
            // But we're removing the comment that specifically mentions it's for the placement process interval
            cdevice.setMicroservicesControllerId(getId());

            // responsible for placement decision-making
            if (cdevice.getDeviceType().equals(SPPFogDevice.FON) || cdevice.getDeviceType().equals(SPPFogDevice.CLOUD)) {
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
                
                // Set the seed if the placement logic is a MyHeuristic instance
                if (microservicePlacementLogic instanceof SPPHeuristic) {
                    ((SPPHeuristic) microservicePlacementLogic).setSeed(heuristicSeed);
                    System.out.println("Set heuristic seed to " + heuristicSeed + " for " + 
                        microservicePlacementLogic.getClass().getSimpleName());
                }
                
                cdevice.initializeController(loadBalancer, microservicePlacementLogic, getResourceInfo(monitoredDevices), applications, monitoredDevices);
            } else if (cdevice.getDeviceType().equals(SPPFogDevice.FCN) || cdevice.isUserDevice()) {
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
            SPPFogDevice cdevice = (SPPFogDevice) device;

            //responsible for placement decision making
            if (cdevice.getDeviceType().equals(SPPFogDevice.FON) || cdevice.getDeviceType().equals(SPPFogDevice.CLOUD)) {
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
                
                // Set the seed if the placement logic is a MyHeuristic instance
                if (microservicePlacementLogic instanceof SPPHeuristic) {
                    ((SPPHeuristic) microservicePlacementLogic).setSeed(heuristicSeed);
                    System.out.println("Set heuristic seed to " + heuristicSeed + " for " + 
                        microservicePlacementLogic.getClass().getSimpleName());
                }
                
                cdevice.initializeController(loadBalancer, microservicePlacementLogic, getResourceInfo(monitoredDevices), applications, monitoredDevices);
            } else if (cdevice.getDeviceType().equals(SPPFogDevice.FCN) || cdevice.isUserDevice()) {
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
            case FogEvents.GENERATE_PR_FOR_DEVICE:
                generatePrForDevice((int) ev.getData());
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
     * Enables mobility for the simulation.
     */
    public void enableMobility() {
        this.mobilityEnabled = true;
        this.mobilityStrategy = new FullMobilityStrategy();
        
        // Initialize the strategy with current state
        // Map<Integer, Integer> initialParentReferences = new HashMap<>();
        // for (FogDevice device : fogDevices) {
        //     initialParentReferences.put(device.getId(), device.getParentId());
        // }
        // mobilityStrategy.initialize(fogDevices, initialParentReferences);
        
        System.out.println("Mobility enabled.");
    }

    public void setPathingSeeds(long seed) {
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
        else Logger.error("Delay WARNING", "This " + mobilityState + " user is NOT scheduled to move again. Check that user will call makePath in future.");
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
//        BeelinePathingStrategy genericPathingStrategy = new BeelinePathingStrategy(seed);
        AbstractPathingStrategy genericPathingStrategy = mobilityEnabled ?
                new GraphHopperPathingStrategy(seed, "foot") : new BeelinePathingStrategy(seed);
        // Option "type". Only return GraphHopperPathingStrategy if mobility enabled.
        //  Because Ambulance user will always call makepath in response to the accident event.
        AbstractPathingStrategy ambulancePathingStrategy = mobilityEnabled ?
            new GraphHopperPathingStrategy(seed) : new LazyBugPathingStrategy(seed);
        JitterBugPathingStrategy operaPathingStrategy = new JitterBugPathingStrategy(seed);

        List<SPPFogDevice> resourceDevices = new ArrayList<>();
        List<SPPFogDevice> userDevices = new ArrayList<>();
        
        for (FogDevice device : fogDevices) {
            SPPFogDevice fogDevice = (SPPFogDevice) device;
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
            SPPFogDevice fogDevice = resourceDevices.get(i);
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
            SPPFogDevice fogDevice = userDevices.get(i);
            int csvIndex = i + 1; // CSV indices start at 1
            
            // Register the device
            registerUserDevice(fogDevice);
            fogDevice.setMicroservicesControllerId(getId());
            
            if (userLocations.containsKey(csvIndex)) {
                System.out.println("Mapped user CSV index " + csvIndex + " to device ID " + fogDevice.getId());
                
                if (fogDevice.getDeviceType().equals(SPPFogDevice.GENERIC_USER)) {
//                    DeviceMobilityState mobilityState = new GenericUserMobilityState(
//                            userLocations.get(csvIndex),
//                            genericPathingStrategy,
//                            1 + random.nextDouble() * 1.5 // m/s
//                    );
                    DeviceMobilityState mobilityState = new GenericUserMobilityState(
                            userLocations.get(csvIndex),
                            genericPathingStrategy,
                            1.38 // m/s
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else if (fogDevice.getDeviceType().equals(SPPFogDevice.AMBULANCE_USER)) {
//                    DeviceMobilityState mobilityState = new AmbulanceUserMobilityState(
//                            userLocations.get(csvIndex), // We don't use this, instead spawn user at hospital.
//                            ambulancePathingStrategy,
//                            random.nextDouble() * 20 + 10 // 10 to 30 m/s
//                    );
                    DeviceMobilityState mobilityState = new AmbulanceUserMobilityState(
                            userLocations.get(csvIndex), // We don't use this, instead spawn user at hospital.
                            ambulancePathingStrategy,
                            8.3
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else if (fogDevice.getDeviceType().equals(SPPFogDevice.OPERA_USER)) {
                    DeviceMobilityState mobilityState = new OperaUserMobilityState(
                        userLocations.get(csvIndex), 
                        operaPathingStrategy,
                        3.5 + random.nextDouble() * 1.5, // m/s
                        600.0 // 10 minutes
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else if (fogDevice.getDeviceType().equals(SPPFogDevice.IMMOBILE_USER)) {
                    DeviceMobilityState mobilityState = new ImmobileUserMobilityState(
                            userLocations.get(csvIndex)
                    );
                    registerDeviceMobilityState(fogDevice.getId(), mobilityState);
                }
                else {
                    throw new NullPointerException("Invalid deviceType Error");
                }
            }
        }
        
        locationDataInitialized = true;
        System.out.println("Location data initialization complete with direct mapping.");
    }

    protected void generateRoutingTable() {
        Map<Integer, Map<Integer, Integer>> routing = ShortestPathRoutingGenerator.generateRoutingTable(fogDevices);

        for (FogDevice f : fogDevices) {
            ((SPPFogDevice) f).addRoutingTable(routing.get(f.getId()));
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
        } else {
            // Even with mobility disabled, initialize locations for reporting
            for (int deviceId : deviceMobilityStates.keySet()) {
                DeviceMobilityState mobilityState = deviceMobilityStates.get(deviceId);
                if (mobilityState != null) {
                    System.out.println("Device: " + CloudSim.getEntityName(deviceId) +
                            " positioned at: " + mobilityState.getCurrentLocation().latitude + 
                            ", " + mobilityState.getCurrentLocation().longitude + 
                            " (mobility disabled)");
                }
            }
        }

        // Keep track of user resources, then 
        // schedule first periodic PR generation
        initializeUserResources();
        send(getId(), prGenerationInterval, FogEvents.GENERATE_PERIODIC_PR);

        // Placement Decisions will be made dynamically, but only by the Cloud!
        // Hence no need for an initialisation
        if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC")
            Logger.error("Simulation not Dynamic error", "Simulation mode should be dynamic");
        if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC")
            initiatePlacementRequestProcessingDynamic();

        send(getId(), Config.CONTROLLER_RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
        send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);
    }

    public void initializeUserResources() {
        for (SPPFogDevice device : userDevices) {
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
        for (String module : pr.getPlacedServices().keySet()) {
            if (pr.getPlacedServices().get(module) == userId) {
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
    public void registerUserDevice(SPPFogDevice device) {
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

    public ContextPlacementRequest createPlacementRequest(Sensor sensor, Map<String, Integer> placedMicroservices, double requestLatency) {
        int sequenceNumber = getNextSequenceNumber(sensor.getId());
        String userType = ((PassiveSensor) sensor).getUserType();

        // Create the placement request with the unique sequence number as the prId, including userType
        return new ContextPlacementRequest(
                sensor.getAppId(),  // applicationId
                sensor.getId(),
                sequenceNumber,     // prId - now using a unique sequence per sensor
                sensor.getGatewayDeviceId(), // parent user device
                userType,           // userType from sensor
                placedMicroservices,
                requestLatency
        );
    }

    /**
     * Periodically generates and sends placement requests for each user device.
     * Now uses Poisson distribution to determine the delay for each device based on its user type.
     */
    public void generatePeriodicPlacementRequests() {
        // Check if we have a Poisson distribution initialized
        if (poissonDistribution == null) {
            // Fall back to uniform distribution if Poisson is not initialized
            for (SPPFogDevice userDevice : userDevices) {
                send(getId(), 0, FogEvents.GENERATE_PR_FOR_DEVICE, userDevice.getId());
            }
        } else {
            // Use Poisson distribution with user-type specific lambda values
            for (SPPFogDevice userDevice : userDevices) {
                String userType = userDevice.getDeviceType();
                double delay;
                
                try {
                    // Try to use the user-type specific lambda value
                    if (poissonDistribution.hasLambdaForUserType(userType)) {
                        delay = poissonDistribution.getNextValue(userType);
                    } else {
                        throw new NullPointerException("Invalid user type");
                    }
                } catch (NullPointerException e) {
                    // Handle case where user type is not valid
                    System.err.println("Warning: Could not get time interval for user type: " + userType);
                    delay = poissonDistribution.getNextValue(); // Use default
                }
                
                // Schedule the PR generation with the calculated delay
                send(getId(), delay, FogEvents.GENERATE_PR_FOR_DEVICE, userDevice.getId());
            }
        }
        
        // Schedule the next periodic PR generation
//        send(getId(), prGenerationInterval, FogEvents.GENERATE_PERIODIC_PR);
    }

    protected void initiatePlacementRequestProcessingDynamic() {
        if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC) {
            for (FogDevice f : fogDevices) {

                if (((SPPFogDevice) f).getDeviceType() == SPPFogDevice.FON || ((SPPFogDevice) f).getDeviceType() == SPPFogDevice.CLOUD) {
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
            if (Objects.equals(((SPPFogDevice) device).getDeviceType(), SPPFogDevice.FCN)) {
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
            SPPFogDevice fogDevice = (SPPFogDevice) device;
            
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
        ((SPPFogDevice) f).setFonID(f.getId());
        List<FogDevice> connected = new ArrayList<>();
        connected.add(f);
        boolean changed = true;
        boolean isCloud = ((SPPFogDevice) f).getDeviceType().equals(SPPFogDevice.CLOUD);
        
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
                        SPPFogDevice mfd = (SPPFogDevice) device;
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
                SPPFogDevice fogDevice = (SPPFogDevice) getFogDeviceById(parentId);
                if (fogDevice.getDeviceType().equals(SPPFogDevice.CLOUD))
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
        int respondedCount = 0;
        int operaUserCount = 0;
        int operaUsersAtOperaCount = 0;
        int ambulanceUserCount = 0;
        
        for (Map.Entry<Integer, DeviceMobilityState> entry : deviceMobilityStates.entrySet()) {
            int deviceId = entry.getKey();
            DeviceMobilityState state = entry.getValue();
            if (state == null) {
                Logger.error("Mobility Error", "Null mobility state found for device " + deviceId);
                continue;
            }
            
            // Count user types for statistics
            String deviceName = CloudSim.getEntityName(deviceId);
            if (deviceName.contains("Opera")) {
                operaUserCount++;
                if (state.getStatus().toString().equals("AT_OPERA")) {
                    operaUsersAtOperaCount++;
                }
            } else if (deviceName.contains("Ambulance")) {
                ambulanceUserCount++;
            }
            
            try {
                double delay = state.handleEvent(FogEvents.OPERA_ACCIDENT_EVENT, eventData);
                if (delay > 0) {
                    // Schedule the next movement with the returned delay
                    send(getId(), delay, FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE, deviceId);
                    respondedCount++;
                    System.out.println("Device " + deviceName + " responded to accident event and will move in " + delay + " time units");
                }
            } catch (Exception e) {
                Logger.error("Exception in handleAccidentEvent", 
                    "Device " + deviceName + " error: " + e.getMessage());
            }
        }
        
        System.out.println("Accident Event Statistics:");
        System.out.println("- Total Opera Users: " + operaUserCount);
        System.out.println("- Opera Users at Opera: " + operaUsersAtOperaCount + 
                          " (" + (mobilityEnabled ? "mobility enabled" : "mobility disabled") + ")");
        System.out.println("- Total Ambulance Users: " + ambulanceUserCount);
        System.out.println("- Total Devices Responded: " + respondedCount);
    }

    /**
     * Gets a random application from the pool, using a seeded random number generator.
     * 
     * @return A randomly selected application from the application pool
     */
    private Application getRandomApplication() {
        List<String> appKeys = new ArrayList<>(applications.keySet());
        int randomIndex = applicationSelectionRandom.nextInt(appKeys.size());
        return applications.get(appKeys.get(randomIndex));
    }

    /**
     * Registers the mappings between devices and their sensors/actuators.
     * This should be called after all devices, sensors, and actuators are created.
     */
    public void registerDeviceMappings() {
        // Initialize the maps
        deviceToSensors.clear();
        deviceToActuators.clear();
        
        // Populate the maps from the existing sensors and actuators lists
        for (Sensor s : sensors) {
            if (s instanceof PassiveSensor) {
                int deviceId = s.getGatewayDeviceId();
                if (!deviceToSensors.containsKey(deviceId)) {
                    deviceToSensors.put(deviceId, new ArrayList<>());
                }
                deviceToSensors.get(deviceId).add((PassiveSensor)s);
            }
        }

        for (Actuator a : this.actuators) {
            int deviceId = a.getGatewayDeviceId();
            if (!deviceToActuators.containsKey(deviceId)) {
                deviceToActuators.put(deviceId, new ArrayList<>());
            }
            deviceToActuators.get(deviceId).add(a);
        }
    }

    /**
     * Creates a new placement request for a device with a random application.
     *
     * @param deviceId The device ID to create the PR for
     * @return The newly created placement request
     * @throws NullPointerException if the device has no associated sensors
     */
    public ContextPlacementRequest createNewPlacementRequestForDevice(int deviceId) {
        // First ensure the device mappings are populated
        if (deviceToSensors.isEmpty() && deviceToActuators.isEmpty()) {
            registerDeviceMappings();
        }
        
        // Check if the device has any sensors
        List<PassiveSensor> deviceSensors = deviceToSensors.getOrDefault(deviceId, new ArrayList<>());
        if (deviceSensors.isEmpty()) {
            throw new NullPointerException("Device " + deviceId + " has no associated sensors");
        }
        
        // Get a random application
        Application randomApp = getRandomApplication();
        
        // Update all sensors for this device
        for (PassiveSensor sensor : deviceSensors) {
            sensor.setAppId(randomApp.getAppId());
            sensor.setApp(randomApp);
        }
        
        // Update all actuators for this device
        List<Actuator> deviceActuators = deviceToActuators.getOrDefault(deviceId, new ArrayList<>());
        for (Actuator actuator : deviceActuators) {
            actuator.setAppId(randomApp.getAppId());
            actuator.setApp(randomApp);
        }

        // todo Change if one user device has more than one sensor
        PassiveSensor firstSensor = deviceSensors.get(0);
        Map<String, Integer> placedMicroservicesMap = new LinkedHashMap<>();
        String clientModuleName = FogBroker.getApplicationToFirstServiceMap().get(randomApp);
        placedMicroservicesMap.put(clientModuleName, deviceId);
        
        int sequenceNumber = getNextSequenceNumber(firstSensor.getId());
        String userType = firstSensor.getUserType();
        
        return new ContextPlacementRequest(
            randomApp.getAppId(),
            firstSensor.getId(),
            sequenceNumber,
            deviceId,
            userType,
            placedMicroservicesMap,
            ((SPPFogDevice) CloudSim.getEntity(deviceId)).getUplinkLatency()
        );
    }

    /**
     * Creates a new placement request for a device with a random application.
     *
     * @param deviceId The device ID to create the PR for
     * @return The newly created placement request
     * @throws NullPointerException if the device has no associated sensors
     */
    private void generatePrForDevice(int deviceId) {
        try {
            // Create a new PR with random application using our device-level method
            ContextPlacementRequest newPR = createNewPlacementRequestForDevice(deviceId);
            
            // Check if the user device can host the microservice
            if (userCanFit(deviceId, newPR)) {
                // Send the PR
                JSONObject jsonSend = new JSONObject();
                jsonSend.put("PR", newPR);
                jsonSend.put("app", applications.get(newPR.getApplicationId()));
                sendNow(deviceId, FogEvents.TRANSMIT_PR, jsonSend);
            } else {
                MicroservicePlacementConfig.FAILURE_REASON reason = MicroservicePlacementConfig.FAILURE_REASON.USER_LACKED_RESOURCES;
                Logger.error("PR failed because", reason + " " + CloudSim.getEntityName(deviceId));
                SPPMonitor.getInstance().recordFailedPR(newPR, reason);
            }
        } catch (NullPointerException e) {
            Logger.error("PR Generation Error", e.getMessage());
        }

        SPPFogDevice userDevice = (SPPFogDevice) CloudSim.getEntity(deviceId);
        String userType = userDevice.getDeviceType();
        double delay;

        try {
            // Try to use the user-type specific lambda value
            if (poissonDistribution.hasLambdaForUserType(userType)) {
                delay = poissonDistribution.getNextValue(userType);
            } else {
                throw new NullPointerException("Invalid user type");
            }
        } catch (NullPointerException e) {
            // Handle case where user type is not valid
            System.err.println("Warning: Could not get time interval for user type: " + userType);
            delay = poissonDistribution.getNextValue(); // Use default
        }

        // Schedule the PR generation with the calculated delay
        send(getId(), delay, FogEvents.GENERATE_PR_FOR_DEVICE, userDevice.getId());
    }

    /**
     * Creates a new placement request for a sensor with a random application 
     * from the application pool.
     *
     * @param sensor The sensor to create the PR for
     * @return The newly created placement request
     */
//    public ContextPlacementRequest createNewPlacementRequestWithRandomApp(PassiveSensor sensor) {
//        try {
//            // Use our device-level method instead
//            return createNewPlacementRequestForDevice(sensor.getGatewayDeviceId());
//        } catch (NullPointerException e) {
//            // If there's an issue with the device-level method, fall back to just updating this sensor
//            Application randomApp = getRandomApplication();
//
//            // Update the sensor's application association
//            sensor.setAppId(randomApp.getAppId());
//            sensor.setApp(randomApp);
//
//            Map<String, Integer> placedMicroservicesMap = new LinkedHashMap<>();
//            String clientModuleName = FogBroker.getApplicationToFirstMicroserviceMap().get(randomApp);
//            placedMicroservicesMap.put(
//                clientModuleName,
//                sensor.getGatewayDeviceId()
//            );
//
//            // Create the PR with the next sequence number
//            int sequenceNumber = getNextSequenceNumber(sensor.getId());
//            String userType = sensor.getUserType();
//
//            return new ContextPlacementRequest(
//                randomApp.getAppId(),
//                sensor.getId(),
//                sequenceNumber,
//                sensor.getGatewayDeviceId(),
//                userType,
//                placedMicroservicesMap,
//                requestLatency
//            );
//        }
//    }

    /**
     * Gets the list of user devices registered with this controller
     * 
     * @return List of user devices
     */
    public List<SPPFogDevice> getUserDevices() {
        return userDevices;
    }

    /**
     * Initialize the Poisson distribution with the provided interval values in seconds
     * 
     * @param intervalValues Map of user types to interval values in seconds, or null to use default
     * @param seed Random seed for the distribution
     */
    private void initializePoissonDistributionWithIntervals(Map<String, Integer> intervalValues, long seed) {
        // Default lambda is 1/interval
        double defaultLambda = 1.0 / prGenerationInterval;
        
        // Initialize with default lambda and set a seed for reproducibility
        poissonDistribution = new PoissonDistribution(defaultLambda, seed);
        
        // Convert intervals to lambda values and add them to the distribution
        if (intervalValues != null && !intervalValues.isEmpty()) {
            for (Map.Entry<String, Integer> entry : intervalValues.entrySet()) {
                String userType = entry.getKey();
                Integer interval = entry.getValue();
                
                if (userType != null && interval != null && interval > 0) {
                    // Convert interval to lambda (lambda = 1/interval)
                    double lambda = 1.0 / interval;
                    poissonDistribution.addUserTypeLambda(userType, lambda);
                }
            }
        }
    }

    /**
     * @deprecated This method is kept for backwards compatibility only
     * Use initializePoissonDistributionWithIntervals instead
     */
    @Deprecated
    private void initializePoissonDistribution(Map<String, Double> lambdaValues, long seed) {
        // Convert lambda values to interval values for internal consistency
        Map<String, Integer> intervalValues = new HashMap<>();
        if (lambdaValues != null) {
            for (Map.Entry<String, Double> entry : lambdaValues.entrySet()) {
                if (entry.getValue() > 0) {
                    intervalValues.put(entry.getKey(), (int)(1.0 / entry.getValue()));
                }
            }
        }
        
        // Use the new method with interval values
        initializePoissonDistributionWithIntervals(intervalValues, seed);
    }
}
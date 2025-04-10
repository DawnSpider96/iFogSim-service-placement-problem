package org.fog.placement;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.entities.MyFogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.mobilitydata.References;
import org.fog.utils.FogEvents;
import org.fog.utils.MigrationDelayMonitor;
import org.fog.mobility.DeviceMobilityState;
import org.fog.mobility.Attractor;
import org.fog.mobility.WayPoint;
import org.fog.mobility.WayPointPath;
import org.json.simple.JSONObject;
import org.fog.utils.Logger;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class MyMicroservicesMobilityController extends MyMicroservicesController {

    private Map<Integer, Integer> parentReference;
    
    /**
     * List of "landmark" attractions available in the simulation.
     * For example, these might be special points such as hospitals or city hotspots.
     */
    private List<Attractor> landmarks;

    protected Map<Integer, Map<String, PlacementRequest>> perClientDevicePrs = new HashMap<>();  // clientDevice -> <Application -> PR>

    /**
     * @param name
     * @param fogDevices
     * @param sensors
     * @param applications
     */
    public MyMicroservicesMobilityController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Application> applications, int placementLogic) {
        super(name, fogDevices, sensors, applications, placementLogic);

        setParentReference(new HashMap<Integer, Integer>());
        this.landmarks = new ArrayList<>();

        // Note: super.init() is no longer called automatically
        // It will be called after location data is loaded via completeInitialization()
    }

    public MyMicroservicesMobilityController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Application> applications, int placementLogic, Map<Integer, List<FogDevice>> monitored) {
        super(name, fogDevices, sensors, applications, placementLogic, monitored);

        setParentReference(new HashMap<Integer, Integer>());
        this.landmarks = new ArrayList<>();

        // Note: super.init(monitored) is no longer called automatically
        // It will be called after location data is loaded via completeInitialization(monitored)
    }

    /**
     * Completes the initialization process after location data has been loaded.
     * Overrides parent class method to store parent references after connections are established.
     */
    @Override
    public void completeInitialization() {
        // Call the parent class method to perform basic initialization
        super.completeInitialization();
        
        // Store initial parent references
        for (FogDevice device : fogDevices) {
            parentReference.put(device.getId(), device.getParentId());
        }
    }

    /**
     * Completes the initialization process with monitored devices after location data has been loaded.
     * Overrides parent class method to store parent references after connections are established.
     * 
     * @param monitored map of monitored devices
     */
    @Override
    public void completeInitialization(Map<Integer, List<FogDevice>> monitored) {
        // Call the parent class method to perform basic initialization
        super.completeInitialization(monitored);
        
        // Store initial parent references
        for (FogDevice device : fogDevices) {
            parentReference.put(device.getId(), device.getParentId());
        }
    }

    private void setParentReference(HashMap<Integer, Integer> parentReference) {
        this.parentReference = parentReference;
    }

    @Override
    public void startEntity() {
        super.startEntity();

        // Initialize parent references for existing devices
        initializeParentReferences();
    }

    /**
     * Initializes parent references for all devices
     */
    private void initializeParentReferences() {
        for (FogDevice device : fogDevices) {
            parentReference.put(device.getId(), device.getParentId());
        }
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE:
                scheduleNextMovementUpdate((int) ev.getData());
                break;
            case FogEvents.MAKE_PATH:
                makePath((int) ev.getData());
                break;
            case FogEvents.STOP_SIMULATION:
                printTimeDetails();
                printResourceConsumptionDetails();
                printPowerDetails();
                printCostDetails();
                printNetworkUsageDetails();
                printMigrationDelayDetails();
                endSimulation();
                break;
            default:
                super.processEvent(ev);
                break;
        }
    }

    /**
     * Schedules the next movement update for a given deviceId.
     * 
     * @param deviceId the unique ID of the device whose movement we are updating
     */
    public void scheduleNextMovementUpdate(int deviceId) {
        // Get the device's mobility state
        DeviceMobilityState dms = getDeviceMobilityState(deviceId);
        if (dms == null) {
            System.out.println("Error: No mobility state found for device " + deviceId);
            return;
        }
        
        // Get the next waypoint
        WayPointPath path = dms.getPath();
        WayPoint nextWaypoint = path.getNextWayPoint();
        
        if (nextWaypoint != null) {
            Logger.debug("Values should be equal", "CloudSim timestamp: " + CloudSim.clock() + ", timestamp: " + nextWaypoint.getArrivalTime());
            // Update device location
            dms.setCurrentLocation(nextWaypoint.getLocation());
            System.out.println("Device " + deviceId + " moved to location: " + nextWaypoint.getLocation());
            
            FogDevice device = getFogDeviceById(deviceId);
            FogDevice prevParent = getFogDeviceById(parentReference.get(deviceId));
            
            // Use LocationManager to determine new parent based on proximity
            int newParentId = locationManager.determineParentByProximity(deviceId, fogDevices);
            FogDevice newParent = getFogDeviceById(newParentId);
            
            // If the parent has changed, update parent and routing tables
            if (newParent != null && prevParent.getId() != newParent.getId()) {
                updateDeviceParent(device, newParent, prevParent);
                setNewOrchestratorNode(device, newParent);
                System.out.println("Device " + deviceId + " updated parent to " + newParent.getId());
            }
            
            // Remove the current waypoint as it's been processed
            path.removeNextWayPoint();
            
            // Schedule next movement if there are more waypoints
            if (!path.isEmpty()) {
                WayPoint nextNextWaypoint = path.getNextWayPoint();
                double nextArrivalTime = nextNextWaypoint.getArrivalTime();
                send(getId(), nextArrivalTime - CloudSim.clock(), FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE, deviceId);
                System.out.println("Scheduled next movement for device " + deviceId + " at time " + nextArrivalTime);
            } else {
                // No more waypoints, device reached destination
                dms.reachedDestination();
                System.out.println("Device " + deviceId + " reached destination");
                
                // Schedule pause at destination
                double pauseTime = determinePauseTime(deviceId);
                send(getId(), pauseTime, FogEvents.MAKE_PATH, deviceId);
                System.out.println("Device " + deviceId + " will pause for " + pauseTime + " seconds");
            }
        } else {
            Logger.error("Control Flow Error", "No waypoints found for device " + deviceId);
            makePath(deviceId);
        }
    }

    /**
     * Determines how long a device should pause after reaching its final WayPoint.
     * 
     * @param deviceId the unique ID of the device
     * @return the pause duration (in simulation time units, e.g., seconds)
     */
    public double determinePauseTime(int deviceId) {
        DeviceMobilityState dms = getDeviceMobilityState(deviceId);
        if (dms != null) {
            return dms.determinePauseTime();
        }
        return 0.1; // default
    }

    /**
     * Creates (or re-creates) a path for the given deviceId, effectively telling the device to move.
     * 
     * @param deviceId the unique ID of the device
     */
    public void makePath(int deviceId) {
        DeviceMobilityState dms = getDeviceMobilityState(deviceId);
        if (dms == null) {
            Logger.error("Error","No mobility state found for device " + deviceId);
            return;
        }
        
        // Status change
        dms.startMoving();
        // Create a new attraction point and path
        dms.createAttractionPoint(dms.getCurrentAttractor());
        dms.makePath();
        
        if (!dms.getPath().isEmpty()) {
            WayPoint firstWaypoint = dms.getPath().getNextWayPoint();
            double arrivalTime = firstWaypoint.getArrivalTime();
            
            send(getId(), arrivalTime - CloudSim.clock(), FogEvents.SCHEDULER_NEXT_MOVEMENT_UPDATE, deviceId);
            System.out.println("Created new path for device " + deviceId + ", first movement at " + arrivalTime);
        } else {
            Logger.error("Control Flow Error", "Created empty path for device " + deviceId);
        }
    }

    /**
     * Register a device's mobility state with the controller.
     * This method overrides the parent class implementation to maintain compatibility
     * with existing code.
     * 
     * @param deviceId the device ID
     * @param mobilityState the device's mobility state
     */
    @Override
    public void registerDeviceMobilityState(int deviceId, DeviceMobilityState mobilityState) {
        // Use parent class implementation which updates the shared deviceMobilityStates map
        super.registerDeviceMobilityState(deviceId, mobilityState);
    }
    
    /**
     * Adds a landmark (point of interest) to the simulation
     * 
     * @param landmark the landmark to add
     */
    public void addLandmark(Attractor landmark) {
        landmarks.add(landmark);
    }
    
    /**
     * Gets all landmarks in the simulation
     * 
     * @return list of landmarks
     */
    public List<Attractor> getLandmarks() {
        return landmarks;
    }
    
    /**
     * Starts mobility for a specific device by creating an initial path
     * 
     * @param deviceId the device to start moving
     */
    public void startDeviceMobility(int deviceId) {
        makePath(deviceId);
    }

    // In your entity that decides to stop the simulation
    public void endSimulation() {
        CloudSim.stopSimulation();  // Stops the simulation internally
        CloudSim.clearQueues();  // A hypothetical method to clear static variables if implemented
    }

    private void printMigrationDelayDetails() {
        // TODO Auto-generated method stub
        System.out.println("Total time required for module migration = " + MigrationDelayMonitor.getMigrationDelay());
    }

    @Override
    public void submitPlacementRequests(List<PlacementRequest> placementRequests, int initialDelay) {
        // todo Simon says we ONLY make the first set of PRs here.
        //  The periodic sending will be done by FogDevices when TRANSMIT_PR
        for (PlacementRequest p : placementRequests) {
            placementRequestDelayMap.put(p, initialDelay);

            int clientDeviceId = p.getRequester();
            String app = p.getApplicationId();
            if (perClientDevicePrs.containsKey(clientDeviceId)) {
                perClientDevicePrs.get(clientDeviceId).put(app, p);
            } else {
                Map<String, PlacementRequest> map = new HashMap<>();
                map.put(app, p);
                perClientDevicePrs.put(clientDeviceId, map);
            }
        }
    }

    @Override
    protected void connectWithLatencies() {
        // Use parent's implementation which now uses the LocationManager
        super.connectWithLatencies();
        
        // No need to store parent references here, as it's now done in completeInitialization()
    }

    /**
     * Updates the routing tables for all devices when a device's location changes
     * 
     * @param fogDevice The device that has changed location
     */
    public void updateRoutingTable(FogDevice fogDevice) {
        for (FogDevice f : fogDevices) {
            if (f.getId() != fogDevice.getId()) {
                // NO communication overhead.
                // Simon (090425) says MicroservicesController is an entity EXTERNAL to the network.
                ((MyFogDevice) fogDevice).updateRoutingTable(f.getId(), fogDevice.getParentId());

                ////for other update route to mobile based on route to parent
                int nextId = ((MyFogDevice) f).getRoutingTable().get(fogDevice.getParentId());
                if (f.getId() != nextId)
                    ((MyFogDevice) f).updateRoutingTable(fogDevice.getId(), nextId);
                else
                    ((MyFogDevice) f).updateRoutingTable(fogDevice.getId(), fogDevice.getId());
            }
        }
    }

    /**
     * Updates the orchestrator node for a device
     * 
     * @param fogDevice The device to update
     * @param newParent The new parent device
     */
    public void setNewOrchestratorNode(FogDevice fogDevice, FogDevice newParent) {
        int parentId = newParent.getId();
        while(parentId!=-1){
            if(((MyFogDevice)newParent).getDeviceType().equals(MyFogDevice.FON) ||
                    ((MyFogDevice)newParent).getDeviceType().equals(MyFogDevice.CLOUD)){
                int currentFon = ((MyFogDevice)fogDevice).getFonId();
                if(currentFon!=parentId) {
                    ((MyFogDevice)getFogDeviceById(currentFon)).removeMonitoredDevice(fogDevice);
                    ((MyFogDevice) fogDevice).setFonID(parentId);
                    ((MyFogDevice)getFogDeviceById(parentId)).addMonitoredDevice(fogDevice);
                    System.out.println("Orchestrator Node for device : " + fogDevice.getId() + " updated to " + parentId);
                }
                break;
            }
            else{
                parentId =newParent.getParentId();
                if(parentId!=-1)
                    newParent = getFogDeviceById(parentId);
            }
        }
    }

    /**
     * Updates a device's parent and handles the necessary connection updates
     * 
     * @param fogDevice The device to update
     * @param newParent The new parent device
     * @param prevParent The previous parent device
     */
    public void updateDeviceParent(FogDevice fogDevice, FogDevice newParent, FogDevice prevParent) {
        fogDevice.setParentId(newParent.getId());
        System.out.println("Child " + fogDevice.getName() + "\t----->\tParent " + newParent.getName());
        
        // Calculate latency based on distance using the LocationManager
        double latency = locationManager.calculateNetworkLatency(fogDevice.getId(), newParent.getId());
        fogDevice.setUplinkLatency(latency);
        
        newParent.getChildToLatencyMap().put(fogDevice.getId(), latency);
        newParent.addChild(fogDevice.getId());
        prevParent.removeChild(fogDevice.getId());
        
        // Update parent reference
        parentReference.put(fogDevice.getId(), newParent.getId());
        
        // Update routing tables
        updateRoutingTable(fogDevice);
    }
}

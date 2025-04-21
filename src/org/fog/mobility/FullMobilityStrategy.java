package org.fog.mobility;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MyFogDevice;
import org.fog.mobilitydata.Location;
import org.fog.placement.LocationManager;
import org.fog.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete implementation of MobilityStrategy for handling device mobility.
 * This class contains all the functionality previously in MyMicroservicesMobilityController.
 */
public class FullMobilityStrategy implements MobilityStrategy {
    
    private List<FogDevice> fogDevices;
    private Map<Integer, Integer> parentReferences = new HashMap<>();
    private List<Attractor> landmarks = new ArrayList<>();
    
    @Override
    public void initialize(List<FogDevice> fogDevices, Map<Integer, Integer> initialParentReferences) {
        this.fogDevices = fogDevices;
        
        // Deep copy of parent references
        this.parentReferences.putAll(initialParentReferences);
    }
    
    @Override
    public double handleMovementUpdate(int deviceId, DeviceMobilityState dms, LocationManager locationManager) {
        if (dms == null) {
            Logger.error("Mobility Error", "No mobility state found for device " + deviceId);
            return -1.0;
        }
        
        // Get the next waypoint
        WayPointPath path = dms.getPath();
        WayPoint nextWaypoint = path.getNextWayPoint();
        
        if (nextWaypoint == null) {
            throw new NullPointerException("CRITICAL ERROR: Empty WayPointPath");
//            makePath(deviceId, dms);
//            return -1.0;
        }
        double currentTime = CloudSim.clock();
        Logger.debug("Values should be roughly equal", "CloudSim timestamp: " + currentTime +
                    ", timestamp: " + nextWaypoint.getArrivalTime());
        Location loc = nextWaypoint.getLocation();
        dms.setCurrentLocation(loc);
        // TODO Simon (100425) says maybe remove the journey state if it gets too heavy,
        //  it's primarily for debugging.
        dms.getJourney().put(currentTime, loc);
        System.out.println("Device " + deviceId + " moved to location: " + nextWaypoint.getLocation());
        
        FogDevice device = getDeviceById(deviceId);
        FogDevice prevParent = getDeviceById(parentReferences.get(deviceId));
        
        // Use LocationManager to determine new parent based on proximity
        // IRL a device wouldn't have full knowledge of the other devices (fogDevices state)
        //  but we don't have a good representation of physically connecting to the nearest edge server.
        int newParentId = locationManager.determineParentByProximity(deviceId, fogDevices);
        FogDevice newParent = getDeviceById(newParentId);
        
        // If the parent has changed, update parent and routing tables
        if (newParent != null && prevParent.getId() != newParent.getId()) {
            updateDeviceParent(device, newParent, prevParent, locationManager);
            setNewOrchestratorNode(device, newParent);
//            System.out.println("Device " + CloudSim.getEntityName(deviceId) + " updated parent to " + CloudSim.getEntityName(newParent.getId()));
        }
        
        // Remove the current waypoint as it's been processed
        path.removeNextWayPoint();
        
        // Schedule next movement if there are more waypoints
        if (!path.isEmpty()) {
            WayPoint nextNextWaypoint = path.getNextWayPoint();
            double nextArrivalTime = nextNextWaypoint.getArrivalTime();
            double delay = nextArrivalTime - CloudSim.clock();
            System.out.println("Scheduled next movement for device " + CloudSim.getEntityName(deviceId) + " at time " + nextArrivalTime);
            return delay;
        } else {
            // No more waypoints, device reached destination
            System.out.println(CloudSim.getEntityName(deviceId) + " reached destination");
            dms.reachedDestination();
            
            // Calculate pause time
            double pauseTime = determinePauseTime(deviceId, dms);
            System.out.println("Device " + CloudSim.getEntityName(deviceId) + " will pause for " + pauseTime + " seconds");
            return pauseTime;
        }
    }
    
    @Override
    public double makePath(int deviceId, DeviceMobilityState dms) {
        if (dms == null) {
            throw new NullPointerException("CRITICAL ERROR: Device mobility state not found for device " + deviceId);
        }
        
        // Status change
        dms.startMoving();
        // Create a new attraction point and path
        dms.updateAttractionPoint(dms.getCurrentAttractor());
        dms.makePath();
        
        if (!dms.getPath().isEmpty()) {
            WayPoint firstWaypoint = dms.getPath().getNextWayPoint();
            double arrivalTime = firstWaypoint.getArrivalTime();
            double delay = arrivalTime - CloudSim.clock();

            if (delay < 0) {
                throw new NullPointerException("CRITICAL ERROR: Negative delay.");
            }
                        
            System.out.println("Created new path for device " + deviceId + ", first movement at " + arrivalTime);
            return delay;
        } else {
            throw new NullPointerException("CRITICAL ERROR: Created Empty Path for device " + deviceId);
//            return -1.0;
        }
    }
    
    @Override
    public double startDeviceMobility(int deviceId, DeviceMobilityState mobilityState) {
        // Return delay (until next waypoint) for controller to send next scheduled movement event
        return makePath(deviceId, mobilityState);
    }
    
    @Override
    public double determinePauseTime(int deviceId, DeviceMobilityState dms) {
        if (dms != null) {
            return dms.determinePauseTime();
        }
        return 0.1; // default
    }
    
    @Override
    public void updateDeviceParent(FogDevice fogDevice, FogDevice newParent, FogDevice prevParent, LocationManager locationManager) {
        fogDevice.setParentId(newParent.getId());
        System.out.println("Child " + fogDevice.getName() + " changed from Parent " + prevParent.getName() + " to " + newParent.getName());
        
        // Calculate latency based on distance using the LocationManager
        double latency = locationManager.calculateNetworkLatency(fogDevice.getId(), newParent.getId());
        fogDevice.setUplinkLatency(latency);
        
        newParent.getChildToLatencyMap().put(fogDevice.getId(), latency);
        newParent.addChild(fogDevice.getId());
        prevParent.removeChild(fogDevice.getId());
        
        // Update parent reference
        parentReferences.put(fogDevice.getId(), newParent.getId());
        
        // Update routing tables
        updateRoutingTable(fogDevice);
    }
    
    @Override
    public void addLandmark(Attractor landmark) {
        landmarks.add(landmark);
    }
    
    @Override
    public List<Attractor> getLandmarks() {
        return landmarks;
    }
    
    @Override
    public void updateRoutingTable(FogDevice fogDevice) {
        // TODO Currently NO communication overhead between fog devices and controller.
        //  Is ok in general, but not ok for THIS functionality.
        //  Because irl there will have to be some communication, between each other (routing protocol)
        //  OR an external entity, before routing tables can be updated.
        for (FogDevice f : fogDevices) {
            if (f.getId() != fogDevice.getId()) {

                ((MyFogDevice) fogDevice).updateRoutingTable(f.getId(), fogDevice.getParentId());

                //For othe((MyFogDevice) f).getRoutingTable()r, update route to mobile based on route to parent
                int nextId = ((MyFogDevice) f).getRoutingTable().get(fogDevice.getParentId());
                if (f.getId() != nextId)
                    ((MyFogDevice) f).updateRoutingTable(fogDevice.getId(), nextId);
                else
                    ((MyFogDevice) f).updateRoutingTable(fogDevice.getId(), fogDevice.getId());
            }
        }
    }
    
    @Override
    public void setNewOrchestratorNode(FogDevice fogDevice, FogDevice newParent) {
        int parentId = newParent.getId();
        while (parentId != -1) {
            if (((MyFogDevice)newParent).getDeviceType().equals(MyFogDevice.FON) ||
                    ((MyFogDevice)newParent).getDeviceType().equals(MyFogDevice.CLOUD)) {
                int currentFon = ((MyFogDevice)fogDevice).getFonId();
                if (currentFon != parentId) {
                    ((MyFogDevice)getDeviceById(currentFon)).removeMonitoredDevice(fogDevice);
                    ((MyFogDevice) fogDevice).setFonID(parentId);
                    ((MyFogDevice)getDeviceById(parentId)).addMonitoredDevice(fogDevice);
                    System.out.println("Orchestrator Node for device : " + fogDevice.getId() + " updated to " + parentId);
                }
                break;
            } else {
                parentId = newParent.getParentId();
                if (parentId != -1)
                    newParent = getDeviceById(parentId);
            }
        }
    }
    
    @Override
    public Map<Integer, Integer> getParentReferences() {
        return parentReferences;
    }

    // With this function, we assume that MobilityController has access to ALL devices.
    private FogDevice getDeviceById(int id) {
        for (FogDevice device : fogDevices) {
            if (device.getId() == id) {
                return device;
            }
        }
        return null;
    }
}
package org.fog.placement;

import org.fog.entities.FogDevice;
import org.fog.mobility.DeviceMobilityState;
import org.fog.mobilitydata.Location;
import org.fog.utils.Config;


import java.util.*;

/**
 * The LocationManager class manages device locations and provides proximity-based
 * parent determination functionality. It replaces parts of the original LocationHandler
 * but is designed to work with the existing DeviceMobilityState system.
 */
public class LocationManager {
    private Map<Integer, Location> resourceLocations = new HashMap<>();
    private Map<Integer, DeviceMobilityState> deviceMobilityStates;
    private Map<String, Integer> levelID;
    private Map<Integer, ArrayList<String>> levelwiseResources;
    private Map<Integer, String> deviceToDataId = new HashMap<>();
    private Map<String, Integer> resourceToLevel = new HashMap<>();

    
    /**
     * Creates a new LocationManager
     * 
     * @param levelID mapping of level names to level IDs
     * @param levelwiseResources mapping of level IDs to resources at that level
     * @param deviceMobilityStates mapping of device IDs to their mobility states
     */
    public LocationManager(
            Map<String, Integer> levelID,
            Map<Integer, ArrayList<String>> levelwiseResources,
            Map<Integer, DeviceMobilityState> deviceMobilityStates) {
        this.levelID = levelID;
        this.levelwiseResources = levelwiseResources;
        this.deviceMobilityStates = deviceMobilityStates;
    }
    
    /**
     * Registers a static resource's location
     * 
     * @param deviceId the device ID
     * @param location the location
     * @param dataId the data ID
     * @param level the level
     */
    public void registerResourceLocation(int deviceId, Location location, String dataId, int level) {
        resourceLocations.put(deviceId, location);
        deviceToDataId.put(deviceId, dataId);
        resourceToLevel.put(dataId, level);
    }
    
    /**
     * Gets a device's location (user or resource)
     * 
     * @param deviceId the device ID
     * @return the device's location, or null if not found
     */
    public Location getDeviceLocation(int deviceId) {
        // Check if user device
        if (deviceMobilityStates.containsKey(deviceId)) {
            return deviceMobilityStates.get(deviceId).getCurrentLocation();
        }
        
        // Else it is fog node
        return resourceLocations.get(deviceId);
    }
    
    /**
     * Calculates distance between two devices
     * 
     * @param device1
     * @param device2
     * @return distance in kilometers
     */
    public double calculateDistance(int device1, int device2) {
        Location loc1 = getDeviceLocation(device1);
        Location loc2 = getDeviceLocation(device2);
        
        if (loc1 == null || loc2 == null) {
            throw new IllegalArgumentException("Location not found for one of the devices");
        }
        
        return loc1.calculateDistance(loc2);
    }
    
    /**
     * Determines the parent for a device based on proximity
     * 
     * @param deviceId the device ID
     * @param fogDevices list of all fog devices
     * @return the ID of the closest parent device, or -1 if none found
     */
    public int determineParentByProximity(int deviceId, List<FogDevice> fogDevices) {
        Location deviceLocation = getDeviceLocation(deviceId);
        if (deviceLocation == null) {
            throw new NullPointerException("No location found for device");
        }
        
        // Determine the device's level
        int deviceLevel;
        if (deviceMobilityStates.containsKey(deviceId)) {
            deviceLevel = levelID.get("User"); // It's a user device
        } else {
            String dataId = deviceToDataId.get(deviceId);
            if (dataId == null || !resourceToLevel.containsKey(dataId)) {
                System.out.println("WARNING: Could not determine level for device " + deviceId);
                return -1;
            }
            deviceLevel = resourceToLevel.get(dataId);
        }
        
        // Parent should be one level up
        int parentLevel = deviceLevel - 1;
        if (parentLevel < 0) {
            System.out.println("WARNING: Device " + deviceId + " is already at the top level");
            return -1;
        } // Population of parent level is not very thorough
        
        // Find the closest fog device at parent level
        double minDistance = Double.MAX_VALUE;
        int closestParentId = -1;
        
        for (FogDevice device : fogDevices) {
            if (device.getLevel() == parentLevel) {
                Location parentLocation = getDeviceLocation(device.getId());
                
                if (parentLocation != null) {
                    double distance = deviceLocation.calculateDistance(parentLocation);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestParentId = device.getId();
                    }
                }
            }
        }
        
        return closestParentId;
    }
    
    /**
     * Calculate network latency between two devices based on distance
     * 
     * @param device1 first device ID
     * @param device2 second device ID
     * @return latency in seconds
     */
    public double calculateDirectLatency(int device1, int device2) {
        boolean isUserDevice1 = deviceMobilityStates.containsKey(device1);
        boolean isUserDevice2 = deviceMobilityStates.containsKey(device2);
        
        double distance = calculateDistance(device1, device2);
        
        // If either device is a user device, use wifi latency model
        // NOTE: The fog node WILL be the parent of the user device.
        // We CANNOT use this function to calculate the latency between any user and any node.
        if (isUserDevice1 || isUserDevice2) {
            return Config.baseWifiLatency + (distance * Config.wifiLatencyPerKilometer);
        } else {
            // Otherwise use server latency model. Cloud-Node link.
            return Config.baseServerLatency + (distance * Config.serverLatencyPerKilometer);
        }
    }
    
    /**
     * Check if a device is a mobile user device
     * 
     * @param deviceId the device ID
     * @return true if the device is a mobile user device
     */
    public boolean isUserDevice(int deviceId) {
        return deviceMobilityStates.containsKey(deviceId);
    }
    
    /**
     * Checks if a device is at the cloud level
     * 
     * @param deviceId the device ID
     * @return true if the device is at the cloud level
     */
    public boolean isCloud(int deviceId) {
        String dataId = deviceToDataId.get(deviceId);
        if (dataId != null && resourceToLevel.containsKey(dataId)) {
            return resourceToLevel.get(dataId) == levelID.get("Cloud");
        }
        return false;
    }
}
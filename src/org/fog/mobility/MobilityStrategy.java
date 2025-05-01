package org.fog.mobility;

import org.fog.entities.FogDevice;
import org.fog.placement.LocationManager;

import java.util.List;
import java.util.Map;

/**
 * Interface defining strategy for handling device mobility in the fog network.
 * Implementations of this interface determine how device movement is handled.
 */
public interface MobilityStrategy {
    /**
     * Initialize the mobility strategy with necessary components
     * 
     * @param fogDevices List of all fog devices in the simulation
     * @param initialParentReferences Initial parent-child relationships
     */
    void initialize(List<FogDevice> fogDevices, Map<Integer, Integer> initialParentReferences);
    
    /**
     * Handles device movement updates
     * 
     * @param deviceId The ID of the device being updated
     * @param mobilityState The device's mobility state
     * @param locationManager The location manager for the simulation
     * @return The time until the next scheduled movement (if any)
     */
    double handleMovementUpdate(int deviceId, DeviceMobilityState mobilityState, LocationManager locationManager);
    
    /**
     * Creates a new path for a device
     * 
     * @param deviceId The ID of the device
     * @param mobilityState The device's mobility state
     * @return The time until the first movement on the new path
     */
    double makePath(int deviceId, DeviceMobilityState mobilityState);
    
    /**
     * Starts mobility for a device
     * 
     * @param deviceId The ID of the device
     * @param mobilityState The device's mobility state
     * @return The time delay until the first movement, or -1 if no path was created
     */
    double startDeviceMobility(int deviceId, DeviceMobilityState mobilityState);
    
    /**
     * Determines pause time at a destination
     * 
     * @param deviceId The ID of the device
     * @param mobilityState The device's mobility state
     * @return The pause time in simulation time units
     */
    double determinePauseTime(int deviceId, DeviceMobilityState mobilityState);
    
    /**
     * Updates device parent and connections when location changes
     * 
     * @param fogDevice The device that's moving
     * @param newParent The new parent device
     * @param prevParent The previous parent device
     * @param locationManager The location manager
     */
    void updateDeviceParent(FogDevice fogDevice, FogDevice newParent, FogDevice prevParent, LocationManager locationManager);
    
    /**
     * Adds a landmark (point of interest) to the simulation
     * 
     * @param landmark The landmark to add
     */
    void addLandmark(Attractor landmark);
    
    /**
     * Gets all landmarks in the simulation
     * 
     * @return List of landmarks
     */
    List<Attractor> getLandmarks();
    
    /**
     * Updates the routing tables when a device's parent changes
     * 
     * @param fogDevice The device whose parent has changed
     */
    void updateRoutingTable(FogDevice fogDevice);
    
    /**
     * Updates the orchestrator node for a device
     * 
     * @param fogDevice The device to update
     * @param newParent The new parent device
     */
    void setNewOrchestratorNode(FogDevice fogDevice, FogDevice newParent);
    
    /**
     * Gets the parent references map
     * 
     * @return Map of device IDs to parent IDs
     */
    Map<Integer, Integer> getParentReferences();
} 
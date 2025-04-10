package org.fog.mobility;

import org.cloudbus.cloudsim.core.SimEntity;
import org.fog.entities.FogDevice;
import org.fog.placement.LocationManager;
import org.fog.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * No-op implementation of MobilityStrategy for when mobility is not needed.
 * Methods log errors when called, as they shouldn't be invoked in a non-mobile simulation.
 */
public class NoMobilityStrategy implements MobilityStrategy {
    
    private SimEntity controller;
    private List<FogDevice> fogDevices;
    private Map<Integer, Integer> parentReferences = new HashMap<>();
    private List<Attractor> landmarks = new ArrayList<>();
    
    @Override
    public void initialize(List<FogDevice> fogDevices, Map<Integer, Integer> initialParentReferences) {
        this.fogDevices = fogDevices;
        this.parentReferences = initialParentReferences;
    }
    
    @Override
    public double handleMovementUpdate(int deviceId, DeviceMobilityState mobilityState, LocationManager locationManager) {
        Logger.error("Mobility Error", "Received movement update for device " + deviceId + " but mobility is not enabled");
        return -1.0;
    }
    
    @Override
    public double makePath(int deviceId, DeviceMobilityState mobilityState) {
        Logger.error("Mobility Error", "Attempted to make path for device " + deviceId + " but mobility is not enabled");
        return -1.0;
    }
    
    @Override
    public double startDeviceMobility(int deviceId, DeviceMobilityState mobilityState) {
        Logger.error("Mobility Error", "Attempted to start mobility for device " + deviceId + " but mobility is not enabled");
        return -1.0; // Return negative value to indicate error/failure
    }
    
    @Override
    public double determinePauseTime(int deviceId, DeviceMobilityState mobilityState) {
        Logger.error("Mobility Error", "Attempted to determine pause time for device " + deviceId + " but mobility is not enabled");
        return 0.0;
    }
    
    @Override
    public void updateDeviceParent(FogDevice fogDevice, FogDevice newParent, FogDevice prevParent, LocationManager locationManager) {
        Logger.error("Mobility Error", "Attempted to update device parent for device " + fogDevice.getId() + " but mobility is not enabled");
    }
    
    @Override
    public void addLandmark(Attractor landmark) {
        Logger.error("Mobility Error", "Attempted to add landmark but mobility is not enabled");
    }
    
    @Override
    public List<Attractor> getLandmarks() {
        return new ArrayList<>(); // Return empty list
    }
    
    @Override
    public void updateRoutingTable(FogDevice fogDevice) {
        Logger.error("Mobility Error", "Attempted to update routing table for device " + fogDevice.getId() + " but mobility is not enabled");
    }
    
    @Override
    public void setNewOrchestratorNode(FogDevice fogDevice, FogDevice newParent) {
        Logger.error("Mobility Error", "Attempted to set new orchestrator node for device " + fogDevice.getId() + " but mobility is not enabled");
    }
    
    @Override
    public Map<Integer, Integer> getParentReferences() {
        return parentReferences;
    }
} 
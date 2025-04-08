package org.fog.entities;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.utils.FogEvents;
import org.fog.utils.Logger;

import java.util.List;

/**
 * Abstract base class for all user devices in the fog network.
 * This class encapsulates common behavior and properties shared by all user devices.
 */
public abstract class UserDevice extends MyFogDevice {
    
    public UserDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, 
                     List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, 
                     double downlinkBandwidth, double clusterLinkBandwidth, double uplinkLatency, 
                     double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, 
              uplinkBandwidth, downlinkBandwidth, clusterLinkBandwidth, uplinkLatency, 
              ratePerMips, "user"); // Base type for all user devices
    }

    /**
     * Abstract method to be implemented by specific user device types.
     * This defines how each type of user device should move.
     */
    protected abstract void updateMovement();

    /**
     * Override the processOtherEvent method to handle user-specific events
     */
    @Override
    protected void processOtherEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.UPDATE_MOVEMENT:
                updateMovement();
                break;
            default:
                super.processOtherEvent(ev);
                break;
        }
    }

    /**
     * Override to ensure proper initialization for user devices
     */
    @Override
    public void initializeController(LoadBalancer loadBalancer) {
        if (getDeviceType().equals("user")) {
            super.initializeController(loadBalancer);
        } else {
            Logger.error("Controller init failed", "User controller initialized for non-user device " + getName());
        }
    }
} 
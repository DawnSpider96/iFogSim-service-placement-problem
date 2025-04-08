package org.fog.entities;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.fog.utils.FogUtils;

import java.util.List;
import java.util.Random;

/**
 * Represents a generic user device with random movement patterns.
 * This device moves without any specific objective or pattern.
 */
public class GenericUserDevice extends UserDevice {
    
    private static final Random random = new Random();
    
    public GenericUserDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, 
                           List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, 
                           double downlinkBandwidth, double clusterLinkBandwidth, double uplinkLatency, 
                           double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, 
              uplinkBandwidth, downlinkBandwidth, clusterLinkBandwidth, uplinkLatency, 
              ratePerMips);
    }

    /**
     * Implements random movement behavior for generic users.
     * The device moves to random positions within the simulation area.
     */
    @Override
    protected void updateMovement() {
        // Generate random coordinates within the simulation area
        double newX = random.nextDouble() * FogUtils.MAX_X;
        double newY = random.nextDouble() * FogUtils.MAX_Y;
        
        // Update the device's position
        setX(newX);
        setY(newY);
    }
} 
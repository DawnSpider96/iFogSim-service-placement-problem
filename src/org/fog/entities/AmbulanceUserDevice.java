package org.fog.entities;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.fog.utils.FogUtils;

import java.util.List;
import java.util.Random;

/**
 * Represents an ambulance user device with movement patterns specific to emergency response.
 * This device moves with purpose, simulating an ambulance responding to emergencies.
 */
public class AmbulanceUserDevice extends UserDevice {
    
    private static final Random random = new Random();
    private double targetX;
    private double targetY;
    private static final double MOVEMENT_SPEED = 50.0; // Units per time step
    
    public AmbulanceUserDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, 
                             List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, 
                             double downlinkBandwidth, double clusterLinkBandwidth, double uplinkLatency, 
                             double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, 
              uplinkBandwidth, downlinkBandwidth, clusterLinkBandwidth, uplinkLatency, 
              ratePerMips);
        
        // Initialize with random target
        setNewTarget();
    }

    /**
     * Implements ambulance-specific movement behavior.
     * The device moves towards a target location, simulating response to emergencies.
     */
    @Override
    protected void updateMovement() {
        // Calculate direction to target
        double dx = targetX - getX();
        double dy = targetY - getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance < MOVEMENT_SPEED) {
            // Reached target, set new target
            setNewTarget();
        } else {
            // Move towards target
            double moveX = (dx / distance) * MOVEMENT_SPEED;
            double moveY = (dy / distance) * MOVEMENT_SPEED;
            
            setX(getX() + moveX);
            setY(getY() + moveY);
        }
    }
    
    /**
     * Sets a new random target location for the ambulance to move towards.
     */
    private void setNewTarget() {
        targetX = random.nextDouble() * FogUtils.MAX_X;
        targetY = random.nextDouble() * FogUtils.MAX_Y;
    }
} 
package org.fog.entities;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.fog.utils.FogUtils;

import java.util.List;
import java.util.Random;

/**
 * Represents an opera user device with movement patterns specific to opera performance locations.
 * This device moves between predefined opera venues in a more structured manner.
 */
public class OperaUserDevice extends UserDevice {
    
    private static final Random random = new Random();
    private static final double MOVEMENT_SPEED = 30.0; // Units per time step
    private static final int NUM_VENUES = 5;
    
    private double[] venueX;
    private double[] venueY;
    private int currentVenueIndex;
    private double targetX;
    private double targetY;
    
    public OperaUserDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, 
                         List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, 
                         double downlinkBandwidth, double clusterLinkBandwidth, double uplinkLatency, 
                         double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, 
              uplinkBandwidth, downlinkBandwidth, clusterLinkBandwidth, uplinkLatency, 
              ratePerMips);
        
        // Initialize opera venues
        initializeVenues();
        currentVenueIndex = 0;
        targetX = venueX[currentVenueIndex];
        targetY = venueY[currentVenueIndex];
    }

    /**
     * Implements opera-specific movement behavior.
     * The device moves between predefined opera venues in a more structured manner.
     */
    @Override
    protected void updateMovement() {
        // Calculate direction to target
        double dx = targetX - getX();
        double dy = targetY - getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance < MOVEMENT_SPEED) {
            // Reached current venue, move to next venue
            currentVenueIndex = (currentVenueIndex + 1) % NUM_VENUES;
            targetX = venueX[currentVenueIndex];
            targetY = venueY[currentVenueIndex];
        } else {
            // Move towards target
            double moveX = (dx / distance) * MOVEMENT_SPEED;
            double moveY = (dy / distance) * MOVEMENT_SPEED;
            
            setX(getX() + moveX);
            setY(getY() + moveY);
        }
    }
    
    /**
     * Initializes the opera venues with predefined locations.
     */
    private void initializeVenues() {
        venueX = new double[NUM_VENUES];
        venueY = new double[NUM_VENUES];
        
        // Create a circular pattern of venues
        for (int i = 0; i < NUM_VENUES; i++) {
            double angle = (2 * Math.PI * i) / NUM_VENUES;
            venueX[i] = FogUtils.MAX_X/2 + (FogUtils.MAX_X/4) * Math.cos(angle);
            venueY[i] = FogUtils.MAX_Y/2 + (FogUtils.MAX_Y/4) * Math.sin(angle);
        }
    }
} 
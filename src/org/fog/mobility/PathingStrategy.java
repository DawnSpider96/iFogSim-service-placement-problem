package org.fog.mobility;

import org.fog.mobilitydata.Location;

/**
 * Given the current location, speed, and an attraction point, this strategy builds 
 * a new WaypointPath that the device should follow to reach that point.
 */
public interface PathingStrategy {

    /**
     * Builds and returns a WaypointPath from the current location to the attraction point.
     * 
     * @param attractionPoint the final destination or point of interest
     * @param speed           the device's travel speed
     * @param currentLocation the starting location
     * @return a WaypointPath from currentLocation to attractionPoint
     */
    WayPointPath makePath(Attractor attractionPoint, double speed, Location currentLocation);
    
    /**
     * Sets the random seed used by this pathing strategy.
     * 
     * @param seed the random seed to use
     */
    void setSeed(long seed);
    
    /**
     * Gets the current random seed used by this pathing strategy.
     * 
     * @return the current random seed
     */
    long getSeed();
} 
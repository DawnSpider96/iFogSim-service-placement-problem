package org.fog.mobility;

import org.fog.mobilitydata.Location;

/**
 * Represents a single step in the device's route.
 * Provides the location and the time at which the device will arrive there.
 */
public class WayPoint {

    /**
     * The geographic location of this waypoint.
     */
    private Location location;

    /**
     * The simulation time at which the device arrives at this waypoint.
     */
    private double arrivalTime;
    
    /**
     * Constructor for creating a waypoint with location and arrival time
     * 
     * @param location the geographic location
     * @param arrivalTime the time at which the device will arrive
     */
    public WayPoint(Location location, double arrivalTime) {
        this.location = location;
        this.arrivalTime = arrivalTime;
    }
    
    /**
     * Gets the location of this waypoint
     * 
     * @return the location
     */
    public Location getLocation() {
        return location;
    }
    
    /**
     * Sets the location of this waypoint
     * 
     * @param location the location to set
     */
    public void setLocation(Location location) {
        this.location = location;
    }
    
    /**
     * Gets the arrival time for this waypoint
     * 
     * @return the arrival time
     */
    public double getArrivalTime() {
        return arrivalTime;
    }
    
    /**
     * Sets the arrival time for this waypoint
     * 
     * @param arrivalTime the arrival time to set
     */
    public void setArrivalTime(double arrivalTime) {
        this.arrivalTime = arrivalTime;
    }
    
    @Override
    public String toString() {
        return "WayPoint[location=" + location + ", arrivalTime=" + arrivalTime + "]";
    }
} 
package org.fog.mobility;

import org.fog.mobilitydata.Location;

/**
 * Holds the device's current location and path.
 * Tracks the device's mobility strategy and pause strategy.
 * Handles transitions in the device's mobility status.
 * Provides methods to generate a new path and to handle "arrival" events.
 */
public abstract class DeviceMobilityState {

    /**
     * The device's current geographic location.
     * This is updated each time we reach the next WayPoint.
     */
    protected Location currentLocation;

    /**
     * A structure that holds the upcoming waypoints for the device.
     * This could be a queue (FIFO) so that we can pop the next WayPoint easily.
     */
    protected WayPointPath path;

    /**
     * The mobility strategy used to generate paths from the current location to an attraction point.
     */
    protected MobilityStrategy strategy;

    /**
     * The current point of attraction or destination.
     * Potentially used for determining pause time or generating the next path.
     */
    protected Attractor currentAttractor;

    /**
     * The walking/driving speed of this device (units: e.g., meters/second or km/h).
     */
    protected double speed;

    /**
     * An enum representing the device's current status.
     * Different subclasses can define different sets of statuses,
     * but all might share at least a PAUSED state.
     */
    protected Enum<?> status;
    
    /**
     * Creates a new device mobility state
     * 
     * @param location initial location
     * @param strategy mobility strategy to use
     * @param speed travel speed
     */
    public DeviceMobilityState(Location location, MobilityStrategy strategy, double speed) {
        this.currentLocation = location;
        this.strategy = strategy;
        this.speed = speed;
        this.path = new WayPointPath();
        this.currentAttractor = null;
    }
    
    /**
     * Gets the current location of the device
     * 
     * @return the current location
     */
    public Location getCurrentLocation() {
        return currentLocation;
    }

    public Attractor getCurrentAttractor() {
        return currentAttractor;
    }
    
    /**
     * Updates the current location of the device
     * 
     * @param location the new location
     */
    public void setCurrentLocation(Location location) {
        this.currentLocation = location;
    }
    
    /**
     * Gets the current path the device is following
     * 
     * @return the waypoint path
     */
    public WayPointPath getPath() {
        return path;
    }
    
    /**
     * Sets a new path for the device to follow
     * 
     * @param path the new path
     */
    public void setPath(WayPointPath path) {
        this.path = path;
    }
    
    /**
     * Gets the device's travel speed
     * 
     * @return the speed
     */
    public double getSpeed() {
        return speed;
    }
    
    /**
     * Sets the device's travel speed
     * 
     * @param speed the new speed
     */
    public void setSpeed(double speed) {
        this.speed = speed;
    }
    
    /**
     * Gets the current status of the device
     * 
     * @return the status enum
     */
    public Enum<?> getStatus() {
        return status;
    }

    /**
     * Initiates movement for the device, changing the status from PAUSED (or WAITING) 
     * to a traveling/active state. 
     * Subclasses should override to enforce domain-specific status transitions.
     */
    public abstract void startMoving();

    /**
     * Called when the device has consumed its final WayPoint and reached the destination.
     * Each subclass will have different logic for how the status is updated.
     */
    public abstract void reachedDestination();

    /**
     * Generates a path from the current location to the device's attraction point.
     * In turn, it calls the assigned MobilityStrategy to build the path.
     */
    public void makePath() {
        if (currentAttractor != null && strategy != null) {
            path = strategy.makePath(currentAttractor, speed, currentLocation);
        }
    }

    /**
     * Creates or updates the IAttract object that represents the new destination or point of interest.
     * The default (or random) strategy may create a brand new IAttract each time.
     * Subclasses may override to consider current status.
     * Updates the attractionPoint field.
     */
    public abstract void createAttractionPoint(Attractor currentAttractionPoint);

    /**
     * Determines how long the device should pause after reaching its final WayPoint.
     * Delegates to the attractionPoint's PauseTimeStrategy if present.
     * 
     * @return the pause time in simulation units
     */
    public double determinePauseTime() {
        if (currentAttractor != null) {
            return currentAttractor.determinePauseTime();
        }
        return 0.1; // default placeholder
    }
} 
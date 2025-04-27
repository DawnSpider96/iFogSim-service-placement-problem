package org.fog.mobility;

import org.fog.mobilitydata.Location;

/**
 * A pathing strategy that does nothing - creates empty paths with no waypoints.
 * Used for immobile users or when mobility is disabled to avoid unnecessary
 * computational work like GraphHopper route calculations.
 */
public class LazyBugPathingStrategy extends AbstractPathingStrategy {
    
    /**
     * Creates a new LazyBugPathingStrategy with the specified random seed
     * 
     * @param seed the random seed to use
     */
    public LazyBugPathingStrategy(long seed) {
        super(seed);
    }
    
    /**
     * Creates a new LazyBugPathingStrategy with the current time as seed
     */
    public LazyBugPathingStrategy() {
        super();
    }
    
    /**
     * Returns an empty path with no waypoints, effectively making the entity immobile
     * 
     * @param attractionPoint the destination point (ignored)
     * @param speed the speed of movement (ignored)
     * @param currentLocation the current location (ignored)
     * @return an empty WayPointPath object
     */
    @Override
    public WayPointPath makePath(Attractor attractionPoint, double speed, Location currentLocation) {
        // Simply return an empty path, causing the entity to remain stationary
        return new WayPointPath();
    }
} 
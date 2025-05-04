package org.fog.mobility;

import org.fog.mobilitydata.Location;
import org.fog.utils.Logger;

/**
 * A mobility state implementation for users that never move.
 * Perfect for simulations where no mobility is needed but the architecture
 * still expects mobility states to be present.
 */
public class ImmobileUserMobilityState extends DeviceMobilityState {
    
    public enum ImmobileUserStatus {
        STATIONARY  // Only one status - never changes
    }
    
    /**
     * Creates a new immobile user state
     * 
     * @param location the fixed location where the user will remain
     */
    public ImmobileUserMobilityState(Location location) {
        // Use the LazyBugPathingStrategy since we never need to calculate paths
        super(location, new LazyBugPathingStrategy(), 0.0);  // Speed is 0
        this.status = ImmobileUserStatus.STATIONARY;
    }
    
    /**
     * Creates a new immobile user state with a specific random seed
     * 
     * @param location the fixed location where the user will remain
     * @param seed random seed for consistency
     */
    public ImmobileUserMobilityState(Location location, long seed) {
        // Use the LazyBugPathingStrategy since we never need to calculate paths
        super(location, new LazyBugPathingStrategy(seed), 0.0);  // Speed is 0
        this.status = ImmobileUserStatus.STATIONARY;
    }

    @Override
    public void updateAttractionPoint(Attractor currentAttractionPoint) {
        // Do nothing - immobile users have no attraction points
    }

    @Override
    public void reachedDestination() {
        // Do nothing - immobile users never reach destinations
        Logger.debug("Immobile Mobility", "reachedDestination called but user is immobile");
    }
    
    @Override
    public void startMoving() {
        // Do nothing - immobile users never start moving
        Logger.debug("Immobile Mobility", "startMoving called but user is immobile");
    }
    
    @Override
    public double handleEvent(int eventType, Object eventData) {
        // Immobile users don't respond to events by moving
        return -1.0;
    }
    
    @Override
    public void makePath() {
        // Even though we override this to do nothing, the superclass implementation
        // would just call strategy.makePath() which returns an empty path, so either approach works
        Logger.debug("Immobile Mobility", "makePath called but user is immobile");
    }
} 
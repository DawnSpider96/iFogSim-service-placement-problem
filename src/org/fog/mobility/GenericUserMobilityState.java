package org.fog.mobility;

import org.fog.mobilitydata.Location;
import org.fog.utils.Logger;

/**
 * A concrete implementation of DeviceMobilityState that implements
 * a simple mobility pattern for generic users in the fog network.
 * This mobility state handles movement for GENERIC_USER, AMBULANCE_USER, and OPERA_USER device types.
 */
public class GenericUserMobilityState extends DeviceMobilityState {
    
    // Possible states for the device
    public enum GenericUserStatus {
        PAUSED,    // Device is paused at a location
        WALKING     // Device is moving to a destination
    }
    
    /**
     * Creates a new generic user mobility state
     * 
     * @param location initial device location
     * @param strategy the mobility strategy to use
     * @param speed the travel speed (e.g., meters/second)
     */
    public GenericUserMobilityState(Location location, PathingStrategy strategy,
                                  double speed) {
        super(location, strategy, speed);
        this.status = GenericUserStatus.PAUSED;        
    }

    @Override
    public void updateAttractionPoint(Attractor currentAttractionPoint) {
        PauseTimeStrategy pts;
        if (currentAttractionPoint == null) {
            pts = new PauseTimeStrategy(getStrategy().getSeed());
        }
        else {
            pts = currentAttractionPoint.getPauseTimeStrategy();
        }

        Location randomPoint = Location.getRandomLocation();
        this.currentAttractor = new Attractor(
            randomPoint,
            "Generic User",
            10.0,
            60.0,
            pts
        );
    }

    @Override
    public void reachedDestination() {
        if (this.status == GenericUserStatus.WALKING) {
            this.status = GenericUserStatus.PAUSED;
        }
        else {
            Logger.error("Entity Status Error", "Invalid for reachedDestination");
        }
    }
    
    @Override
    public void startMoving() {
        if (this.status == GenericUserStatus.PAUSED) {
            this.status = GenericUserStatus.WALKING;
        }
        else {
            Logger.error("Entity Status Error", "Invalid for startMoving");
        }
    }

    
}

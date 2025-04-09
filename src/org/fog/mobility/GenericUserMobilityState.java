package org.fog.mobility;

import java.util.Random;
import org.fog.mobilitydata.Location;
import org.fog.mobility.MobilityStrategy;
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
    
    private Random random;
    private double minPauseTime;
    private double maxPauseTime;
    
    /**
     * Creates a new generic user mobility state
     * 
     * @param location initial device location
     * @param strategy the mobility strategy to use
     * @param speed the travel speed (e.g., meters/second)
     * @param minPauseTime minimum pause time at destinations
     * @param maxPauseTime maximum pause time at destinations
     */
    public GenericUserMobilityState(Location location, MobilityStrategy strategy, 
                                  double speed, double minPauseTime, double maxPauseTime) {
        super(location, strategy, speed);
        this.status = GenericUserStatus.PAUSED;        
    }

    @Override
    public void createAttractionPoint(Attractor currentAttractionPoint) {
        Location randomPoint = Location.getRandomLocation();
        this.currentAttractor = new Attractor(
            randomPoint,
            "Generic User",
            currentAttractionPoint.getPauseTimeMin(),
            currentAttractionPoint.getPauseTimeMax(),
            currentAttractionPoint.getPauseTimeStrategy()
        );
    }

    @Override
    public void reachedDestination() {
        if (this.status == GenericUserStatus.PAUSED) {
            this.status = GenericUserStatus.WALKING;
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

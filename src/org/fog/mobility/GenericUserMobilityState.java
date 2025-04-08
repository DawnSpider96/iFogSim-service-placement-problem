package org.fog.mobility;

import org.fog.mobilitydata.Location;
import org.fog.mobility.MobilityStrategy;
import org.fog.utils.Logger;


public class GenericUserMobilityState extends DeviceMobilityState {

    public enum GenericUserStatus {
        WALKING,
        PAUSED
    }
    
    public GenericUserMobilityState(Location location, MobilityStrategy strategy, double speed) {
        super(location, strategy, speed);
        this.status = GenericUserStatus.PAUSED;        
    }

    @Override
    public void createAttractionPoint(Attractor currentAttractionPoint) {
        Location randomPoint = Location.getRandomLocation();
        this.attractionPoint = new Attractor(
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

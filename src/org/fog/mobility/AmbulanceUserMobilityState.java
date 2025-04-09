package org.fog.mobility;

import org.fog.mobilitydata.Location;
import org.fog.mobility.MobilityStrategy;
import org.fog.utils.Logger;


public class AmbulanceUserMobilityState extends DeviceMobilityState {

    public enum AmbulanceUserStatus {
        TRAVELLING_TO_HOSPITAL,
        TRAVELLING_TO_PATIENT,
        PAUSED
    }
    
    public AmbulanceUserMobilityState(Location location, MobilityStrategy strategy, double speed) {
        // TODO Make sure start at hospital
        super(Location.HOSPITAL1, strategy, speed);
        this.status = AmbulanceUserStatus.PAUSED;        
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
        // Simon (080425) says Possible integration with actual applications/services
        if (this.status == AmbulanceUserStatus.TRAVELLING_TO_HOSPITAL) {
            this.status = AmbulanceUserStatus.PAUSED;
        }
        else if (this.status == AmbulanceUserStatus.TRAVELLING_TO_PATIENT) {
            this.status = AmbulanceUserStatus.TRAVELLING_TO_HOSPITAL;
        }
        else {
            Logger.error("Entity Status Error", "Invalid for reachedDestination");
        }
    }
    
    @Override
    public void startMoving() {
        if (this.status == AmbulanceUserStatus.PAUSED) {
            this.status = AmbulanceUserStatus.TRAVELLING_TO_HOSPITAL;
        }
        else {
            Logger.error("Entity Status Error", "Invalid for startMoving");
        }
    }

    
}

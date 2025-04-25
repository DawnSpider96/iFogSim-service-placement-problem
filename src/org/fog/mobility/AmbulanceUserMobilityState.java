package org.fog.mobility;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.Location;
import org.fog.mobility.PathingStrategy;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.Logger;


public class AmbulanceUserMobilityState extends DeviceMobilityState {

    public enum AmbulanceUserStatus {
        TRAVELLING_TO_HOSPITAL,
        TRAVELLING_TO_PATIENT,
        PAUSED_AT_PATIENT,
        PAUSED_AT_HOSPITAL,
        WAITING_FOR_EMERGENCY
    }

    int patientIndex = 0;
    
    public AmbulanceUserMobilityState(Location location, PathingStrategy strategy, double speed) {
        // TODO Make sure start at hospital.
        //  Next time if we have multiple hospitals, each ambulance's hospital can be passed in as argument.
        super(Location.HOSPITAL1, strategy, speed);
        this.status = AmbulanceUserStatus.WAITING_FOR_EMERGENCY;

    }

    // Called right after startMoving()
    @Override
    public void updateAttractionPoint(Attractor currentAttractionPoint) {
        PauseTimeStrategy pts;
        if (currentAttractionPoint == null) {
            pts = new PauseTimeStrategy(getStrategy().getSeed());
        }
        else {
            pts = currentAttractionPoint.getPauseTimeStrategy();
        }

        if (status == AmbulanceUserStatus.WAITING_FOR_EMERGENCY) {
            // no-op
        }
        else if (status == AmbulanceUserStatus.TRAVELLING_TO_PATIENT) {
            Location operaHouse = Config.OPERA_HOUSE;
            // Situation is that opera house exploded and casualties are being dragged outside.
            // Hence, they are scattered within 50m radius of the exact opera house coordinates.
            Location randomPointNearOperaHouse = Location.getRandomLocationWithinRadius(operaHouse.getLatitude(), operaHouse.getLongitude(), 50);
            this.currentAttractor = new Attractor( // Ambulance spends up to 1 minute picking up patient.
                    randomPointNearOperaHouse,
                    "Random Patient " + patientIndex,
                    30,
                    60,
                    pts
            );
            // After incrementing this is the i-th patient
            patientIndex++;
        }
        else if (status == AmbulanceUserStatus.TRAVELLING_TO_HOSPITAL) {
            Location hospital = Config.HOSPITAL1;
            this.currentAttractor = new Attractor( // Ambulance will park at hospital for up to 5 minutes.
                    hospital,
                    "Hospital",
                    30,
                    300,
                    pts
            );
        }
        else throw new NullPointerException("Invalid state for updateAttractionPoint.");
    }

    @Override
    public void reachedDestination() {
        // Simon (080425) says Possible integration with actual applications/services
        if (this.status == AmbulanceUserStatus.TRAVELLING_TO_HOSPITAL) {
            this.status = AmbulanceUserStatus.PAUSED_AT_HOSPITAL;
        }
        else if (this.status == AmbulanceUserStatus.TRAVELLING_TO_PATIENT) {
            this.status = AmbulanceUserStatus.PAUSED_AT_PATIENT;
        }
        else {
            Logger.error("Entity Status Error", "Invalid for reachedDestination");
        }
    }
    
    @Override
    public void startMoving() {
        // If waiting for an emergency, do nothing
        if (this.status == AmbulanceUserStatus.WAITING_FOR_EMERGENCY) {
            return;  // Early return without doing anything
        }
        
        // Original implementation continues unchanged
        if (this.status == AmbulanceUserStatus.PAUSED_AT_PATIENT) {
            this.status = AmbulanceUserStatus.TRAVELLING_TO_HOSPITAL;
        }
        else if (this.status == AmbulanceUserStatus.PAUSED_AT_HOSPITAL) {
            this.status = AmbulanceUserStatus.TRAVELLING_TO_PATIENT;
        }
        else {
            Logger.error("Entity Status Error", "Invalid for startMoving");
        }
    }

    @Override
    public boolean handleEvent(int eventType, Object eventData) {
        if (eventType == FogEvents.OPERA_ACCIDENT_EVENT) {
            // Can respond from any waiting state
            if (this.status == AmbulanceUserStatus.WAITING_FOR_EMERGENCY) {
                
                // Change to traveling to patient state
                this.status = AmbulanceUserStatus.TRAVELLING_TO_PATIENT;
                updateAttractionPoint(null);
                makePath();
                
                Logger.debug("Ambulance Mobility", "Ambulance responding to opera house emergency");
                return true;
            }
        }
        return false;
    }

    @Override
    public void makePath() {
        // Explicitly do nothing if waiting for emergency
        if (this.status == AmbulanceUserStatus.WAITING_FOR_EMERGENCY) {
            // No-op: Ambulance should not create a path while waiting for emergency
            return;
        }
        
        // For all other states, proceed with normal path creation
        if (currentAttractor != null && strategy != null) {
            path = strategy.makePath(currentAttractor, speed, currentLocation);
        }
    }
}

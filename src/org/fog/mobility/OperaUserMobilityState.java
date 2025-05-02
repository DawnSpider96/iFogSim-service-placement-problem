package org.fog.mobility;

import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.Location;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.Logger;
import java.util.Random;

/**
 * Mobility state for Opera users.
 * These users have specific behavior related to the opera house:
 * - They travel to the opera house
 * - They stay there for a while
 * - If an explosion happens, they evacuate to a random location
 */
public class OperaUserMobilityState extends DeviceMobilityState {

    public enum OperaUserStatus {
        TRAVELING_TO_OPERA,
        AT_OPERA,
        EVACUATING
    }

    private double eventTime;
    private Random random;

    /**
     * Creates a new opera user mobility state with the given location, strategy, and speed.
     *
     * @param location initial location
     * @param strategy pathing strategy
     * @param speed movement speed in m/s
     * @param eventTime time at which the user should be at the opera (in simulation time)
     */
    public OperaUserMobilityState(Location location, PathingStrategy strategy, double speed, double eventTime) {
        super(location, strategy, speed);
        this.status = OperaUserStatus.TRAVELING_TO_OPERA;
        this.eventTime = eventTime;
        this.random = new Random(strategy.getSeed());
    }

    /**
     * Updates the attraction point based on the current status
     */
    @Override
    public void updateAttractionPoint(Attractor currentAttractionPoint) {
        PauseTimeStrategy pts;
        if (currentAttractionPoint == null) {
            pts = new PauseTimeStrategy(getStrategy().getSeed());
        } else {
            pts = currentAttractionPoint.getPauseTimeStrategy();
        }

        if (status == OperaUserStatus.TRAVELING_TO_OPERA) {
            Location operaHouse = Location.getPointOfInterest("OPERA_HOUSE");
            // Make them stay at the opera for a fixed time (1 hour)
            this.currentAttractor = new Attractor(
                    operaHouse,
                    "Opera House",
                    30,
                    3600,  // 1 hour
                    pts
            );
        } else if (status == OperaUserStatus.EVACUATING) {
            // When evacuating, go to a random location 100-300m away from the opera house
            double randomDistance = 100 + random.nextDouble() * 200;  // 100-300m
            Location operaHouse = Location.getPointOfInterest("OPERA_HOUSE");
            Location evacLocation = Location.getRandomLocationWithinRadius(
                    operaHouse.getLatitude(),
                    operaHouse.getLongitude(),
                    randomDistance,
                    strategy.getSeed()
            );
            
            this.currentAttractor = new Attractor(
                    evacLocation,
                    "Evacuation Point",
                    10,
                    600,  // 10 minutes
                    pts
            );
        }
    }

    /**
     * Called when the user reaches the destination
     */
    @Override
    public void reachedDestination() {
        if (this.status == OperaUserStatus.TRAVELING_TO_OPERA) {
            this.status = OperaUserStatus.AT_OPERA;
            Logger.debug("Opera User", "User reached the opera house");
        }
    }
    
    /**
     * Implements the startMoving method from the DeviceMobilityState abstract class.
     * For Opera users, this handles transitioning between states when they start moving again.
     */
    @Override
    public void startMoving() {
        if (this.status == OperaUserStatus.EVACUATING) {
            // Just continue evacuating
            Logger.debug("Opera User", "User continuing evacuation movement");
        } else {
            Logger.debug("Opera User", "User starting movement from state: " + this.status);
        }
    }

    /**
     * Handles events like the opera house explosion
     */
    @Override
    public boolean handleEvent(int eventType, Object eventData) {
        if (eventType == FogEvents.OPERA_ACCIDENT_EVENT) {
            // Only evacuate if at the opera
            if (this.status == OperaUserStatus.AT_OPERA) {
                this.status = OperaUserStatus.EVACUATING;
                updateAttractionPoint(null);
                makePath();
                Logger.debug("Opera User", "User evacuating from opera house after explosion");
                return true;
            }
        }
        return false;
    }
} 
package org.fog.mobility;

import org.apache.commons.math3.exception.NullArgumentException;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.Location;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.Logger;

/**
 * A concrete implementation of DeviceMobilityState that implements
 * the mobility pattern for opera house users in the fog network.
 * These users go to the opera house for a concert and become immobile after an accident.
 */
public class OperaUserMobilityState extends DeviceMobilityState {
    
    /**
     * Possible states for an Opera user
     */
    public enum OperaUserStatus {
        WAITING_AT_SPAWN,
        TRAVELLING_TO_OPERA,
        AT_OPERA,
        IMMOBILE_AFTER_ACCIDENT
    }
    
    private double concertStartTime;
    
    /**
     * Creates a new opera user mobility state
     * 
     * @param location initial device location
     * @param strategy the mobility strategy to use
     * @param speed the travel speed (meters/second)
     * @param concertStartTime the time when the concert starts
     */
    public OperaUserMobilityState(Location location, PathingStrategy strategy, 
                               double speed, double concertStartTime) {
        super(location, strategy, speed);
        this.status = OperaUserStatus.WAITING_AT_SPAWN;
        this.concertStartTime = concertStartTime;
        
        // Adjust speed if needed to arrive before concert
        adjustSpeedForTimedArrival(location);
    }
    
    /**
     * Adjusts walking speed if needed to arrive at opera house before concert,
     * accounting for potential speed variations in the pathing strategy.
     */
    private void adjustSpeedForTimedArrival(Location initialLocation) {
        Location operaHouse = Config.OPERA_HOUSE;
        double distanceToOpera = initialLocation.calculateDistance(operaHouse) * Consts.KM_TO_METERS;
        double estimatedTravelTime = distanceToOpera / speed;
        double currentTime = CloudSim.clock();
        
        // Factor to account for worst-case speed variations in JitterBugPathingStrategy
        double speedBufferFactor = 1.0 / (1.0 - 0.2); // 0.2 is JitterBugPathingStrategy.MAX_SPEED_VARIATION
        
        // Add time buffer (15 time units early) and apply speed buffer factor
        if (currentTime + estimatedTravelTime > concertStartTime - 15) {
            // Calculate base required speed for timely arrival
            double requiredSpeed = distanceToOpera / (concertStartTime - currentTime - 15);
            
            // Apply buffer factor to account for potential slowdowns during path creation
            requiredSpeed *= speedBufferFactor;
            
            // Cap at reasonable walking speed (up to 3x normal)
            if (requiredSpeed > speed && requiredSpeed < speed * 3) {
                this.speed = requiredSpeed;
                Logger.debug("Opera Mobility", "Adjusted speed to " + requiredSpeed + 
                            " m/s (with buffer) to arrive before concert");
            } else if (requiredSpeed >= speed * 3) {
                throw new NullPointerException("Opera user will not arrive in time for concert");
            }
        } else {
            Logger.debug("Opera Mobility", "User will arrive on time with current speed of " + speed + " m/s");
        }
    }

    @Override
    public void updateAttractionPoint(Attractor currentAttractionPoint) {
        PauseTimeStrategy pts = (currentAttractionPoint == null) ? 
                new PauseTimeStrategy(getStrategy().getSeed()) : currentAttractionPoint.getPauseTimeStrategy();

        if (status == OperaUserStatus.TRAVELLING_TO_OPERA) {
            Location operaHouse = Config.OPERA_HOUSE;
            
            // Set minimum and maximum stay durations to ensure they stay at opera
            // Use MAX_SIMULATION_TIME or Double.MAX_VALUE
            double MAX_STAY_DURATION = Config.MAX_SIMULATION_TIME;
            
            this.currentAttractor = new Attractor(
                    operaHouse,
                    "Opera House",
                    MAX_STAY_DURATION,  // They will stay at opera until the end
                    MAX_STAY_DURATION,
                    pts
            );
        }
        else if (status != OperaUserStatus.IMMOBILE_AFTER_ACCIDENT) {
            throw new NullPointerException("Invalid status for update attraction point.");
        }
    }

    @Override
    public void reachedDestination() {
        if (this.status == OperaUserStatus.TRAVELLING_TO_OPERA) {
            this.status = OperaUserStatus.AT_OPERA;
            
            Logger.debug("Opera Mobility", "Opera user reached opera house");
        }
        else {
            throw new NullPointerException("Invalid status for reached destination");
        }
    }
    
    @Override
    public void startMoving() {
        if (status == OperaUserStatus.WAITING_AT_SPAWN) {
            status = OperaUserStatus.TRAVELLING_TO_OPERA;
        }
        else {
            throw new NullPointerException("Opera user should not start moving");
        }
    }
    
    @Override
    public boolean handleEvent(int eventType, Object eventData) {
        if (eventType == FogEvents.OPERA_ACCIDENT_EVENT) {
            if (this.status == OperaUserStatus.AT_OPERA) {
                this.status = OperaUserStatus.IMMOBILE_AFTER_ACCIDENT;
                
                // Clear any remaining path to ensure no movement
                // But path should be empty
                while (!this.path.isEmpty()) {
                    this.path.removeNextWayPoint();
                }
                Logger.debug("Opera Mobility", "User became immobile due to accident event");
                return true;
            }
            else {
                throw new NullPointerException("Opera user should be at the opera house");
            }
        }
        
        // Not handled or already in immobile state
        return false;
    }
} 
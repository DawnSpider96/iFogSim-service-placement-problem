package org.fog.test.perfeval;

import org.fog.utils.Logger;

import java.util.HashMap;
import java.util.Map;

public class SimulationConfig {
    final int numberOfEdge;
    final int numberOfUser;
    final int numberOfApplications;
    final int appLoopLength;
    final int placementLogic;
    final Map<String, Integer> usersPerType;
    
    // Random seed configuration
    private final int experimentSeed;
    private final int locationSeed;
    private final int mobilityStrategySeed;
    
    // Default seed values
    private static final int DEFAULT_EXPERIMENT_SEED = 33;
    private static final int DEFAULT_LOCATION_SEED = 42;
    private static final int DEFAULT_MOBILITY_STRATEGY_SEED = 123;

    /**
     * Constructor for the new configuration format with numberOfApplications and appLoopLength
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                           int numberOfApplications, int appLoopLength,
                           Map<String, Integer> usersPerType,
                           int experimentSeed, int locationSeed, int mobilityStrategySeed) {
        this.numberOfEdge = numberOfEdge;
        this.placementLogic = placementLogic;
        this.numberOfApplications = numberOfApplications;
        this.appLoopLength = appLoopLength;
        this.usersPerType = usersPerType;
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        this.experimentSeed = experimentSeed;
        this.locationSeed = locationSeed;
        this.mobilityStrategySeed = mobilityStrategySeed;
        
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
    }

    /**
     * Legacy constructor for backward compatibility
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType,
                            int experimentSeed, int locationSeed, int mobilityStrategySeed) {
        this.numberOfEdge = numberOfEdge;
        this.placementLogic = placementLogic;
        // Default values for new fields
        this.numberOfApplications = 0; // Not used in legacy mode
        this.appLoopLength = 0; // Not used in legacy mode
        this.usersPerType = usersPerType;
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        this.experimentSeed = experimentSeed;
        this.locationSeed = locationSeed;
        this.mobilityStrategySeed = mobilityStrategySeed;
        
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
    }

    /**
     * Legacy constructor for backward compatibility
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType) {
        this(numberOfEdge, placementLogic, usersPerType, appLoopLengthPerType, 
             DEFAULT_EXPERIMENT_SEED, DEFAULT_LOCATION_SEED, DEFAULT_MOBILITY_STRATEGY_SEED);
    }

    @Override
    public String toString() {
        if (numberOfApplications > 0) {
            return String.format("numberOfEdge: %d, numberOfApplications: %d, appLoopLength: %d, " +
                             "numberOfUser: %d, placementLogic: %d, " +
                             "experimentSeed: %d, locationSeed: %d, mobilityStrategySeed: %d",
                numberOfEdge, numberOfApplications, appLoopLength, 
                numberOfUser, placementLogic, 
                experimentSeed, locationSeed, mobilityStrategySeed);
        } else {
            return String.format("numberOfEdge: %d, numberOfUser: %d, placementLogic: %d, " +
                             "experimentSeed: %d, locationSeed: %d, mobilityStrategySeed: %d",
                numberOfEdge, numberOfUser, placementLogic, 
                experimentSeed, locationSeed, mobilityStrategySeed);
        }
    }

    public int getPlacementLogic() {
        return placementLogic;
    }

    public int getNumberOfEdge() {
        return numberOfEdge;
    }

    public int getNumberOfUser() {
        return numberOfUser;
    }
    
    public int getNumberOfApplications() {
        return numberOfApplications;
    }
    
    public int getAppLoopLength() {
        return appLoopLength;
    }
    
    public int getExperimentSeed() {
        return experimentSeed;
    }
    
    public int getLocationSeed() {
        return locationSeed;
    }
    
    public int getMobilityStrategySeed() {
        return mobilityStrategySeed;
    }
}

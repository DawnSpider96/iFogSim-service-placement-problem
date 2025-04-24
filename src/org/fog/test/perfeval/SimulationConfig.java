package org.fog.test.perfeval;

import org.fog.utils.Logger;

import java.util.HashMap;
import java.util.Map;

public class SimulationConfig {
    final int numberOfEdge;
    final int numberOfUser;
    final Map<String, Integer> appLoopLengthPerType;
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

    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType) {
        this(numberOfEdge, placementLogic, usersPerType, appLoopLengthPerType, 
             DEFAULT_EXPERIMENT_SEED, DEFAULT_LOCATION_SEED, DEFAULT_MOBILITY_STRATEGY_SEED);
    }

    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType,
                            int experimentSeed, int locationSeed, int mobilityStrategySeed) {
        this.usersPerType = usersPerType;
        this.appLoopLengthPerType = appLoopLengthPerType;
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
        this.numberOfEdge = numberOfEdge;
        this.placementLogic = placementLogic;
        this.experimentSeed = experimentSeed;
        this.locationSeed = locationSeed;
        this.mobilityStrategySeed = mobilityStrategySeed;
    }

    @Override
    public String toString() {
        return String.format("numberOfEdge: %d, numberOfUser: %d, appLoopLengthPerType: %s, placementLogic: %d, " +
                            "experimentSeed: %d, locationSeed: %d, mobilityStrategySeed: %d",
                numberOfEdge, numberOfUser, appLoopLengthPerType, placementLogic, 
                experimentSeed, locationSeed, mobilityStrategySeed);
    }


    public Map<String, Integer> getAppLoopLengthPerType() {
        return appLoopLengthPerType;
    }

    public int getPlacementLogic() {
        return placementLogic;
    }

    public int getNumberOfEdge() {
        return numberOfEdge;
    }

    public int getNumberOfUser() {return numberOfUser;}
    
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

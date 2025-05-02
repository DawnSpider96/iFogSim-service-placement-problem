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
    
    // Map of user types to their interval values in seconds
    private final Map<String, Integer> intervalValues;
    
    // Placement process interval in seconds
    private final double placementProcessInterval;
    
    // Random seed configuration
    private final int experimentSeed;
    private final int locationSeed;
    private final int mobilityStrategySeed;
    private final int heuristicSeed;
    
    // Default seed values
    private static final int DEFAULT_EXPERIMENT_SEED = 33;
    private static final int DEFAULT_LOCATION_SEED = 42;
    private static final int DEFAULT_MOBILITY_STRATEGY_SEED = 123;
    private static final int DEFAULT_HEURISTIC_SEED = 456;
    
    // Default placement process interval
    private static final double DEFAULT_PLACEMENT_PROCESS_INTERVAL = 300.0;

    /**
     * Primary constructor that takes all configuration parameters
     * Use this constructor when you have interval values in seconds
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                           int numberOfApplications, int appLoopLength,
                           Map<String, Integer> usersPerType,
                           Map<String, Integer> intervalValues,
                           int experimentSeed, int locationSeed, int mobilityStrategySeed,
                           int heuristicSeed) {
        this(numberOfEdge, placementLogic, numberOfApplications, appLoopLength, 
             usersPerType, intervalValues, experimentSeed, locationSeed, 
             mobilityStrategySeed, heuristicSeed, DEFAULT_PLACEMENT_PROCESS_INTERVAL);
    }
    
    /**
     * Full constructor with placement process interval
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                           int numberOfApplications, int appLoopLength,
                           Map<String, Integer> usersPerType,
                           Map<String, Integer> intervalValues,
                           int experimentSeed, int locationSeed, int mobilityStrategySeed,
                           int heuristicSeed, double placementProcessInterval) {
        this.numberOfEdge = numberOfEdge;
        this.placementLogic = placementLogic;
        this.numberOfApplications = numberOfApplications;
        this.appLoopLength = appLoopLength;
        this.usersPerType = usersPerType;
        this.intervalValues = intervalValues != null ? intervalValues : new HashMap<>();
        this.placementProcessInterval = placementProcessInterval;
        
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        this.experimentSeed = experimentSeed;
        this.locationSeed = locationSeed;
        this.mobilityStrategySeed = mobilityStrategySeed;
        this.heuristicSeed = heuristicSeed;
        
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
    }

    /**
     * Constructor for the new configuration format without interval values
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                           int numberOfApplications, int appLoopLength,
                           Map<String, Integer> usersPerType,
                           Map<String, Integer> intervalValues,
                           int experimentSeed, int locationSeed, int mobilityStrategySeed) {
        this(numberOfEdge, placementLogic, numberOfApplications, appLoopLength, 
             usersPerType, intervalValues, experimentSeed, locationSeed, 
             mobilityStrategySeed, DEFAULT_HEURISTIC_SEED);
    }

    /**
     * Legacy constructor for user types with app loop lengths per type
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType,
                            int experimentSeed, int locationSeed, int mobilityStrategySeed,
                            int heuristicSeed) {
        this.numberOfEdge = numberOfEdge;
        this.placementLogic = placementLogic;
        // Default values for new fields
        this.numberOfApplications = 0; // Not used in legacy mode
        this.appLoopLength = 0; // Not used in legacy mode
        this.usersPerType = usersPerType;
        this.placementProcessInterval = DEFAULT_PLACEMENT_PROCESS_INTERVAL;
        
        // Set default interval values for backward compatibility
        // Generic user: 1 event every 5 minutes (300 seconds)
        // Ambulance user: 1 event every 3 minutes (180 seconds)
        // Opera user: 1 event every 10 minutes (600 seconds)
        this.intervalValues = new HashMap<>();
        this.intervalValues.put("genericUser", 300);
        this.intervalValues.put("ambulanceUser", 180);
        this.intervalValues.put("operaUser", 600);
        this.intervalValues.put("immobileUser", 900);
        
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        this.experimentSeed = experimentSeed;
        this.locationSeed = locationSeed;
        this.mobilityStrategySeed = mobilityStrategySeed;
        this.heuristicSeed = heuristicSeed;
        
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
    }

    /**
     * Legacy constructor with default seed values
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType,
                            int experimentSeed, int locationSeed, int mobilityStrategySeed) {
        this(numberOfEdge, placementLogic, usersPerType, appLoopLengthPerType, 
             experimentSeed, locationSeed, mobilityStrategySeed, DEFAULT_HEURISTIC_SEED);
    }

    /**
     * Legacy constructor for backward compatibility without any seed values
     */
    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType) {
        this(numberOfEdge, placementLogic, usersPerType, appLoopLengthPerType, 
             DEFAULT_EXPERIMENT_SEED, DEFAULT_LOCATION_SEED, DEFAULT_MOBILITY_STRATEGY_SEED, DEFAULT_HEURISTIC_SEED);
    }

    @Override
    public String toString() {
        if (numberOfApplications > 0) {
            return String.format("numberOfEdge: %d, numberOfApplications: %d, appLoopLength: %d, " +
                             "numberOfUser: %d, placementLogic: %d, placementProcessInterval: %.1f, " +
                             "experimentSeed: %d, locationSeed: %d, mobilityStrategySeed: %d, heuristicSeed: %d",
                numberOfEdge, numberOfApplications, appLoopLength, 
                numberOfUser, placementLogic, placementProcessInterval,
                experimentSeed, locationSeed, mobilityStrategySeed, heuristicSeed);
        } else {
            return String.format("numberOfEdge: %d, numberOfUser: %d, placementLogic: %d, " +
                             "placementProcessInterval: %.1f, experimentSeed: %d, locationSeed: %d, " +
                             "mobilityStrategySeed: %d, heuristicSeed: %d",
                numberOfEdge, numberOfUser, placementLogic, placementProcessInterval,
                experimentSeed, locationSeed, mobilityStrategySeed, heuristicSeed);
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
    
    public double getPlacementProcessInterval() {
        return placementProcessInterval;
    }

    public Map<String, Integer> getUsersPerType() {
        return usersPerType;
    }

    /**
     * Gets the map of user types to their lambda values for the Poisson distribution
     * These are calculated on-the-fly from interval values (lambda = 1/interval)
     * 
     * @return Map of user types to lambda values
     */
    public Map<String, Double> getLambdaValues() {
        Map<String, Double> lambdaValues = new HashMap<>();
        if (intervalValues != null) {
            for (Map.Entry<String, Integer> entry : intervalValues.entrySet()) {
                if (entry.getValue() > 0) {
                    lambdaValues.put(entry.getKey(), 1.0 / entry.getValue());
                }
            }
        }
        return lambdaValues;
    }
    
    /**
     * Gets the map of user types to their interval values in seconds
     * 
     * @return Map of user types to interval values
     */
    public Map<String, Integer> getIntervalValues() {
        return intervalValues;
    }

    public int getHeuristicSeed() {
        return heuristicSeed;
    }
}

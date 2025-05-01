package org.fog.placement;

import org.fog.utils.Logger;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Samodha Pallewatta.
 */
public class PlacementLogicFactory {

    // Integer constants for backward compatibility
    public static final int EDGEWART_MICROSERCVICES_PLACEMENT = 1;
    public static final int CLUSTERED_MICROSERVICES_PLACEMENT = 2;
    public static final int DISTRIBUTED_MICROSERVICES_PLACEMENT = 3;
    public static final int MY_MICROSERVICES_PLACEMENT = 4;
    public static final int MY_OFFLINE_POC_PLACEMENT = 5;
    public static final int MY_ONLINE_POC_PLACEMENT = 6;

    // Simon
    public static final int BEST_FIT = 7;
    public static final int CLOSEST_FIT = 8;
    public static final int MAX_FIT = 9;
    public static final int RANDOM = 10;
    public static final int MULTI_OPT = 11;
    public static final int SIMULATED_ANNEALING = 12;
    public static final int ACO = 13;
    public static final int ILP = 14;

    // String constants for readability in config files
    public static final String EDGEWART_MICROSERCVICES_PLACEMENT_STR = "EDGEWART";
    public static final String CLUSTERED_MICROSERVICES_PLACEMENT_STR = "CLUSTERED";
    public static final String DISTRIBUTED_MICROSERVICES_PLACEMENT_STR = "DISTRIBUTED";
    public static final String MY_MICROSERVICES_PLACEMENT_STR = "MY_MICROSERVICES";
    public static final String MY_OFFLINE_POC_PLACEMENT_STR = "OFFLINE_POC";
    public static final String MY_ONLINE_POC_PLACEMENT_STR = "ONLINE_POC";
    public static final String BEST_FIT_STR = "BEST_FIT";
    public static final String CLOSEST_FIT_STR = "CLOSEST_FIT";
    public static final String MAX_FIT_STR = "MAX_FIT";
    public static final String RANDOM_STR = "RANDOM";
    public static final String MULTI_OPT_STR = "MULTI_OPT";
    public static final String SIMULATED_ANNEALING_STR = "SIMULATED_ANNEALING";
    public static final String ACO_STR = "ACO";
    public static final String ILP_STR = "ILP";

    // Maps for string to int and int to string conversion
    private static final Map<String, Integer> stringToIntMap = new HashMap<>();
    private static final Map<Integer, String> intToStringMap = new HashMap<>();

    static {
        // Initialize the mapping
        stringToIntMap.put(EDGEWART_MICROSERCVICES_PLACEMENT_STR, EDGEWART_MICROSERCVICES_PLACEMENT);
        stringToIntMap.put(CLUSTERED_MICROSERVICES_PLACEMENT_STR, CLUSTERED_MICROSERVICES_PLACEMENT);
        stringToIntMap.put(DISTRIBUTED_MICROSERVICES_PLACEMENT_STR, DISTRIBUTED_MICROSERVICES_PLACEMENT);
        stringToIntMap.put(MY_MICROSERVICES_PLACEMENT_STR, MY_MICROSERVICES_PLACEMENT);
        stringToIntMap.put(MY_OFFLINE_POC_PLACEMENT_STR, MY_OFFLINE_POC_PLACEMENT);
        stringToIntMap.put(MY_ONLINE_POC_PLACEMENT_STR, MY_ONLINE_POC_PLACEMENT);
        stringToIntMap.put(BEST_FIT_STR, BEST_FIT);
        stringToIntMap.put(CLOSEST_FIT_STR, CLOSEST_FIT);
        stringToIntMap.put(MAX_FIT_STR, MAX_FIT);
        stringToIntMap.put(RANDOM_STR, RANDOM);
        stringToIntMap.put(MULTI_OPT_STR, MULTI_OPT);
        stringToIntMap.put(SIMULATED_ANNEALING_STR, SIMULATED_ANNEALING);
        stringToIntMap.put(ACO_STR, ACO);
        stringToIntMap.put(ILP_STR, ILP);

        // Populate reverse mapping
        for (Map.Entry<String, Integer> entry : stringToIntMap.entrySet()) {
            intToStringMap.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Convert string placement logic identifier to integer code
     * @param logicName The string name of the placement logic
     * @return The integer code for the placement logic, or -1 if not found
     */
    public static int getPlacementLogicCode(String logicName) {
        return stringToIntMap.getOrDefault(logicName.toUpperCase(), -1);
    }

    /**
     * Convert integer code to string placement logic name
     * @param code The integer code of the placement logic
     * @return The string name for the placement logic, or null if not found
     */
    public static String getPlacementLogicName(int code) {
        return intToStringMap.get(code);
    }

    /**
     * Get placement logic by string name
     * @param logicName String identifier for the placement logic
     * @param fonId FON ID (unrelated to the placement logic type)
     * @return The appropriate MicroservicePlacementLogic instance
     */
    public MicroservicePlacementLogic getPlacementLogic(String logicName, int fonId) {
        int logicCode = getPlacementLogicCode(logicName);
        if (logicCode == -1) {
            Logger.error("Placement Logic Error", "Unknown placement logic name: " + logicName);
            return null;
        }
        return getPlacementLogic(logicCode, fonId);
    }

    /**
     * Original method for getting placement logic by integer code
     * @param logic Integer code for the placement logic type
     * @param fonId FON ID (unrelated to the placement logic type)
     * @return The appropriate MicroservicePlacementLogic instance
     */
    public MicroservicePlacementLogic getPlacementLogic(int logic, int fonId) {
        switch (logic) {
//            case EDGEWART_MICROSERCVICES_PLACEMENT:
//                return new EdgewardMicroservicePlacementLogic(fonId);
            case CLUSTERED_MICROSERVICES_PLACEMENT:
                return new ClusteredMicroservicePlacementLogic(fonId);
            case DISTRIBUTED_MICROSERVICES_PLACEMENT:
                return new DistributedMicroservicePlacementLogic(fonId);
            case MY_MICROSERVICES_PLACEMENT:
                return new SPPMicroservicePlacementLogic(fonId);
            case MY_OFFLINE_POC_PLACEMENT:
                return new OfflinePOCPlacementLogic(fonId);
            case MY_ONLINE_POC_PLACEMENT:
                return new OnlinePOCPlacementLogicSPP(fonId);
            case BEST_FIT:
                return new BestFitHeuristic(fonId);
            case CLOSEST_FIT:
                return new ClosestFitHeuristic(fonId);
            case MAX_FIT:
                return new MaxFitHeuristic(fonId);
            case RANDOM:
                return new RandomHeuristic(fonId);
            case MULTI_OPT:
                return new MultiOptHeuristic(fonId);
            case SIMULATED_ANNEALING:
                return new SimulatedAnnealingHeuristic(fonId);
            case ACO:
                return new SPPACO(fonId);
            case ILP:
                return new ILPHeuristic(fonId);
        }

        Logger.error("Placement Logic Error", "Error initializing placement logic, unknown code: " + logic);
        return null;
    }
}

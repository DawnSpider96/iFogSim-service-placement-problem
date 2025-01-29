package org.fog.placement;

import org.fog.utils.Logger;

/**
 * Created by Samodha Pallewatta.
 */
public class PlacementLogicFactory {

    public static final int EDGEWART_MICROSERCVICES_PLACEMENT = 1;
    public static final int CLUSTERED_MICROSERVICES_PLACEMENT = 2;
    public static final int DISTRIBUTED_MICROSERVICES_PLACEMENT = 3;
    public static final int MY_MICROSERVICES_PLACEMENT = 4;
    public static final int MY_OFFLINE_POC_PLACEMENT = 5;
    public static final int MY_ONLINE_POC_PLACEMENT = 6;
    public static final int BEST_FIT = 7;
    public static final int CLOSEST_FIT = 8;
    public static final int MAX_FIT = 9;
    public static final int RANDOM = 10;
    public static final int MULTI_OPT = 11;
    public static final int SIMULATED_ANNEALING = 12;
    public static final int ACO = 13;


    public MicroservicePlacementLogic getPlacementLogic(int logic, int fonId) {
        switch (logic) {
//            case EDGEWART_MICROSERCVICES_PLACEMENT:
//                return new EdgewardMicroservicePlacementLogic(fonId);
            case CLUSTERED_MICROSERVICES_PLACEMENT:
                return new ClusteredMicroservicePlacementLogic(fonId);
            case DISTRIBUTED_MICROSERVICES_PLACEMENT:
                return new DistributedMicroservicePlacementLogic(fonId);
            case MY_MICROSERVICES_PLACEMENT:
                return new MyMicroservicePlacementLogic(fonId);
            case MY_OFFLINE_POC_PLACEMENT:
                return new MyOfflinePOCPlacementLogic(fonId);
            case MY_ONLINE_POC_PLACEMENT:
                return new MyOnlinePOCPlacementLogicCopy(fonId);
            case BEST_FIT:
                return new MyBestFitHeuristic(fonId);
            case CLOSEST_FIT:
                return new MyClosestFitHeuristic(fonId);
            case MAX_FIT:
                return new MyMaxFitHeuristic(fonId);
            case RANDOM:
                return new MyRandomHeuristic(fonId);
            case MULTI_OPT:
                return new MyMultiOptHeuristic(fonId);
            case SIMULATED_ANNEALING:
                return new MySimulatedAnnealingHeuristic(fonId);
            case ACO:
                return new MyACO(fonId);
        }

        Logger.error("Placement Logic Error", "Error initializing placement logic");
        return null;
    }

}

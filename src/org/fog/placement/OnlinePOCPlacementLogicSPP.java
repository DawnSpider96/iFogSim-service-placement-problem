package org.fog.placement;

import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.ContextPlacementRequest;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;

import java.util.*;

public class OnlinePOCPlacementLogicSPP extends SPPHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "Original iFogSim Placement Logic";
    }

    /**
     * Fog network related details
     */
    public OnlinePOCPlacementLogicSPP(int fonID) {
        super(fonID);
    }

    @Override
    protected int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {
        // Not used in this heuristic.
        // -1 means success, so return -2 just in case
        return -2;
    }

    @Override
    protected List<DeviceState> getCurrentDeviceStates() {
        throw new NullPointerException("Shouldn't be called");
    }

    @Override
    public void postProcessing() {    }

    /**
     * Prunes mappedMicroservices and updates placementRequests such that their entries reflect the modules placed in THIS cycle only.
     * This function should ONLY be called when placementRequests and mappedMicroservices do NOT contain matching entries.
     *
     * @param toPlace An empty/incomplete map of PlacementRequest to the list of Microservices (String) that require placement.
     *                CPU and RAM requirements of each Microservice can be obtained with getModule() method.
     * @see #getModule
     * @param placementRequests this.placementRequests, ie the list of all PlacementRequest objects
     * @return A map reflecting the updated entries after cleaning.
     */
    @Override
    protected int fillToPlace(int placementCompleteCount, Map<PlacementRequest, List<String>> toPlace, List<PlacementRequest> placementRequests) {
        int f = placementCompleteCount;
        for (PlacementRequest placementRequest : placementRequests) {
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            
            // Create a key for this placement request
            PlacementRequestKey prKey = new PlacementRequestKey(
                placementRequest.getSensorId(), 
                ((ContextPlacementRequest)placementRequest).getPrIndex()
            );
            
            // Skip if this placement request doesn't have an entry in mappedMicroservices yet
            if (!mappedMicroservices.containsKey(prKey)) {
                continue;
            }
            
            // modulesToPlace returns all the modules from the APP which 1. Have not been placed 2. All their dependent modules (from UP or DOWN) within their PR have been placed
            // NOTE: Every PR (primary key placementRequestId) has its own set of placed modules (stored in mappedMicroservices).
            // Meaning each module in all PRs has a separate set of dependent modules, which are from the same PR
            // As argument we pass the list of set modules FOR THAT PR that have been placed. But in the function we are iterating through ALL modules in the app
            List<String> modulesToPlace = getNextLayerOfModulesToPlace(mappedMicroservices.get(prKey).keySet(), app);
            if (modulesToPlace.isEmpty())
                f++;
            else
                toPlace.put(placementRequest, modulesToPlace);
        }
        return f;
    }


    /**
     * State updated:
     * -
     */
    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();
        Map<PlacementRequest, Integer> currentTargets = new HashMap<>(closestNodes);

        int placementCompleteCount = 0;
        while (placementCompleteCount < placementRequests.size()) {
            if (toPlace.isEmpty()) {
                // Update toPlace and placementCompleteCount
                placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
            }
            for (PlacementRequest placementRequest : placementRequests) {
                Application app = applicationInfo.get(placementRequest.getApplicationId());
                int deviceId = currentTargets.get(placementRequest); // NOTE: Initially contains parent ID of gateway device (MOBILE USER). Changes depending on how we "forward" the PR (if previous target device lacked resources).
                // if not cluster
                if (deviceId != -1) {
                    FogDevice device = getDevice(deviceId);
                    assert device != null;
                    List<String> placed = new ArrayList<>();

                    if (toPlace.containsKey(placementRequest)) {
                        for (String microservice : toPlace.get(placementRequest)) {
                            PlacementRequestKey prKey = new PlacementRequestKey(
                                    placementRequest.getSensorId(),
                                    ((ContextPlacementRequest)placementRequest).getPrIndex()
                            );
                            tryPlacingMicroserviceNoAggregate(microservice, device, app, placed::add, prKey);
                        }
                        for (String m : placed) {
                            toPlace.get(placementRequest).remove(m);
                        }
                        if (!toPlace.get(placementRequest).isEmpty()) {
                            currentTargets.put(placementRequest, device.getParentId());
                        }
                        if (toPlace.get(placementRequest).isEmpty())
                            toPlace.remove(placementRequest);
                    }
                } else {
                    Logger.error("Cluster issue", "deviceId is -1");
                    System.out.println("deviceId is -1");
                }
            }
        }
        Map<PlacementRequest, Integer> prStatus = new HashMap<>();
        for (PlacementRequest placementRequest : placementRequests) {
            prStatus.put(placementRequest, -1);
        }
        return prStatus;
    }
}

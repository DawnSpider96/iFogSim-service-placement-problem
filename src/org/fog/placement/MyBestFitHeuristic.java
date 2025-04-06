package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MyPlacementRequest;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;

import java.util.*;

public class MyBestFitHeuristic extends MyHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "BestFit";
    }

    /**
     * Fog network related details
     */
    public MyBestFitHeuristic(int fonID) {
        super(fonID);
    }

    private List<DeviceState> DeviceStates = new ArrayList<>();

    @Override
    public void postProcessing() {
    }

    /**
     * Determines all the modules to place in this cycle.
     * This implementation traces the AppLoop and compiles ALL modules from the AppLoop.
     *
     * @param toPlace           An empty/incomplete map of PlacementRequest to the list of Microservices (String) that require placement.
     *                          CPU and RAM requirements of each Microservice can be obtained with getModule() method.
     * @param placementRequests this.placementRequests, ie the list of all PlacementRequest objects
     * @return A map reflecting the updated entries after cleaning.
     * @see #getModule
     */
    @Override
    protected int fillToPlace(int placementCompleteCount, Map<PlacementRequest, List<String>> toPlace, List<PlacementRequest> placementRequests) {
        int f = placementCompleteCount;
        for (PlacementRequest placementRequest : placementRequests) {
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            
            // Create a key for this placement request
            PlacementRequestKey prKey = new PlacementRequestKey(
                placementRequest.getSensorId(), 
                ((MyPlacementRequest)placementRequest).getPrIndex()
            );
            
            // Skip if this placement request doesn't have an entry in mappedMicroservices yet
            if (!mappedMicroservices.containsKey(prKey)) {
                continue;
            }
            
            Set<String> alreadyPlaced = mappedMicroservices.get(prKey).keySet();
            List<String> completeModuleList = getAllModulesToPlace(new HashSet<>(alreadyPlaced), app);

            if (completeModuleList.isEmpty()) {
                Logger.error("Flow Control Error", "fillToPlace is called on a completed PR");
                f++;  // Increment only if no more modules can be placed
            } else {
                toPlace.put(placementRequest, completeModuleList);
            }
        }
        return f;
    }

    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
        }

        DeviceStates = new ArrayList<>();
        for (FogDevice fogDevice : edgeFogDevices) {
            DeviceStates.add(new DeviceState(fogDevice.getId(), resourceAvailability.get(fogDevice.getId()),
                    fogDevice.getHost().getTotalMips(), fogDevice.getHost().getRam(), fogDevice.getHost().getStorage()));
        }

        Map<PlacementRequest, Integer> prStatus = new HashMap<>();
        // Process every PR individually
        for (Map.Entry<PlacementRequest, List<String>> entry : toPlace.entrySet()) {
            PlacementRequest placementRequest = entry.getKey();
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            List<String> microservices = entry.getValue();

            // status is -1 if success, cloudId if failure
            // Cloud will resend to itself
            // Type int for flexibility: In more complex simulations there may be more FON heads, not just the cloud.
            int status = tryPlacingOnePr(microservices, app, placementRequest);
            prStatus.put(placementRequest, status);
        }
        return prStatus;
    }

    @Override
    protected int tryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {
        int[] placed = new int[microservices.size()];
        for (int i = 0 ; i < microservices.size() ; i++) {
            placed[i] = -1;
        }

        for (int j = 0 ; j < microservices.size() ; j++) {
            String s = microservices.get(j);
            AppModule service = getModule(s, app);
            Collections.sort(DeviceStates);

            if (!DeviceStates.get(0).canFit(service.getMips(), service.getRam(), service.getSize())) {
                Logger.error("Simulation limitation problem", "FogDevices have no resources! Check DeviceStates.");
            }

            for (int i = 0; i < DeviceStates.size(); i++) {
                // Try to place
                if (DeviceStates.get(i).canFit(service.getMips(), service.getRam(), service.getSize())) {

                    DeviceStates.get(i).allocate(service.getMips(), service.getRam(), service.getSize());
//                    addToCpu.accept(getModule(microservice, app).getMips());
//                    addToRam.accept(getModule(microservice, app).getRam());
//                    addToStorage.accept(getModule(microservice, app).getSize());
//                    markAsPlaced.accept(microservice);

                    // Update temporary state
                    placed[j]  = DeviceStates.get(i).getId();
                    break;
                }
            }

            if (placed[j] < 0) {
                MyPlacementRequest mpr = (MyPlacementRequest) placementRequest;
                // todo Simon says what do we do when failure?
                //  (160125) Nothing. Because (aggregated) failure will be determined outside the for loop
                System.out.printf("Failed to place module %s on PR %d, cycle %d%n",
                        s,
                        mpr.getSensorId(),
                        mpr.getPrIndex());
                System.out.println("Failed placement " + placementRequest.getSensorId());

                // Undo every "placement" recorded in placed. Only deviceStates was changed, so we change it back
                for (int i = 0 ; i < placed.length ; i++) {
                    int deviceId = placed[i];
                    String microservice = microservices.get(i);
                    if (deviceId != -1) {
                        DeviceState targetDeviceState = null;
                        for (DeviceState DeviceState : DeviceStates) {
                            if (DeviceState.getId() == deviceId) {
                                targetDeviceState = DeviceState;
                                break;
                            }
                        }
                        assert targetDeviceState != null;
                        AppModule placedService = getModule(microservice, app);
                        targetDeviceState.deallocate(placedService.getMips(), placedService.getRam(), placedService.getSize());
                    }
                }
                break;
            }
        }

        boolean allPlaced = true;
        for (int p : placed) {
            if (p == -1) allPlaced = false;
        }

        if (allPlaced) {
            // Create a key for this placement request
            PlacementRequestKey prKey = new PlacementRequestKey(
                placementRequest.getSensorId(), 
                ((MyPlacementRequest)placementRequest).getPrIndex()
            );
            
            // Ensure the key exists in mappedMicroservices
            if (!mappedMicroservices.containsKey(prKey)) {
                mappedMicroservices.put(prKey, new LinkedHashMap<>());
            }
            
            for (int i = 0 ; i < microservices.size(); i++) {
                String s = microservices.get(i);
                AppModule service = getModule(s, app);
                int deviceId = placed[i];

                System.out.printf("Placement of operator %s on device %s successful. Device id: %d, sensorId: %d, prIndex: %d%n",
                        s,
                        CloudSim.getEntityName(deviceId),
                        deviceId,
                        placementRequest.getSensorId(),
                        ((MyPlacementRequest) placementRequest).getPrIndex());

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(prKey).put(s, deviceId);

                //currentModuleLoad
                if (!currentModuleLoadMap.get(deviceId).containsKey(s))
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips());
                else
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips() + currentModuleLoadMap.get(deviceId).get(s)); // todo Simon says isn't this already vertical scaling? But is on PR side not FogDevice side

                //currentModuleInstance
                if (!currentModuleInstanceNum.get(deviceId).containsKey(s))
                    currentModuleInstanceNum.get(deviceId).put(s, 1);
                else
                    currentModuleInstanceNum.get(deviceId).put(s, currentModuleInstanceNum.get(deviceId).get(s) + 1);
            }
        }
        else {
            Logger.error("BestFit Control Flow Problem", "The program should not reach this code. See allPlaced and (placed.get(s) < 0).");
        }

        if (allPlaced) return -1;
        else return getFonID();
    }
}




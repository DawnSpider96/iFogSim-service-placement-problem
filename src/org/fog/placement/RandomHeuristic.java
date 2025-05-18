package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.ContextPlacementRequest;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;

import java.util.*;

public class RandomHeuristic extends SPPHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "Random";
    }

    /**
     * Fog network related details
     */
    public RandomHeuristic(int fonID) {
        super(fonID);
    }

    // Add DeviceStates field to match other implementations
    private List<DeviceState> DeviceStates = new ArrayList<>();
    private Map<Integer, DeviceState> deviceStateMap = new HashMap<>();

    private int cloudIndex = -1;
    // Maps DeviceId to index (on latencies and `fogDevices` state)
    @Override
    public void postProcessing() {
    }

    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
        }

        // Initialize DeviceStates similar to other implementations
        DeviceStates = new ArrayList<>();
        deviceStateMap = new HashMap<>();
        for (FogDevice fogDevice : edgeFogDevices) {
            DeviceState state = new DeviceState(
                fogDevice.getId(), 
                resourceAvailability.get(fogDevice.getId()),
                fogDevice.getHost().getTotalMips(), 
                fogDevice.getHost().getRam(), 
                fogDevice.getHost().getStorage()
            );
            DeviceStates.add(state);
            deviceStateMap.put(fogDevice.getId(), state);
        }

        Map<PlacementRequest, Integer> prStatus = new HashMap<>();
        // Process every PR individually
        for (Map.Entry<PlacementRequest, List<String>> entry : toPlace.entrySet()) {
            PlacementRequest placementRequest = entry.getKey();
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            List<String> microservices = entry.getValue();
            // -1 if success, cloudId if failure
            // Cloud will resend to itself
            // Type int for flexibility: In more complex simulations there may be more FON heads, not just the cloud.
            int status = processOnePr(microservices, app, placementRequest);
            prStatus.put(placementRequest, status);
        }
        return prStatus;
    }

    @Override
    protected List<DeviceState> getCurrentDeviceStates() {
        return DeviceStates;
    }

    @Override
    protected int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {

        // Initialize temporary state
        int[] placed = new int[microservices.size()];
        for (int i = 0 ; i < microservices.size() ; i++) {
            placed[i] = -1;
        }

        // Shallow Copy
        List<FogDevice> nodes = new ArrayList<>(edgeFogDevices);

        // If fails to fit, the entire PR should fail
        for (int j = 0 ; j < microservices.size() ; j++) {
            int nodeIndex = getRandom().nextInt(nodes.size());
            int deviceId = nodes.get(nodeIndex).getId();
            String s = microservices.get(j);
            AppModule service = getModule(s, app);

            DeviceState deviceState = deviceStateMap.get(deviceId);
            if (deviceState.canFit(service.getMips(), service.getRam(), service.getSize())) {
                deviceState.allocate(service.getMips(), service.getRam(), service.getSize());
                placed[j] = deviceId;
            }

            if (placed[j] < 0) {

                System.out.println("Failed to place module " + s + " on PR " + placementRequest.getSensorId());
                System.out.println("Failed placement " + placementRequest.getSensorId());

                // Undo every "placement" recorded in placed
                for (int i = 0 ; i < placed.length ; i++) {
                    int placedDeviceId = placed[i];
                    if (placedDeviceId != -1) {
                        String microservice = microservices.get(i);
                        AppModule placedService = getModule(microservice, app);
                        deviceStateMap.get(placedDeviceId).deallocate(
                            placedService.getMips(), 
                            placedService.getRam(), 
                            placedService.getSize()
                        );
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
                ((ContextPlacementRequest)placementRequest).getPrIndex()
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
                        ((ContextPlacementRequest) placementRequest).getPrIndex());

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(prKey).put(s, deviceId);

                //currentModuleLoad
                if (!currentModuleLoadMap.get(deviceId).containsKey(s))
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips());
                else
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips() + currentModuleLoadMap.get(deviceId).get(s));

                //currentModuleInstance
                if (!currentModuleInstanceNum.get(deviceId).containsKey(s))
                    currentModuleInstanceNum.get(deviceId).put(s, 1);
                else
                    currentModuleInstanceNum.get(deviceId).put(s, currentModuleInstanceNum.get(deviceId).get(s) + 1);
            }
        }
        else {
            Logger.error("Random Control Flow Error", "The program should not reach this code. See allPlaced and (placed.get(s) < 0).");
        }

        if (allPlaced) return -1;
        else return getFonID();
    }
}
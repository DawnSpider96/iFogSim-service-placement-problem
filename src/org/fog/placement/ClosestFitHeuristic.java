package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.entities.ContextPlacementRequest;
import org.fog.utils.Logger;

import java.util.*;

public class ClosestFitHeuristic extends SPPHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "ClosestFit";
    }

    /**
     * Fog network related details
     */
    public ClosestFitHeuristic(int fonID) {
        super(fonID);
    }

    // Add DeviceStates field to match other implementations
    private List<DeviceState> DeviceStates = new ArrayList<>();
    private Map<Integer, DeviceState> deviceStateMap = new LinkedHashMap<>();

    @Override
    public void postProcessing() {
    }

    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new LinkedHashMap<>();

        if(cloudIndex < 0) {
            Logger.error("Control Flow Error", "Cloud index should have value.");
        }

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
        }

        // Initialize DeviceStates similar to other implementations
        DeviceStates = new ArrayList<>();
        deviceStateMap = new LinkedHashMap<>();
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

        Map<PlacementRequest, Integer> prStatus = new LinkedHashMap<>();
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
        return new ArrayList<>(deviceStateMap.values());
    }

    @Override
    protected int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {

        // The closest node changes with every PR
        // Hence `nodes` needs to be remade repeatedly
        List<RelativeLatencyDeviceState> nodes = new ArrayList<>();
        FogDevice closestFogDevice = getDevice(closestNodes.get(placementRequest));

        for (FogDevice fogDevice : edgeFogDevices) {
            nodes.add(new RelativeLatencyDeviceState(fogDevice, closestFogDevice, globalLatencies));
        }

        Collections.sort(nodes);

        // Initialize temporary state
        int[] placed = new int[microservices.size()];
        for (int j = 0 ; j < microservices.size() ; j++) {
            placed[j] = -1;
        }


        for (int j = 0 ; j < microservices.size() ; j++) {
            String s = microservices.get(j);
            AppModule service = getModule(s, app);

            for (int i = 0; i < nodes.size(); i++) {
                int deviceId = nodes.get(i).fogDevice.getId();
                DeviceState deviceState = deviceStateMap.get(deviceId);
                if (deviceState.canFit(service.getMips(), service.getRam(), service.getSize())) {
                    deviceState.allocate(service.getMips(), service.getRam(), service.getSize());
                    placed[j] = deviceId;
                    break;
                }
            }

            if (placed[j] < 0) {
                ContextPlacementRequest mpr = (ContextPlacementRequest) placementRequest;
                System.out.printf("Failed to place module %s on PR %d, cycle %d%n",
                        s,
                        mpr.getSensorId(),
                        mpr.getPrIndex());
                System.out.println("Failed placement " + placementRequest.getSensorId());

                // Undo every "placement" recorded in placed using DeviceStates
                for (int i = 0 ; i < placed.length ; i++) {
                    int deviceId = placed[i];
                    if (deviceId != -1) {
                        String microservice = microservices.get(i);
                        AppModule placedService = getModule(microservice, app);
                        deviceStateMap.get(deviceId).deallocate(
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

        if (allPlaced) return -1;
        else return getFonID();
    }
}




package org.fog.placement;

import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;

import java.util.*;

public class MyBestFitHeuristic extends MyHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */
    public MyBestFitHeuristic(int fonID) {
        super(fonID);
    }

    @Override
    public void postProcessing() {
    }

    /**
     * Prunes mappedMicroservices and updates placementRequests such that their entries reflect the modules placed in THIS cycle only.
     * This function should ONLY be called when placementRequests and mappedMicroservices do NOT contain matching entries.
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
            Set<String> alreadyPlaced = mappedMicroservices.get(placementRequest.getPlacementRequestId()).keySet();
            List<String> completeModuleList = getAllModulesToPlace(new HashSet<>(alreadyPlaced), app);

            if (completeModuleList.isEmpty()) {
                f++;  // Increment only if no more modules can be placed
            } else {
                toPlace.put(placementRequest, completeModuleList);
            }
        }
        return f;
    }

    @Override
    protected void mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();

        int placementCompleteCount = 0;
        while (placementCompleteCount < placementRequests.size()) {
            if (toPlace.isEmpty()) {
                // Update toPlace and placementCompleteCount
                placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
            }

            // Simon says for now all the microservices go in one big list
            List<String> microservices = new ArrayList<>();
            for (List<String> valueList : toPlace.values()) {
                microservices.addAll(valueList);
            }

            List<DeviceState> deviceStates = new ArrayList<>();
            for (Map.Entry<Integer, Map<String, Double>> entry : resourceAvailability.entrySet()) {
                deviceStates.add(new DeviceState(entry.getKey(), entry.getValue()));
            }

            Map<AppModule, Integer> placements = new HashMap<>(); // service -> deviceId

            // Initialize loads to 0
//            resourceAvailability.keySet().forEach(deviceId -> {
//                getCurrentCpuLoad().put(deviceId, 0.0);
//                getCurrentRamLoad().put(deviceId, 0.0);
//            });

            // Try to place each service
            for (String s : microservices) {
                Application app = applicationInfo.get(moduleToApp.get(s));
                AppModule service = getModule(s, app);
                Collections.sort(deviceStates);

                boolean placed = false;
                for (DeviceState deviceState : deviceStates) {
                    if (deviceState.canFit(service.getMips(), service.getRam())) {
                        // Update device state
                        deviceState.allocate(service.getMips(), service.getRam());

                        // TODO Simon says here is where we update all the relevant state
                        placements.put(service, deviceState.deviceId);
                        getCurrentCpuLoad().put(deviceState.deviceId,
                                getCurrentCpuLoad().get(deviceState.deviceId) + service.getMips());
                        getCurrentRamLoad().put(deviceState.deviceId,
                                getCurrentRamLoad().get(deviceState.deviceId) + service.getRam());

                        placed = true;
                        break;
                    }
                }

                if (!placed) {
                    // TODO Simon says what do we do when failure?
                }
            }
        }
    }

    //    public Placement schedule() {
//
//        // Not using latency here at all
//        // double[][] networkLatency = getNetworkLatency();
//
//        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();
//        int placementCompleteCount = 0;
//        if (toPlace.isEmpty()) {
//            // Update toPlace and placementCompleteCount
//            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
//        }
//
//        List<FogDevice> nodes = new ArrayList<>(this.fogDevices);
//
//        // create the placement list
//        int[] placement = new int[this.getFunctions().length];
//        for (int i = 0; i < placement.length; i++)
//            placement[i] = -1;
//
//        for (int i = 0; i < this.getFunctions().length; i++) {
//            Collections.sort(nodes);
//
//            for (int j = 0; j < nodes.size(); j++) {
//                if (nodes.get(j).canFit(this.getFunctions()[i].getCpuRequirement(),	this.getFunctions()[i].getRamRequirement())) {
//                    nodes.get(j).update(this.getFunctions()[i].getCpuRequirement(),	this.getFunctions()[i].getRamRequirement());
//                    placement[i] = (int) nodes.get(j).getContact().getNodeID().value();
//                    break;
//                }
//            }
//        }
//
//        for (int i = 0; i < placement.length; i++) {
//            if (placement[i] == -1) {
//                placement = null;
//                break;
//            }
//        }
//
//        Placement solution = new Placement(placement);
//        return solution;
//    }
    // Class to track resource state during placement decisions
    private static class DeviceState implements Comparable<DeviceState> {
        private final Integer deviceId;
        private final Map<String, Double> remainingResources;

        public DeviceState(Integer deviceId, Map<String, Double> initialResources) {
            this.deviceId = deviceId;
            this.remainingResources = new HashMap<>(initialResources);
        }

        public boolean canFit(double cpuReq, double ramReq) {
            return remainingResources.get("cpu") >= cpuReq &&
                    remainingResources.get("ram") >= ramReq;
        }

        public void allocate(double cpuReq, double ramReq) {
            remainingResources.put("cpu", remainingResources.get("cpu") - cpuReq);
            remainingResources.put("ram", remainingResources.get("ram") - ramReq);
        }

        @Override
        public int compareTo(DeviceState other) {
            // Sort by available CPU, then by RAM if CPU is equal
            int cpuCompare = Double.compare(
                    this.remainingResources.get("cpu"),
                    other.remainingResources.get("cpu")
            );
            if (cpuCompare != 0) return cpuCompare;

            return Double.compare(
                    this.remainingResources.get("ram"),
                    other.remainingResources.get("ram")
            );
        }
    }
}




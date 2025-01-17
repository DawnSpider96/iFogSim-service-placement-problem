package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;

import java.util.*;

public class MyBestFitHeuristic extends MyHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */
    public MyBestFitHeuristic(int fonID) {
        super(fonID);
    }

    private List<DeviceState> deviceStates = new ArrayList<>();

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
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
        }

        // Simon says for now all the microservices go in one big map.
        // Simon (15015) says this is now unnecessary. We will hence use toPlace normally.
        // appId -> microservices to place (can be multiple)
//        // TODO Verify that the microservices are in order! (Because they are ordered in Simonstrator)
//        Map<String, Map<PlacementRequest, List<String>>> microservices = new HashMap<>();
//        for (Map.Entry<PlacementRequest, List<String>> entry : toPlace.entrySet()) {
//            String applicationId = entry.getKey().getApplicationId();
//            List<String> valueList = entry.getValue();
//            microservices.computeIfAbsent(applicationId, k -> new HashMap<>());
//            microservices.get(applicationId).put(entry.getKey(), valueList);
//        }

//        Set<Integer> deviceIdsToInclude = new HashSet<>();
//        for (FogDevice fogDevice : fogDevices) {
//            MyFogDevice mfd = (MyFogDevice) fogDevice;
//            if (Objects.equals(mfd.getDeviceType(), MyFogDevice.FCN)) {
//                deviceIdsToInclude.add(mfd.getId());
//            }
//        }
        deviceStates = new ArrayList<>();
        for (FogDevice fogDevice : availableFogDevices) {
            deviceStates.add(new DeviceState(fogDevice.getId(), resourceAvailability.get(fogDevice.getId())));
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
            int status = tryPlacingOnePr(microservices, app, placementRequest);
            prStatus.put(placementRequest, status);
        }
        return prStatus;
    }

    @Override
    protected int tryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {
        // Initialize temporary state
//        final double[] cpusum = new double[1];
//        Consumer<Double> addToCpu = (value) -> cpusum[0] += value;
//        final int[] ramsum = new int[1];
//        Consumer<Integer> addToRam = (value) -> ramsum[0] += value;
//        final long[] storagesum = new long[1];
//        Consumer<Long> addToStorage = (value) -> storagesum[0] += value;
        Map<String, Integer> placed = new HashMap<>();
        for (String microservice : microservices) {
            placed.put(microservice, -1);
        }
//        Consumer<String> markAsPlaced = microservice -> {
//            if (placed.containsKey(microservice)) {
//                placed.put(microservice, 1);
//            }
//        };

        for (String s : microservices) {
            AppModule service = getModule(s, app);
            Collections.sort(deviceStates);

            for (int i = 0; i < deviceStates.size(); i++) {
                // Try to place
                if (deviceStates.get(i).canFit(service.getMips(), service.getRam())) {

                    deviceStates.get(i).allocate(service.getMips(), service.getRam());
//                    addToCpu.accept(getModule(microservice, app).getMips());
//                    addToRam.accept(getModule(microservice, app).getRam());
//                    addToStorage.accept(getModule(microservice, app).getSize());
//                    markAsPlaced.accept(microservice);

                    // Update temporary state
                    placed.put(s, deviceStates.get(i).deviceId);

//                    tryPlacingAllHelper(microservice, device, app, markAsPlaced, addToCpu, addToRam, addToStorage, placementRequest.getPlacementRequestId());
                    break;
                }
            }

            if (placed.get(s) < 0) {
                // todo Simon says what do we do when failure?
                //  (160125) Nothing. Because (aggregated) failure will be determined outside the for loop
                System.out.println("Failed to place module " + s + "on PR " + placementRequest.getPlacementRequestId());
                System.out.println("Failed placement " + placementRequest.getPlacementRequestId());

                // Undo every "placement" recorded in placed. Only deviceStates was changed, so we change it back
                for (String microservice : placed.keySet()) {
                    int deviceId = placed.get(microservice);
                    if (deviceId != -1) {
                        DeviceState targetDeviceState = null;
                        for (DeviceState deviceState : deviceStates) {
                            if (deviceState.deviceId == deviceId) {
                                targetDeviceState = deviceState;
                                break;
                            }
                        }
                        assert targetDeviceState != null;
                        AppModule placedService = getModule(microservice, app);
                        targetDeviceState.deallocate(placedService.getMips(), placedService.getRam());
                    }
                }
                break;
            }
        }

        boolean allPlaced = placed.values().stream().allMatch(value -> value > -1);

        if (allPlaced) {
            for (String s: placed.keySet()) {
                AppModule service = getModule(s, app);
                int deviceId = placed.get(s);

//                DeviceState targetDeviceState = null;
//                for (DeviceState deviceState : deviceStates) {
//                    if (deviceState.deviceId == deviceId) {
//                        targetDeviceState = deviceState;
//                        break;
//                    }
//                }
//                assert targetDeviceState != null;
//                targetDeviceState.allocate(service.getMips(), service.getRam());

                Logger.debug("ModulePlacementEdgeward", "Placement of operator " + s + " on device " + CloudSim.getEntityName(deviceId) + " successful.");
                System.out.println("Placement of operator " + s + " on device " + CloudSim.getEntityName(deviceId) + " successful.");

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(placementRequest.getPlacementRequestId()).put(s, deviceId);

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
            Logger.error("Control Flow Error", "The program should not reach this code. See allPlaced and (placed.get(s) < 0).");
        }
//        placements.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
//        placements.get(entry.getKey()).put(service, deviceState.deviceId);
//        getCurrentCpuLoad().put(deviceState.deviceId,
//                getCurrentCpuLoad().get(deviceState.deviceId) + service.getMips());
//        getCurrentRamLoad().put(deviceState.deviceId,
//                getCurrentRamLoad().get(deviceState.deviceId) + service.getRam());

        if (allPlaced) return -1;
        else return getFonID();
    }


    @Override
    protected Map<PlacementRequest, Integer> determineTargets(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice) {
        // Simon says that ALL the parent edge servers of the users that made PRs must receive deployments. Otherwise, error.
        Map<PlacementRequest, Integer> targets = new HashMap<>();
        for (PlacementRequest pr : placementRequests) {
            Application app = applicationInfo.get(pr.getApplicationId());
            // Simon says we want one target per second microservice in the PR's application
            // If there are no second microservices, targeted is true
            boolean targeted = true;
            for (String secondMicroservice : FogBroker.getApplicationToSecondMicroservicesMap().get(app)) {
                for (Map.Entry<String, Integer> entry : pr.getPlacedMicroservices().entrySet()) {
                    if (Objects.equals(entry.getKey(), secondMicroservice)) {
                        targets.put(pr, entry.getValue());
                        targeted = true;
                        break;
                    }
                    targeted = false;
                }
            }

            if (!targeted) {
                Logger.error("Deployment Error", "Cannot find target device for " + pr.getPlacementRequestId() + ". Check the placement of its first microservice.");
            }
        }
        return targets;

    }




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

        public void deallocate(double cpuReq, double ramReq) {
            remainingResources.put("cpu", remainingResources.get("cpu") + cpuReq);
            remainingResources.put("ram", remainingResources.get("ram") + ramReq);
        }

        @Override
        public int compareTo(DeviceState other) {
            // Sort by available CPU, then by RAM if CPU is equal, from biggest to smallest
            int cpuCompare = Double.compare(
                    other.remainingResources.get("cpu"), // Biggest first
                    this.remainingResources.get("cpu")
            );
            if (cpuCompare != 0) return cpuCompare;

            return Double.compare(
                    other.remainingResources.get("ram"), // Biggest first
                    this.remainingResources.get("ram")
            );
        }
    }
}




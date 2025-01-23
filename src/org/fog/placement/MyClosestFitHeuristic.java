package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;
import org.fog.utils.MicroservicePlacementConfig;
import org.fog.utils.ModuleLaunchConfig;

import java.util.*;

public class MyClosestFitHeuristic extends MyHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */
    public MyClosestFitHeuristic(int fonID) {
        super(fonID);
    }

    private int cloudIndex = -1;
    // Maps DeviceId to index (on latencies and `fogDevices` state)
    private Map<Integer, Integer> indices;
    private double [][] globalLatencies;

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
            Set<String> alreadyPlaced = mappedMicroservices.get(placementRequest.getPlacementRequestId()).keySet();
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

        int length = fogDevices.size();
        indices = new HashMap<>();
        for (int i=0 ; i<fogDevices.size() ; i++) {
            indices.put(fogDevices.get(i).getId(), i);
            // While we're at it, determine cloud's index
            if (fogDevices.get(i).getName() == "cloud") cloudIndex = i;
        }
        if(cloudIndex <0) {
            Logger.error("Control Flow Error", "Cloud index should have value.");
        }
        globalLatencies = fillGlobalLatencies(length, indices);

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
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

    private double[][] fillGlobalLatencies(int length, Map<Integer, Integer> indices) {
        // Simon (210125) says this matrix is missing information:
        // latencies[cloud][children] have values, but latencies[children][cloud] are -1
        // For any int device, latencies[device][device] should be 0 but are currently -1
        double[][] latencies = new double[length][length];
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (i==j) latencies[i][j] = 0.0;
                else latencies[i][j] = -1.0; // Initialize
            }
        }

        // Centralised, flower-shaped topology
        // Only latencies from cloud to edge are included
        for (Map.Entry<Integer, Double> entry : fogDevices.get(cloudIndex).getChildToLatencyMap().entrySet()) {
            latencies[cloudIndex][indices.get(entry.getKey())] = entry.getValue();
        }

        return latencies;
    }


    @Override
    protected int tryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {

        // Simon says the closest node changes with every PR
        // Hence `nodes` needs to be remade repeatedly
        List<RelativeLatencyFogDevice> nodes = new ArrayList<>();
        FogDevice closestFogDevice = getDevice(closestNodes.get(placementRequest));

        for (FogDevice fogDevice : edgeFogDevices) {
            nodes.add(new RelativeLatencyFogDevice(fogDevice, closestFogDevice, globalLatencies));
        }

        Collections.sort(nodes);

        // Initialize temporary state
        Map<String, Integer> placed = new HashMap<>();
        for (String microservice : microservices) {
            placed.put(microservice, -1);
        }


        for (String s : microservices) {
            AppModule service = getModule(s, app);

            for (int i = 0; i < nodes.size(); i++) {
                int deviceId = nodes.get(i).fogDevice.getId();
                // Try to place
                if (canFit(s, deviceId, app)) {

                    // Update temporary state
                    allocate(deviceId, service.getMips(), service.getRam(), service.getSize());
                    placed.put(s, deviceId);
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
                        RelativeLatencyFogDevice targetNode = null;
                        for (RelativeLatencyFogDevice node: nodes) {
                            if (node.fogDevice.getId() == deviceId) {
                                targetNode = node;
                                break;
                            }
                        }
                        assert targetNode != null;
                        AppModule placedService = getModule(microservice, app);
                        deallocate(targetNode.fogDevice.getId(), placedService.getMips(), placedService.getRam(), placedService.getSize());
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

    private void allocate(int deviceId, double mips, int ram, long size) {
        getCurrentCpuLoad().put(deviceId, mips + getCurrentCpuLoad().get(deviceId));
        getCurrentRamLoad().put(deviceId, ram + getCurrentRamLoad().get(deviceId));
        getCurrentStorageLoad().put(deviceId, size + getCurrentStorageLoad().get(deviceId));
    }

    private void deallocate(int deviceId, double mips, int ram, long size) {
        getCurrentCpuLoad().put(deviceId, mips - getCurrentCpuLoad().get(deviceId));
        getCurrentRamLoad().put(deviceId, ram - getCurrentRamLoad().get(deviceId));
        getCurrentStorageLoad().put(deviceId, size - getCurrentStorageLoad().get(deviceId));
    }


    /**
     * Queries FogBroker to obtain the name(s) of second Microservice(s) in the AppLoop
     * Iterates through all Placement Requests, using them to extract target for the second Microservice(s)
     * State that can be used:
     *   - List<PlacementRequest> placementRequests:    This has the completed placement target IDs.
     *   - Map<PlacementRequest, Integer> closestNodes
     *  - Map<Integer, Application> applicationInfo
     * @param perDevice     Actually not very needed. Contains details of exactly how many module instance requests
     *                      were sent to each device. Includes the module instances themselves.
     * @return Map of each PR to the deviceId that the FogBroker will inform to begin execution
     * */
    @Override
    protected Map<PlacementRequest, Integer> determineTargets(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice) {
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



    class RelativeLatencyFogDevice implements Comparable<RelativeLatencyFogDevice> {

        FogDevice fogDevice;
        Double latencyToClosestHost;
        FogDevice closestEdgeNode;
        double[][] globalLatencies;

        RelativeLatencyFogDevice(FogDevice fogDevice, FogDevice closestEdgeNode,
                                 double[][] globalLatencies) {

            this.fogDevice = fogDevice;
            this.closestEdgeNode = closestEdgeNode;
            this.globalLatencies = globalLatencies;

            // Simon says this is just our FogDevices state
            // TODO All references to this will use the state instead
//            this.allEdgeServers = allEdgeServers;

            // if the same node
            if (fogDevice == closestEdgeNode) {
                latencyToClosestHost = 0.0;
            } else {
                // calculate latency depending on topology
                switch (MicroservicePlacementConfig.NETWORK_TOPOLOGY) {
                    case MicroservicePlacementConfig.CENTRALISED:
                        latencyToClosestHost = centralisedPlacementLatency();
                        break;

                    case MicroservicePlacementConfig.FEDERATED:
                        latencyToClosestHost = federatedPlacementLatency();
                        break;

                    case MicroservicePlacementConfig.DECENTRALISED:
                        latencyToClosestHost = decentralisedPlacementLatency();
                        break;
                }
            }
        }

        private Double centralisedPlacementLatency() {
            // Assuming a star network topology where every edge node is
            // connected via cloud
            int closestEdgeNodeIndex = indices.get(closestEdgeNode.getId());
            int fogDeviceIndex = indices.get(fogDevice.getId());

            if (closestEdgeNodeIndex<0 || fogDeviceIndex<0) {
                Logger.error("Value Error", "Global Latencies not appropriately filled.");
            }

            Double closestEdgeNodeToCloudLatency = this.globalLatencies[cloudIndex][closestEdgeNodeIndex];
            Double fogDeviceToCloudLatency = this.globalLatencies[cloudIndex][fogDeviceIndex];

            return fogDeviceToCloudLatency + closestEdgeNodeToCloudLatency;
        }

        private Double federatedPlacementLatency() {
            Logger.error("Control Flow Error", "Topology cannot possibly be Federated.");
            return -1.0;
            // TODO If I ever work on this topology, understand that this involves FONs. The Simonstrator equivalent is MecSystem class,
            //  a data class used to encapsulate FON network information:
            //  Leader id, member ids, latencies.
            //  But here, only leader id is needed. iFogSim already has that information IN THE FOGDEVICES.

//            int cloudIndex = 0;
//            int closestEdgeNodeIndex = closestEdgeNode.getId();
//            int fogDeviceIndex = fogDevice.getId();
//
//            Long closestEdgeNodeLeader = Util.getLeader(closestEdgeNode.getContact().getNodeID().value(),
//                    this.mecSystem);
//            Long fogDeviceLeader = Util.getLeader(fogDevice.getContact().getNodeID().value(), this.mecSystem);
//
//            int closestEdgeNodeLeaderIndex = this.allEdgeServers.get(closestEdgeNodeLeader).getId();
//            int fogDeviceLeaderIndex = this.allEdgeServers.get(fogDeviceLeader).getId();
//
//            Double closestToLeaderLatency = this.globalLatencies[closestEdgeNodeLeaderIndex][closestEdgeNodeIndex];
//            Double thisEdgeToLeaderLatency = this.globalLatencies[fogDeviceLeaderIndex][fogDeviceIndex];
//
//            // If under the same leader
//            if (closestEdgeNodeLeader == fogDeviceLeader) {
//
//                return closestToLeaderLatency + thisEdgeToLeaderLatency;
//
//                // under different leader involves cloud
//            } else {
//
//                Double closestLeaderToCloudLatency = this.globalLatencies[cloudIndex][closestEdgeNodeLeaderIndex];
//                Double thisEdgeLeaderToCloudLatency = this.globalLatencies[cloudIndex][fogDeviceLeaderIndex];
//
//                return closestToLeaderLatency + thisEdgeToLeaderLatency + thisEdgeLeaderToCloudLatency
//                        + closestLeaderToCloudLatency;
//
//            }
        }

        private Double decentralisedPlacementLatency() {
            Logger.error("Control Flow Error", "Topology cannot possibly be Decentralised.");
            return -1.0;
            // Assuming that every edge node has a direct connection to each
            // other

//            int closestEdgeNodeIndex = this.allEdgeServers.get(closestEdgeNode.getContact().getNodeID().value()).getId();
//            int fogDeviceIndex = this.allEdgeServers.get(fogDevice.getContact().getNodeID().value()).getId();
//
//            return this.globalLatencies[fogDeviceIndex][closestEdgeNodeIndex];

        }

        @Override
        public int compareTo(RelativeLatencyFogDevice other) {
            try {
                if (this.latencyToClosestHost < other.latencyToClosestHost) {
                    return -1;
                } else if (this.latencyToClosestHost > other.latencyToClosestHost) {
                    return 1;

                } else {
                    return 0;
                }
            } catch (Exception ex) {
                Logger.error("An error occurred", ex.getMessage());
                return 0;
            }
        }
    }
}




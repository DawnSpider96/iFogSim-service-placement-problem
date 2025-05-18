package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.entities.ContextPlacementRequest;
import org.fog.utils.Logger;

import java.util.*;

public class SimulatedAnnealingHeuristic extends SPPHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "SA";
    }

    /**
     * Fog network related details
     */
    public SimulatedAnnealingHeuristic(int fonID) {
        super(fonID);
    }

    private SortedMap<Integer, DeviceState> deviceStateMap = new TreeMap<>();
    List<DeviceState> baseStates;
    // For quick lookup with id as key
    private Map<Integer, FogDevice> deviceIdMap = new LinkedHashMap<>();
    Map<Integer,Integer> devToIdx = new LinkedHashMap<>();
    private Map<String, Double> latencyCache = new LinkedHashMap<>();

    // Simulated Annealing parameters
    private static double temperature = 1000;
    private static double coolingFactor = 0.995;


    @Override
    public void postProcessing() {
    }

    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new LinkedHashMap<>();

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
        }

        // In the algorithm itself, copies of these DeviceStates will be made.
        //  This initialisation occurs only once, capturing the state of resourceAvailability (and fogDevices) at this point in time
        // However, everytime a placement is made (for one PR), DeviceStates will be updated.
        deviceStateMap = new TreeMap<>();
        deviceIdMap = new LinkedHashMap<>();
        for (FogDevice fogDevice : edgeFogDevices) {
            // SHARED object in both states
            DeviceState deviceState = new DeviceState(
                    fogDevice.getId(),
                    resourceAvailability.get(fogDevice.getId()),
                    fogDevice.getHost().getTotalMips(),
                    fogDevice.getHost().getRam(),
                    fogDevice.getHost().getStorage()
            );
            deviceStateMap.put(fogDevice.getId(), deviceState);
            deviceIdMap.put(fogDevice.getId(), fogDevice);
        }
        // For indexability.
        baseStates = new ArrayList<>(deviceStateMap.values());
        for (int i = 0; i < baseStates.size(); i++) {
            devToIdx.put(baseStates.get(i).getId(), i);
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

    private String getLatencyCacheKey(int deviceId, int closestNodeId) {
        return deviceId + "-" + closestNodeId;
    }

    /**
     * Calculates cumulative latency of all placements to the users closest host
     *
     * @param placement
     * @return
     */
    private double placementLatencySum(int[] placement, FogDevice closestNode) {
        double totalLatency = 0.0;
        int closestNodeId = closestNode.getId();

        for (int j = 0; j < placement.length; j++) {
            int deviceId = placement[j];
            if (deviceId == -1) {
                throw new NullPointerException("Placement should be populated!");
            }

            String cacheKey = getLatencyCacheKey(deviceId, closestNodeId);
            // Check if latency is in cache
            Double cachedLatency = latencyCache.get(cacheKey);

            if (cachedLatency != null) {
                totalLatency += cachedLatency;
            } else {
                FogDevice device = deviceIdMap.get(deviceId);
                if (device != null) {
                    RelativeLatencyDeviceState relativeEdgeNode = new RelativeLatencyDeviceState(
                            device,
                            closestNode,
                            this.globalLatencies
                    );
                    latencyCache.put(cacheKey, relativeEdgeNode.latencyToClosestHost);
                    totalLatency += relativeEdgeNode.latencyToClosestHost;
                }
            }
        }

        return totalLatency;
    }

    public static double probabilityOfAcceptance(double currentLatency, double neighborLatency, double temp) {
        // neighbour is smaller, we accept always
        if (neighborLatency < currentLatency)
            return 1;
        // else we might accept neighbour depending on the difference with
        // existing solution and temperature
        return Math.exp((currentLatency - neighborLatency) / temp);
    }

    /**
     * Quickly checks if placement is possible at all before running expensive SA algorithm
     * Returns true if placement might be feasible, false if definitely not feasible
     */
    private boolean isPlacementFeasible(List<String> services, Application app) {
        boolean firstFitSuccessful = true;
        List<DeviceState> nodesBestPlacement = new ArrayList<>(baseStates);
        for (int i = 0; i < services.size(); i++) {
            boolean placedThisService = false;
            for (int j = 0; j < nodesBestPlacement.size(); j++) {
                AppModule service = getModule(services.get(i), app);
                // Ensure that DeviceState j is a PRIVATE, DEEP copy
                if (nodesBestPlacement.get(j) == baseStates.get(j)) {
                    nodesBestPlacement.set(j, new DeviceState(baseStates.get(j)));
                }
                if (nodesBestPlacement.get(j).canFit(service.getMips(), service.getRam(), service.getSize())) {
                    nodesBestPlacement.get(j).allocate(service.getMips(), service.getRam(), service.getSize());
                    placedThisService = true;
                    break;
                }
            }
            if (!placedThisService) {
                firstFitSuccessful = false;
                break;
            }
        }
        return firstFitSuccessful;
    }

    @Override
    protected List<DeviceState> getCurrentDeviceStates() {
        // For metrics collection
        // Definitely ordered according to deviceId
        return new ArrayList<>(deviceStateMap.values());
    }

    @Override
    protected int doTryPlacingOnePr(List<String> services, Application app, PlacementRequest placementRequest) {

//        if (!isPlacementFeasible(services, app)) {
//            Logger.debug("Simulated Annealing Placement Problem", "Early termination - no feasible placement exists");
//            return getFonID();
//        }

        FogDevice closestFogDevice = getDevice(closestNodes.get(placementRequest));

        // create empty placement list
        int[] placements = new int[services.size()];
        Arrays.fill(placements, -1);

        // duplicates to not use original values
        int[] bestPlacement = placements.clone();
        List<DeviceState> nodesBestPlacement = new ArrayList<>(baseStates);

        // use FirstFit for an initial "best" placement generation
        boolean firstFitSuccessful = true;
        for (int i = 0; i < services.size(); i++) {
            boolean placedThisService = false;
            for (int j = 0; j < nodesBestPlacement.size(); j++) {
                AppModule service = getModule(services.get(i), app);
                if (nodesBestPlacement.get(j).canFit(service.getMips(), service.getRam(), service.getSize())) {
                    // Ensure that DeviceState j is a PRIVATE, DEEP copy
                    if (nodesBestPlacement.get(j) == baseStates.get(j)) {
                        nodesBestPlacement.set(j, new DeviceState(baseStates.get(j)));
                    }
                    nodesBestPlacement.get(j).allocate(service.getMips(), service.getRam(), service.getSize());
                    bestPlacement[i] = nodesBestPlacement.get(j).getId();
                    placedThisService = true;
                    break;
                }
            }
            if (!placedThisService) {
                firstFitSuccessful = false;
                break;
            }
        }

        // If first fit failed, no need to run SA
        if (!firstFitSuccessful) {
            Logger.debug("Simulated Annealing Placement Problem", "Early termination - no feasible placement exists");
            return getFonID();
        }

        // Current placement is also the "best" so far
        int[] currentPlacement = bestPlacement.clone();

        // iterate while reducing temperature by a cooling factor (step)
        for (double t = temperature; t > 1; t *= coolingFactor) {
            int[] neighbourPlacement = new int[services.size()];
            Arrays.fill(neighbourPlacement, -1);
            // also need a SHALLOW copy of nodes as we would update these as we book resources
            // NOTE We take a copy of the ORIGINAL device states
            List<DeviceState> nodesNeighborPlacement = new ArrayList<>(baseStates);
            boolean placementStillPossible = true;

            // for each service find a random node with sufficient ram and cpu
            for (int i = 0; i < services.size(); i++) {
                // pre-select only fitting nodes for random selection
                List<DeviceState> onlyFittingNodesSubset = new ArrayList<DeviceState>();
                AppModule service = getModule(services.get(i), app);

                for (DeviceState node : nodesNeighborPlacement) {
                    if (node.canFit(service.getMips(), service.getRam(), service.getSize())) {
                        onlyFittingNodesSubset.add(node);
                    }
                }

                // if no candidates was found set placement to -1
                if (onlyFittingNodesSubset.isEmpty()) {
                    placementStillPossible = false;
                    break; // Do nothing, already -1
                } else {
                    // get random fitting node - use seeded random instead of Math.random()
                    int j = (int) (onlyFittingNodesSubset.size() * getRandom().nextDouble());
                    int j_Idx = devToIdx.get(onlyFittingNodesSubset.get(j).getId());
                    // check if DeviceState at j_Inx is ALREADY a private copy
                    //  (private copy would have been made if a previous service was placed on DeviceState j)
                    if (nodesNeighborPlacement.get(j_Idx) == baseStates.get(j_Idx)) {
                        // DeviceState j is not private, so make a private DEEP COPY
                        nodesNeighborPlacement.set(
                                j_Idx,
                                new DeviceState(baseStates.get(j_Idx))
                        );
                    }
                    // NOTE DeviceState j will definitely be in onlyFittingNodesSubset
                    // nodesNeighbourPlacement will disappear after the SA iteration,
                    //  However we still allocate to prevent overallocation WITHIN the iteration.
                    nodesNeighborPlacement.get(j_Idx).allocate(service.getMips(), service.getRam(), service.getSize());
                    neighbourPlacement[i] = onlyFittingNodesSubset.get(j).getId();
                }
            }

            if (!placementStillPossible) {
//                Logger.error(
//                        "Simulation Limitation Problem",
//                        "All DeviceStates have no resources for ONE of the services! Check the biggest one."
//                );
                continue;
                // rationale: At least one of the values in the placement is -1
                //  So there is no point continuing with this SA iteration.
            }
            double currentLatency = placementLatencySum(currentPlacement, closestFogDevice);
            double neighborLatency = placementLatencySum(neighbourPlacement, closestFogDevice);

            if (getRandom().nextDouble() < probabilityOfAcceptance(currentLatency, neighborLatency, t)) {
                currentPlacement = neighbourPlacement.clone();
                currentLatency = neighborLatency;
            }

            // if solution is the best then put it aside
            double bestLatency = placementLatencySum(bestPlacement, closestFogDevice);
            if (currentLatency < bestLatency) {
                bestPlacement = currentPlacement.clone();
            }
        }

        // At the end, bestPlacement is the final placement
        int[] placed = bestPlacement.clone();

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
            
            for (int i = 0 ; i < services.size(); i++) {
                String s = services.get(i);
                AppModule service = getModule(s, app);
                int deviceId = bestPlacement[i];

                System.out.printf("Placement of operator %s on device %s successful. Device id: %d, sensorId: %d, prIndex: %d%n",
                        s,
                        CloudSim.getEntityName(deviceId),
                        deviceId,
                        placementRequest.getSensorId(),
                        ((ContextPlacementRequest) placementRequest).getPrIndex());

                deviceStateMap.get(deviceId).allocate(service.getMips(), service.getRam(), service.getSize());

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
            // Simulated Annealing is an "All or nothing" placement strategy.
            //  If one service fails to place, all cannot be placed, hence state is unchanged.
            Logger.debug("Simulated Annealing Placement Failure", "But temporary state not affected");
        }

        if (allPlaced) return -1;
        else return getFonID();
    }
}




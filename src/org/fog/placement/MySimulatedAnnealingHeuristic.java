package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.entities.MyPlacementRequest;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MySimulatedAnnealingHeuristic extends MyHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "SA";
    }

    /**
     * Fog network related details
     */
    public MySimulatedAnnealingHeuristic(int fonID) {
        super(fonID);
    }

    private SortedMap<Integer, DeviceState> deviceStateMap = new TreeMap<>();
    List<DeviceState> baseStates;
    // For quick lookup with id as key
    private Map<Integer, FogDevice> deviceIdMap = new HashMap<>();
    private Map<String, Double> latencyCache = new HashMap<>();

    // Simulated Annealing parameters
    private static double temperature = 1000;
    private static double coolingFactor = 0.995;


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

        // Simon says in the algorithm itself, copies of these DeviceStates will be made.
        //  This initialisation occurs only once, capturing the state of resourceAvailability (and fogDevices) at this point in time
        // However, everytime a placement is made (for one PR), DeviceStates will be updated.
        deviceStateMap = new TreeMap<>();
        deviceIdMap = new HashMap<>();
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

    /**
     * Makes a copy of DeviceStates. DeviceStates is updated after every placement.
     *.
     * @return
     */
    private List<DeviceState> getEdgeNodesListClone(List<DeviceState> nodes) {
        List<DeviceState> listClone = new ArrayList<>();
        for (DeviceState server : nodes) {
            listClone.add(new DeviceState(server));
        }
        return listClone;
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
    private boolean isPlacementFeasible(List<String> microservices, Application app) {
        // For each microservice, check if there's at least one device that can host it
        for (String microservice : microservices) {
            AppModule service = getModule(microservice, app);
            boolean canBePlaced = false;

            // Check if at least one device has enough resources
            for (DeviceState deviceState : deviceStateMap.values()) {
                if (deviceState.canFit(service.getMips(), service.getRam(), service.getSize())) {
                    canBePlaced = true;
                    break;
                }
            }

            // If this microservice can't be placed anywhere, the whole placement fails
            if (!canBePlaced) {
                return false;
            }
        }

        // If we get here, each microservice has at least one potential host
        return true;
    }

    @Override
    protected List<DeviceState> getCurrentDeviceStates() {
        // Use the deviceStateMap for efficiency
        // Definitely ordered according to deviceId
        return new ArrayList<>(deviceStateMap.values());
    }

    @Override
    protected int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {

        if (!isPlacementFeasible(microservices, app)) {
            Logger.debug("Simulated Annealing Placement Problem", "Early termination - no feasible placement exists");
            return getFonID();
        }

        FogDevice closestFogDevice = getDevice(closestNodes.get(placementRequest));

        // create empty placement list
        int[] placements = new int[microservices.size()];
        Arrays.fill(placements, -1);

        // duplicates to not use original values
        int[] bestPlacement = placements.clone();
        List<DeviceState> nodesBestPlacement = getEdgeNodesListClone(baseStates);

        // use FirstFit for an initial "best" placement generation
        boolean firstFitSuccessful = true;
        for (int i = 0; i < microservices.size(); i++) {
            boolean placedThisService = false;
            for (int j = 0; j < nodesBestPlacement.size(); j++) {
                AppModule service = getModule(microservices.get(i), app);
                if (nodesBestPlacement.get(j).canFit(service.getMips(), service.getRam(), service.getSize())) {
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

        // If FirstFit failed, no need to run SA
        if (!firstFitSuccessful) {
            Logger.debug("Simulated Annealing Placement", "First-fit initialization failed - skipping SA");
            return getFonID();
        }

        // Current placement is also the "best" so far
        int[] currentPlacement = bestPlacement.clone();

        // iterate while reducing temperature by a cooling factor (step)
        for (double t = temperature; t > 1; t *= coolingFactor) {
            int[] neighbourPlacement = currentPlacement.clone();
            // also need a copy of nodes as we would update these as we book resources
            // NOTE We take a copy of the ORIGINAL device states
            List<DeviceState> nodesNeighborPlacement = getEdgeNodesListClone(baseStates);

            // for each service find a random node with sufficient ram and cpu
            for (int i = 0; i < microservices.size(); i++) {
                // pre-select only fitting nodes for random selection
                List<DeviceState> onlyFittingNodesSubset = new ArrayList<DeviceState>();
                AppModule service = getModule(microservices.get(i), app);

                for (DeviceState node : nodesNeighborPlacement) {
                    if (node.canFit(service.getMips(), service.getRam(), service.getSize())) {
                        onlyFittingNodesSubset.add(node);
                    }
                }

                // if no candidates was found set placement to -1
                if (onlyFittingNodesSubset.isEmpty()) {
                    neighbourPlacement[i] = -1;
                } else {
                    // get random fitting node
                    int j = (int) (onlyFittingNodesSubset.size() * Math.random());
                    // Update the node resources
                    onlyFittingNodesSubset.get(j).allocate(service.getMips(), service.getRam(), service.getSize());
                    // add to placement
                    neighbourPlacement[i] = onlyFittingNodesSubset.get(j).getId();
                }
            }

            double currentLatency = placementLatencySum(currentPlacement, closestFogDevice);
            double neighborLatency = placementLatencySum(neighbourPlacement, closestFogDevice);

            if (Math.random() < probabilityOfAcceptance(currentLatency, neighborLatency, t)) {
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
                ((MyPlacementRequest)placementRequest).getPrIndex()
            );
            
            // Ensure the key exists in mappedMicroservices
            if (!mappedMicroservices.containsKey(prKey)) {
                mappedMicroservices.put(prKey, new LinkedHashMap<>());
            }
            
            for (int i = 0 ; i < microservices.size(); i++) {
                String s = microservices.get(i);
                AppModule service = getModule(s, app);
                int deviceId = bestPlacement[i];

                System.out.printf("Placement of operator %s on device %s successful. Device id: %d, sensorId: %d, prIndex: %d%n",
                        s,
                        CloudSim.getEntityName(deviceId),
                        deviceId,
                        placementRequest.getSensorId(),
                        ((MyPlacementRequest) placementRequest).getPrIndex());

                deviceStateMap.get(deviceId).allocate(service.getMips(), service.getRam(), service.getSize());

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
            // Simon says Simulated Annealing is an "All or nothing" placement strategy.
            //  If one service fails to place, all cannot be placed, hence state is unchanged.
            Logger.debug("Simulated Annealing Placement Failure", "But temporary state not affected");
        }

        if (allPlaced) return -1;
        else return getFonID();
    }
}




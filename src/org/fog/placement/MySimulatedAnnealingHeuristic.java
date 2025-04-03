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

    private List<DeviceState> DeviceStates = new ArrayList<>();

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
            Set<String> alreadyPlaced = mappedMicroservices.get(placementRequest.getSensorId()).keySet();
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
        // This initialisation occurs only once,
        // capturing the state of resourceAvailability (and fogDevices) at this point in time
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
            // -1 if success, cloudId if failure
            // Cloud will resend to itself
            // Type int for flexibility: In more complex simulations there may be more FON heads, not just the cloud.
            int status = tryPlacingOnePr(microservices, app, placementRequest);
            prStatus.put(placementRequest, status);
        }
        return prStatus;
    }

    /**
     * Calculates cumulative latency of all placements to the users closest host
     *
     * @param placement
     * @return
     */
    private double placementLatencySum(int[] placement, FogDevice closestNode) {

        List<FogDevice> servers = this.edgeFogDevices;
        double totalLatency = 0.0;
        // create list with calculated user relative latency
        for (int j = 0; j < placement.length; j++) {
            for (int i = 0; i < servers.size(); i++) {

                if (servers.get(i).getId() == placement[j]) {

                    RelativeLatencyDeviceState relativeEdgeNode = new RelativeLatencyDeviceState(servers.get(i), closestNode, this.globalLatencies);

                    totalLatency = totalLatency + relativeEdgeNode.latencyToClosestHost;
                    break;
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


    @Override
    protected int tryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {
        
        FogDevice closestFogDevice = getDevice(closestNodes.get(placementRequest));

        // create empty placement list
        int[] placements = new int[microservices.size()];
        // initialise to null
        for (int i = 0; i < placements.length; i++) {
            placements[i] = -1;
        }

        // duplicate to not use original values
        int[] bestPlacement = placements.clone();
        List<DeviceState> nodesBestPlacement = getEdgeNodesListClone(DeviceStates);

        // use FirstFit for an initial "best" placement generation
        for (int i = 0; i < microservices.size(); i++) {
            for (int j = 0; j < nodesBestPlacement.size(); j++) {
                AppModule service = getModule(microservices.get(i), app);
                if (nodesBestPlacement.get(j).canFit(service.getMips(), service.getRam(), service.getSize())) {
                    nodesBestPlacement.get(j).allocate(service.getMips(), service.getRam(), service.getSize());
                    bestPlacement[i] = nodesBestPlacement.get(j).getId();
                    break;
                }
            }
        }

        // Current placement is also the "best" so far
        int[] currentPlacement = bestPlacement.clone();

        // iterate while reducing temperature by a cooling factor (step)
        for (double t = temperature; t > 1; t *= coolingFactor) {
            int[] neighbourPlacement = currentPlacement.clone();
            // also need a copy of nodes as we would update these as we book
            // resources
            List<DeviceState> nodesNeighborPlacement = getEdgeNodesListClone(DeviceStates);

            // for each service function find a random node with ram and cpu
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
                if (onlyFittingNodesSubset.size() < 1) {
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
            for (int i = 0 ; i < microservices.size(); i++) {
                String s = microservices.get(i);
                AppModule service = getModule(s, app);
                int deviceId = placed[i];

                Logger.debug("ModulePlacementEdgeward", "Placement of operator " + s + " on device " + CloudSim.getEntityName(deviceId) + " successful.");
                System.out.println("Placement of operator " + s + " on device " + CloudSim.getEntityName(deviceId) + " successful.");

                // DeviceStates will go into future ACOHelper objects
                // Then all the "copy" DeviceStates will contain the updated resource information
                for (DeviceState d : DeviceStates) {
                    if (d.getId() == deviceId) {
                        d.allocate(service.getMips(), service.getRam(), service.getSize());
                    }
                }

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(placementRequest.getSensorId()).put(s, deviceId);

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
//        placements.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
//        placements.get(entry.getKey()).put(service, deviceState.deviceId);
//        getCurrentCpuLoad().put(deviceState.deviceId,
//                getCurrentCpuLoad().get(deviceState.deviceId) + service.getMips());
//        getCurrentRamLoad().put(deviceState.deviceId,
//                getCurrentRamLoad().get(deviceState.deviceId) + service.getRam());

        if (allPlaced) return -1;
        else return getFonID();
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
                Logger.error("SimulatedAnnealing Deployment Error", "Cannot find target device for " + pr.getSensorId() + ". Check the placement of its first microservice.");
            }
        }
        return targets;
    }
}




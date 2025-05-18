package org.fog.placement;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.utils.Logger;
import org.fog.utils.MicroservicePlacementConfig;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.SPPMonitor;
import org.fog.utils.MetricUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.Objects;

public abstract class SPPHeuristic implements MicroservicePlacementLogic {
    public abstract String getName();
    /**
     * Fog network related details
     */
    protected List<FogDevice> fogDevices; // ALL fog devices in the network
    protected List<FogDevice> edgeFogDevices = new ArrayList<>(); // Fog devices in the network that are in consideration for placement
    protected List<PlacementRequest> placementRequests; // requests to be processed
    protected Map<PlacementRequestKey, PlacementRequest> placementRequestMap; // Map for efficient lookup
    protected Map<Integer, Map<String, Double>> resourceAvailability;
    protected Map<String, Application> applicationInfo = new LinkedHashMap<>(); // map app name to Application
    protected Map<String, String> moduleToApp = new LinkedHashMap<>();
    protected Map<PlacementRequest, Integer> closestNodes = new LinkedHashMap<>();

    int fonID;

    // Maps DeviceId to index (on latencies and `fogDevices` state)
    protected Map<Integer, Integer> indices;
    protected int cloudIndex = -1;
    protected int cloudId = -1;
    protected double [][] globalLatencies;

    // Temporary State
    protected Map<Integer, Double> currentCpuLoad;
    protected Map<Integer, Double> currentRamLoad;
    protected Map<Integer, Double> currentStorageLoad;
    protected Map<Integer, List<String>> currentModuleMap = new LinkedHashMap<>();
    protected Map<Integer, Map<String, Double>> currentModuleLoadMap = new LinkedHashMap<>();
    protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum = new LinkedHashMap<>();

    // For quick lookup in getModule method
    private final Map<String, Map<String, AppModule>> moduleCache = new LinkedHashMap<>();

    // Composite key class for placement requests
    // sensorId (sensor that created PR), prIndex (index of PR made by this sensor)
    protected static class PlacementRequestKey {
        private final int sensorId;
        private final int prIndex;
        
        public PlacementRequestKey(int sensorId, int prIndex) {
            this.sensorId = sensorId;
            this.prIndex = prIndex;
        }
        
        public int getSensorId() {
            return sensorId;
        }
        
        public int getPrIndex() {
            return prIndex;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlacementRequestKey that = (PlacementRequestKey) o;
            return sensorId == that.sensorId && prIndex == that.prIndex;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(sensorId, prIndex);
        }
    }
    
    // Modified to use composite key but maintain LinkedHashMap as the outer structure
    LinkedHashMap<PlacementRequestKey, LinkedHashMap<String, Integer>> mappedMicroservices = new LinkedHashMap<>();

    // Add a seed field and a random generator
    protected long seed = 33;
    protected Random random = new Random(seed);

    public SPPHeuristic(int fonID) {
        setFONId(fonID);
    }

    public void setFONId(int id) {
        fonID = id;
    }

    public int getFonID() {
        return fonID;
    }

    @Override/**/
    public PlacementLogicOutput run(List<FogDevice> fogDevices, Map<String, Application> applicationInfo, Map<Integer, Map<String, Double>> resourceAvailability, List<PlacementRequest> prs) {
        resetTemporaryState(fogDevices, applicationInfo, resourceAvailability, prs);
        Map<PlacementRequest, Integer> prStatus = mapModules();
        PlacementLogicOutput placement = generatePlacementDecision(prStatus);
        updateResources(resourceAvailability);
        postProcessing();
        return placement;
    }

//    @Override
//    public void postProcessing() {    }

    public Map<PlacementRequest, Integer> mapPlacedAndSpecialModules(List <PlacementRequest> prs) {
        // Note the edge servers that sent the PRs
        Map<PlacementRequest, Integer> closestNodes = new HashMap<>();

        for (PlacementRequest placementRequest : prs) {
            closestNodes.put(placementRequest, getDevice(placementRequest.getRequester()).getParentId());

            // Create a composite key for this placement request
            PlacementRequestKey prKey = new PlacementRequestKey(
                placementRequest.getSensorId(), 
                ((ContextPlacementRequest)placementRequest).getPrIndex()
            );

            // already placed modules
            mappedMicroservices.put(prKey, new LinkedHashMap<>(placementRequest.getPlacedServices()));

            //special modules  - predefined cloud placements
            Application app =  applicationInfo.get(placementRequest.getApplicationId());
            for (String microservice : app.getSpecialPlacementInfo().keySet()) {
                for (String deviceName : app.getSpecialPlacementInfo().get(microservice)) {
                    FogDevice device = getDeviceByName(deviceName);
                    tryPlacingMicroserviceNoAggregate(microservice, device, app, m->{}, prKey);
                }
            }
        }
        return closestNodes;
    }

    protected boolean canFit(String microservice, int deviceId, Application app) {
        return (getModule(microservice, app).getMips() + getCurrentCpuLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.CPU)
                && getModule(microservice, app).getRam() + getCurrentRamLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.RAM)
                && getModule(microservice, app).getSize() + getCurrentStorageLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.STORAGE));
    }


    /**
     * State updated:
     *  - mappedMicroservices
     *  - moduleToApp
     *  - currentModuleMap
     *  - currentModuleLoadMap
     *  -
     * @return A map reflecting the updated entries after cleaning.
     */
    protected void tryPlacingMicroserviceNoAggregate(String microservice, FogDevice device, Application app, Consumer<String> onPlaced, PlacementRequestKey prKey) {
        int deviceId = device.getId();

        if (canFit(microservice, deviceId, app)) {
            Logger.debug("ModulePlacementEdgeward", "Placement of operator " + microservice + " on device " + device.getName() + " successful.");
            getCurrentCpuLoad().put(deviceId, getModule(microservice, app).getMips() + getCurrentCpuLoad().get(deviceId));
            getCurrentRamLoad().put(deviceId, getModule(microservice, app).getRam() + getCurrentRamLoad().get(deviceId));
            getCurrentStorageLoad().put(deviceId, getModule(microservice, app).getSize() + getCurrentStorageLoad().get(deviceId));
            System.out.println("Placement of operator " + microservice + " on device " + device.getName() + " successful.");

            moduleToApp.put(microservice, app.getAppId());

            if (!currentModuleMap.get(deviceId).contains(microservice))
                currentModuleMap.get(deviceId).add(microservice);

            mappedMicroservices.get(prKey).put(microservice, deviceId);

            //currentModuleLoad
            if (!currentModuleLoadMap.get(deviceId).containsKey(microservice))
                currentModuleLoadMap.get(deviceId).put(microservice, getModule(microservice, app).getMips());
            else
                currentModuleLoadMap.get(deviceId).put(microservice, getModule(microservice, app).getMips() + currentModuleLoadMap.get(deviceId).get(microservice));

            //currentModuleInstance
            if (!currentModuleInstanceNum.get(deviceId).containsKey(microservice))
                currentModuleInstanceNum.get(deviceId).put(microservice, 1);
            else
                currentModuleInstanceNum.get(deviceId).put(microservice, currentModuleInstanceNum.get(deviceId).get(microservice) + 1);

            onPlaced.accept(microservice);
        }
    }

    /**
     * Prunes mappedMicroservices and updates placementRequests such that their entries reflect the modules placed in THIS cycle only.
     * This function should ONLY be called when placementRequests and mappedMicroservices do NOT contain matching entries.
     *
     * @param placementCompleteCount Current number of placements that have successfully completed.
     *                               To be updated in this function.
     * @param toPlace An empty/incomplete map of PlacementRequest to the list of Microservices (String) that require placement.
     *                CPU and RAM requirements of each Microservice can be obtained with getModule() method.
     * @see #getModule
     * @param placementRequests this.placementRequests, ie the list of all PlacementRequest objects
     * @return A map reflecting the updated entries after cleaning.
     */
    protected int fillToPlace(int placementCompleteCount, Map<PlacementRequest, List<String>> toPlace, List<PlacementRequest> placementRequests) {
        int f = placementCompleteCount;
        
        // Sort placementRequests by a deterministic key to ensure consistent processing order
        List<PlacementRequest> sortedRequests = new ArrayList<>(placementRequests);
        sortedRequests.sort((pr1, pr2) -> {
            ContextPlacementRequest mpr1 = (ContextPlacementRequest) pr1;
            ContextPlacementRequest mpr2 = (ContextPlacementRequest) pr2;
            
            // First compare by sensorId
            int sensorCompare = Integer.compare(mpr1.getSensorId(), mpr2.getSensorId());
            if (sensorCompare != 0) return sensorCompare;
            
            // Then by prIndex if sensorIds are equal
            return Integer.compare(mpr1.getPrIndex(), mpr2.getPrIndex());
        });
        
        for (PlacementRequest placementRequest : sortedRequests) {
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

    protected List<String> getNextLayerOfModulesToPlace(Set<String> placedModules, Application app) {
        List<String> modulesToPlace_1 = new ArrayList<String>();
        List<String> modulesToPlace = new ArrayList<String>();
        for (AppModule module : app.getModules()) {
            if (!placedModules.contains(module.getName()))
                modulesToPlace_1.add(module.getName());
        }
        /*
         * Filtering based on whether modules (to be placed) lower in physical topology are already placed
         */
        for (String moduleName : modulesToPlace_1) {
            boolean toBePlaced = true;

            for (AppEdge edge : app.getEdges()) {
                //CHECK IF OUTGOING DOWN EDGES ARE PLACED
                if (edge.getSource().equals(moduleName) && edge.getDirection() == Tuple.DOWN && !placedModules.contains(edge.getDestination()))
                    toBePlaced = false;
                //CHECK IF INCOMING UP EDGES ARE PLACED
                if (edge.getDestination().equals(moduleName) && edge.getDirection() == Tuple.UP && !placedModules.contains(edge.getSource()))
                    toBePlaced = false;
            }
            if (toBePlaced)
                modulesToPlace.add(moduleName);
        }
        return modulesToPlace;
    }

    // Returns the ENTIRE list of Modules to place for ONE placement request
    // Output is one list, which is a bit awkward
    //  if the AppLoop belonging to the PR has non-linear structure
    protected List<String> getAllModulesToPlace(Set<String> placedModules, Application app) {
        List<String> modulesToPlace = new ArrayList<>();
        Queue<String> toCheck = new LinkedList<>();

        // Start with the initial list of modules that can be placed

        toCheck.addAll(getNextLayerOfModulesToPlace(placedModules, app));

        while (!toCheck.isEmpty()) {
            String currentModule = toCheck.poll();
            if (!modulesToPlace.contains(currentModule)) {
                modulesToPlace.add(currentModule);
                // Add the current module to the 'placed' set temporarily to check further dependencies
                placedModules.add(currentModule);

                // Get next level of modules based on new 'placed' status
                List<String> nextModules = getNextLayerOfModulesToPlace(placedModules, app);
                for (String nextModule : nextModules) {
                    if (!modulesToPlace.contains(nextModule)) {
                        toCheck.add(nextModule);
                    }
                }
            }
        }
        return modulesToPlace;
    }

    protected abstract Map<PlacementRequest, Integer> mapModules();

    protected void resetTemporaryState(List<FogDevice> fogDevices, Map<String, Application> applicationInfo, Map<Integer, Map<String, Double>> resourceAvailability, List<PlacementRequest> prs){
        this.fogDevices = fogDevices;

        Set<Integer> deviceIdsToInclude = new HashSet<>();
        for (FogDevice fogDevice : fogDevices) {
            SPPFogDevice mfd = (SPPFogDevice) fogDevice;
            if (Objects.equals(mfd.getDeviceType(), SPPFogDevice.FCN)) {
                deviceIdsToInclude.add(mfd.getId());
            }
        }
        edgeFogDevices = new ArrayList<>();
        // There is already filtering for ALL FCNs in MyMicroservicesController.getResourceInfo.
        // TODO consider making additional functionality here to query AVAILABLE FCNs,
        //  instead of all FCNs.
        //  I believe these can be safely stored into edgeFogDevices because it is used in
        //  mapModules as the full pool of Fog Nodes in consideration for placement.
        // Problem is currently we don't have the concept of FCNs rendering themsevles unavailable.
        for (FogDevice fogDevice : this.fogDevices) {
            if (deviceIdsToInclude.contains(fogDevice.getId())) {
                edgeFogDevices.add(fogDevice);
            }
        }

        this.placementRequests = prs;
        this.resourceAvailability = resourceAvailability;
        this.applicationInfo = applicationInfo;
        this.mappedMicroservices = new LinkedHashMap<>();
        this.closestNodes = mapPlacedAndSpecialModules(placementRequests);

        setCurrentCpuLoad(new LinkedHashMap<Integer, Double>());
        setCurrentRamLoad(new LinkedHashMap<Integer, Double>());
        setCurrentStorageLoad(new LinkedHashMap<Integer, Double>());
        setCurrentModuleMap(new LinkedHashMap<>());
        for (FogDevice dev : fogDevices) {
            int id = dev.getId();
            getCurrentCpuLoad().put(id, 0.0);
            getCurrentRamLoad().put(id, 0.0);
            getCurrentStorageLoad().put(id, 0.0);
            getCurrentModuleMap().put(id, new ArrayList<>());
            currentModuleLoadMap.put(id, new LinkedHashMap<String, Double>());
            currentModuleInstanceNum.put(id, new LinkedHashMap<String, Integer>());
        }

        indices = new LinkedHashMap<>();
        for (int i=0 ; i<fogDevices.size() ; i++) {
            indices.put(fogDevices.get(i).getId(), i);
            // While we're at it, determine cloud's index
            if (Objects.equals(fogDevices.get(i).getName(), "cloud")) {
                cloudIndex = i;
                cloudId = fogDevices.get(i).getId();
            }
        }
        globalLatencies = fillGlobalLatencies(fogDevices.size(), indices);

        // Initialize placementRequestMap for efficient lookups
        placementRequestMap = new LinkedHashMap<>();
        for (PlacementRequest pr : prs) {
            ContextPlacementRequest myPr = (ContextPlacementRequest) pr;
            PlacementRequestKey key = new PlacementRequestKey(myPr.getSensorId(), myPr.getPrIndex());
            placementRequestMap.put(key, myPr);
        }
    }

    protected double[][] fillGlobalLatencies(int length, Map<Integer, Integer> indices) {
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
            latencies[indices.get(entry.getKey())][cloudIndex] = entry.getValue();
        }

        return latencies;
    }

    @Override
    public void updateResources(Map<Integer, Map<String, Double>> resourceAvailability) {
        for (int deviceId : currentModuleInstanceNum.keySet()) {
            Map<String, Integer> moduleCount = currentModuleInstanceNum.get(deviceId);
            for (String moduleName : moduleCount.keySet()) {
                Application app = applicationInfo.get(moduleToApp.get(moduleName));
                AppModule module = getModule(moduleName, app);
                double mips = resourceAvailability.get(deviceId).get(ControllerComponent.CPU) - (module.getMips() * moduleCount.get(moduleName));
                resourceAvailability.get(deviceId).put(ControllerComponent.CPU, mips);
                double ram = resourceAvailability.get(deviceId).get(ControllerComponent.RAM) - (module.getRam() * moduleCount.get(moduleName));
                resourceAvailability.get(deviceId).put(ControllerComponent.RAM, ram);
                double storage = resourceAvailability.get(deviceId).get(ControllerComponent.STORAGE) - (module.getSize() * moduleCount.get(moduleName));
                resourceAvailability.get(deviceId).put(ControllerComponent.STORAGE, storage);
            }
        }

        // Update resource consumption metrics
        //  currentModuleInstanceNum contains deviceID -> module name (String) -> instance count (int)
        //  Total resources are found in PowerHost of FogDevice: fogDevice.getHost().getTotalMips()
//        List<DeviceState> snapshots = new ArrayList<>();
//        for (FogDevice fd : edgeFogDevices) {
//            int deviceId = fd.getId();
//            snapshots.add(new DeviceState(deviceId, resourceAvailability.get(deviceId),
//                    fd.getHost().getTotalMips(), fd.getHost().getRam(), fd.getHost().getStorage()));
//        }
//        MyMonitor.getInstance().getSnapshots().put(CloudSim.clock(), snapshots);
        // FogBroker.getBatchNumber()

    }

    protected void captureResourceMetricsAfterSuccessfulPlacement(
            PlacementRequest pr,
            List<DeviceState> currentDeviceStates,
            double timestamp) {

        double utilization = MetricUtils.computeResourceUtilisation(currentDeviceStates);

        // Store utilization temporarily instead of recording immediately
        SPPMonitor.getInstance().storeUtilizationForPR(pr, utilization);
    }

    /**
     * Template method for placing a PR and capturing metrics
     * Child classes implement doTryPlacingOnePr with their placement logic
     */
    protected final int processOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {
        int result = doTryPlacingOnePr(microservices, app, placementRequest);
        if (result == -1) {
            List<DeviceState> currentStates = getCurrentDeviceStates();
            captureResourceMetricsAfterSuccessfulPlacement(placementRequest, currentStates, CloudSim.clock());
        }
        return result;
    }

    protected abstract int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest);

    protected abstract List<DeviceState> getCurrentDeviceStates();

    /**
     * Prunes mappedMicroservices and updates placementRequests such that their entries reflect the modules placed in THIS cycle only.
     * This function should ONLY be called when placementRequests and mappedMicroservices do NOT contain matching entries.
     *
     * @param placementRequests A list of outdated (only containing initial modules) placement requests.
     *                          Entries will be added to the `placedMicroservices` field according to entries from `mappedMicroservices`.
     *                          End result: placedMicroservices field contains ALL microservices
     * @param mappedMicroservices A map of service IDs to their corresponding microservice details.
     *                            Entries that match the outdated placement requests will be removed.
     *                            End result: Entries will
     * @return A map reflecting the updated entries after cleaning. Should have NO failed PRs.
     * PlacementRequestKey -> Map(microservice name -> target deviceId)
     */
    protected Map<PlacementRequestKey, Map<String, Integer>> cleanPlacementRequests(List<PlacementRequest> placementRequests,
                                                                                    LinkedHashMap<PlacementRequestKey, LinkedHashMap<String, Integer>> mappedMicroservices,
                                                                                    Map<PlacementRequest, Integer> prStatus) {
        /*
        Returns HashMap containing all the services placed IN THIS CYCLE to each device
        */
        MicroservicePlacementConfig.FAILURE_REASON failureReason = MicroservicePlacementConfig.FAILURE_REASON.PLACEMENT_FAILED;
        Map<PlacementRequestKey, Map<String, Integer>> placement = new LinkedHashMap<>();
        
        // Collect Total PRs metric. Failed PRs metric collected below.
        SPPMonitor.getInstance().recordTotalPRs(placementRequests.size(), CloudSim.clock());

        for (PlacementRequest pr : placementRequests) {
            if (prStatus.get(pr) == -1) {
                ContextPlacementRequest placementRequest = (ContextPlacementRequest) pr;
                // Create a key for this placement request
                PlacementRequestKey prKey = new PlacementRequestKey(
                        placementRequest.getSensorId(),
                        placementRequest.getPrIndex()
                );

                // Skip if this placement request has no entries in mappedMicroservices
                if (!mappedMicroservices.containsKey(prKey)) {
                    continue;
                }

                List<String> toRemove = new ArrayList<>();
                // placement should include newly placed ones
                for (String microservice : mappedMicroservices.get(prKey).keySet()) {
                    if (placementRequest.getPlacedServices().containsKey(microservice))
                        toRemove.add(microservice);
                    else
                        placementRequest.getPlacedServices().put(microservice, mappedMicroservices.get(prKey).get(microservice));
                }
                for (String microservice : toRemove)
                    mappedMicroservices.get(prKey).remove(microservice);

                // Update PR to shift first module (always clientModule) to last place
                // For metric collecting purposes
                Map.Entry<String, Integer> clientModuleEntry = placementRequest.getPlacedServices().entrySet().iterator().next();
                placementRequest.getPlacedServices().remove(clientModuleEntry.getKey());
                placementRequest.getPlacedServices().put(clientModuleEntry.getKey(), clientModuleEntry.getValue());
                
                // Calculate latency and record both metrics together
                double latency = determineLatencyOfDecision(placementRequest);
                SPPMonitor.getInstance().recordMetricsForPR(placementRequest, CloudSim.clock(), latency);

                // Update output - now keyed by PlacementRequestKey rather than just sensorId
                placement.put(prKey, mappedMicroservices.get(prKey));
            }
            else {
                // Otherwise, do collection of failed PR metrics
                Logger.error("PR failed because ", failureReason
                        + ", sensorId " + pr.getSensorId()
                        + ", prIndex " + ((ContextPlacementRequest) pr).getPrIndex());
                SPPMonitor.getInstance().recordFailedPR(pr, failureReason);
            }
        }
        return placement;
    }

    /**
     * State queried: applicationInfo, currentModuleInstanceNum
     *
     * @param prStatus Map of placement requests to their result. Was outputted by MapModules()
     *
     * @return Placement Decision containing:
     *  - perDevice
     *  - perDevice
     *  - ServiceDiscovery
     *  - prStatus
     *  - Targets
     */
    protected ContextAwarePlacement generatePlacementDecision(Map<PlacementRequest, Integer> prStatus) {
        // placements: PlacementRequestKey -> Map(microservice name -> target deviceId)
        Map<PlacementRequestKey, Map<String, Integer>> placements = cleanPlacementRequests(placementRequests, mappedMicroservices, prStatus);

        //todo it assumed that modules are not shared among applications.
        // <deviceid, < app, list of modules to deploy > this is to remove deploying same module more than once on a certain device.
        Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice = new LinkedHashMap<>();
        Map<Integer, List<PRContextAwareEntry>> serviceDiscoveryInfo = new LinkedHashMap<>();
        if (placements != null) {
            for (PlacementRequestKey prKey : placements.keySet()) {
                // placementRequestMap is for O(1) lookup
                PlacementRequest placementRequest = placementRequestMap.get(prKey);
                
                if (placementRequest == null) {
                    Logger.error("PlacementRequest query Error", "Could not find placement request for sensorId: " + prKey.getSensorId() + ", prIndex: " + prKey.getPrIndex());
                    continue;
                }
                
                ContextPlacementRequest contextPlacementRequest = (ContextPlacementRequest) placementRequest;
                int sensorId = contextPlacementRequest.getSensorId();
                int prIndex = contextPlacementRequest.getPrIndex();

                // Check if placementRequest was a failure.
                if (prStatus.containsKey(placementRequest) && prStatus.get(placementRequest) != -1) {
                    throw new NullPointerException("There should be no failed PR here");
                    // If mappedMicroservices state doesn't reflect that it was a failure, we have discrepancy.
//                    if (!mappedMicroservices.containsKey(prKey) || !mappedMicroservices.get(prKey).isEmpty()) {
//                        // Only log if we actually have mappings for this placement request
//                        if (mappedMicroservices.containsKey(prKey) && !mappedMicroservices.get(prKey).isEmpty()) {
//                            // Build a string representation of all placed microservices
//                            StringBuilder placedEntries = new StringBuilder();
//                            for (Map.Entry<String, Integer> entry : mappedMicroservices.get(prKey).entrySet()) {
//                                placedEntries.append("\n    - ")
//                                    .append(entry.getKey())
//                                    .append(" -> Device ")
//                                    .append(entry.getValue())
//                                    .append(" (")
//                                    .append(CloudSim.getEntityName(entry.getValue()))
//                                    .append(")");
//                            }
//
//                            Logger.error("Service Discovery Integrity Error",
//                                    "Found mappedMicroservices entry for PR " + sensorId + ", prIndex " + prIndex +
//                                    ", but prStatus indicates failure (status: " + prStatus.get(placementRequest) +
//                                    "). This suggests a mismatch between placement success status and service discovery entries." +
//                                    "\nPlaced microservices:" + placedEntries.toString());
//                        }
//                    }
//                    continue;
                }

                Application application = applicationInfo.get(placementRequest.getApplicationId());

                for (String microserviceName : placements.get(prKey).keySet()) {
                    int deviceID = placements.get(prKey).get(microserviceName);

                    // Get client devices that need this service discovery info
                    List<Integer> clientDevices = getClientServiceNodeIds(application,
                            microserviceName,
                            placementRequest.getPlacedServices(),
                            placements.get(prKey));

                    for (int clientDevice : clientDevices) {
                        PRContextAwareEntry entry = new PRContextAwareEntry(
                                microserviceName,
                                deviceID,
                                placementRequest.getSensorId(),
                                ((ContextPlacementRequest)placementRequest).getPrIndex()
                        );
                        if (serviceDiscoveryInfo.containsKey(clientDevice)) {
                            serviceDiscoveryInfo.get(clientDevice).add(entry);
                        } else {
                            List<PRContextAwareEntry> entries = new ArrayList<>();
                            entries.add(entry);
                            serviceDiscoveryInfo.put(clientDevice, entries);
                        }
                    }
                }
            }

            //todo module is created new here check if this is needed
            for (int deviceId : currentModuleInstanceNum.keySet()) {
                for (String microservice : currentModuleInstanceNum.get(deviceId).keySet()) {
                    Application application = applicationInfo.get(moduleToApp.get(microservice));
                    AppModule appModule = new AppModule(application.getModuleByName(microservice));
                    ModuleLaunchConfig moduleLaunchConfig = new ModuleLaunchConfig(appModule, currentModuleInstanceNum.get(deviceId).get(microservice));
                    if (perDevice.keySet().contains(deviceId)) {
                        if (perDevice.get(deviceId).containsKey(application)) {
                            perDevice.get(deviceId).get(application).add(moduleLaunchConfig);
                        } else {
                            List<ModuleLaunchConfig> l = new ArrayList<>();
                            l.add(moduleLaunchConfig);
                            perDevice.get(deviceId).put(application, l);
                        }
                    } else {
                        List<ModuleLaunchConfig> l = new ArrayList<>();
                        l.add(moduleLaunchConfig);
                        HashMap<Application, List<ModuleLaunchConfig>> m = new HashMap<>();
                        m.put(application, l);
                        perDevice.put(deviceId, m);
                    }
                }
            }
        }

        Map<PlacementRequest, Integer> targets = determineTargets(perDevice, prStatus);

        return new ContextAwarePlacement(perDevice, serviceDiscoveryInfo, prStatus, targets);
    }


    /**
    * State that can be used:
    *   - List<PlacementRequest> placementRequests
    *   - Map<PlacementRequest, Integer> closestNodes
    *  - Map<Integer, Application> applicationInfo
    * @param perDevice     Actually not very needed. Contains details of exactly how many module instance requests
    *                      were sent to each device. Includes the module instances themselves.
    * @param prStatus      Map of placement requests to their status (-1 for success, deviceId for failure)
    * @return Map of each PR to the deviceId that the FogBroker will inform to begin execution
    * */
    protected Map<PlacementRequest, Integer> determineTargets(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice, Map<PlacementRequest, Integer> prStatus) {
        Map<PlacementRequest, Integer> targets = new LinkedHashMap<>();
        for (PlacementRequest pr : placementRequests) {
            // Skip failed placement requests
            if (prStatus.containsKey(pr) && prStatus.get(pr) != -1) {
                continue;
            }
            
            Application app = applicationInfo.get(pr.getApplicationId());
            // We want one target per second microservice in the PR's application
            // If there are no second microservices, targeted is true
            boolean targeted = true;
            for (String secondMicroservice : FogBroker.getApplicationToSecondServicesMap().get(app)) {
                for (Map.Entry<String, Integer> entry : pr.getPlacedServices().entrySet()) {
                    if (Objects.equals(entry.getKey(), secondMicroservice)) {
                        targets.put(pr, entry.getValue());
                        targeted = true;
                        break;
                    }
                    targeted = false;
                }
            }

            if (!targeted) {
                Logger.error(this.getName() + " Deployment Error", "Cannot find target device for " + pr.getSensorId() + ". Check the placement of its first microservice.");
            }
        }
        return targets;
    }

    /**
    * Legacy method for backward compatibility
    * @param perDevice Contains module instance requests
    * @return Map of each PR to target deviceId
    * @deprecated Use determineTargets(perDevice, prStatus) instead
    */
    protected Map<PlacementRequest, Integer> determineTargets(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice) {
        // Create an empty prStatus map to pass to the new method
        Map<PlacementRequest, Integer> emptyPrStatus = new LinkedHashMap<>();
        // Mark all PRs as successful by default
        for (PlacementRequest pr : placementRequests) {
            emptyPrStatus.put(pr, -1);
        }
        return determineTargets(perDevice, emptyPrStatus);
    }

    /**
     * Gets the client service node IDs for a microservice. This includes looking up from already placed
     * microservices and newly placed microservices in the current cycle.
     * 
     * @param application The application to which the microservice belongs
     * @param microservice The microservice for which client service nodes are needed
     * @param placed Already placed microservices (from the PR)
     * @param placementPerPr Newly placed microservices from the current placement cycle
     * @return List of device IDs where client services are placed
     */
    protected List<Integer> getClientServiceNodeIds(Application application, String
            microservice, Map<String, Integer> placed, Map<String, Integer> placementPerPr) {
        List<String> clientServices = getClientServices(application, microservice);
        List<Integer> nodeIDs = new LinkedList<>();
        for (String clientService : clientServices) {
            if (placed.get(clientService) != null)
                nodeIDs.add(placed.get(clientService));
            else if (placementPerPr.get(clientService) != null)
                nodeIDs.add(placementPerPr.get(clientService));
        }
        return nodeIDs;
    }


    protected List<String> getClientServices(Application application, String microservice) {
        List<String> clientServices = new LinkedList<>();

        for (AppEdge edge : application.getEdges()) {
            if (edge.getDestination().equals(microservice) && edge.getDirection() == Tuple.UP)
                clientServices.add(edge.getSource());
        }
        return clientServices;
    }




    protected FogDevice getDeviceByName(String deviceName) {
        for (FogDevice f : fogDevices) {
            if (f.getName().equals(deviceName))
                return f;
        }
        return null;
    }

    public Map<Integer, Double> getCurrentCpuLoad() {
        return currentCpuLoad;
    }

    public Map<Integer, Double> getCurrentRamLoad() {
        return currentRamLoad;
    }

    public Map<Integer, Double> getCurrentStorageLoad() {
        return currentStorageLoad;
    }

    public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
        this.currentCpuLoad = currentCpuLoad;
    }

    public void setCurrentRamLoad(Map<Integer, Double> currentRamLoad) {
        this.currentRamLoad = currentRamLoad;
    }

    public void setCurrentStorageLoad(Map<Integer, Double> currentStorageLoad) {
        this.currentStorageLoad = currentStorageLoad;
    }

    public Map<Integer, List<String>> getCurrentModuleMap() {
        return currentModuleMap;
    }

    public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
        this.currentModuleMap = currentModuleMap;
    }

    /**
     * Given a microservice (AppModule) name, returns the actual AppModule object.
     * Used for querying the resource requirements of a microservice.
     *
     * @param moduleName Name (String) of the microservice
     * @param app Application that the microservice belongs to
     * @return The relevant appModule belonging to `app` with the name `moduleName`
     */
    protected AppModule getModule(String moduleName, Application app) {
        Map<String, AppModule> appModules = moduleCache.computeIfAbsent(
                app.getAppId(), k -> new HashMap<>()
        );
        if (appModules.containsKey(moduleName)) return appModules.get(moduleName);
        AppModule module = app.getModuleByName(moduleName);
        if (module != null) {
            appModules.put(moduleName, module);
        }
        else {
            throw new NullPointerException("Module not found");
        }
        return module;
    }

    protected FogDevice getDevice(int deviceId) {
        for (FogDevice fogDevice : fogDevices) {
            if (fogDevice.getId() == deviceId)
                return fogDevice;
        }
        return null;
    }


    // INCLUDES FINAL JOURNEY back to user
    private double determineLatencyOfDecision(ContextPlacementRequest pr) {
        // We are currently NOT considering execution time
        // Hence only placement targets are considered
        // placedMicroservices are ordered.
        double latency = 0;
        List<Integer> edges = new ArrayList<>(pr.getPlacedServices().values());


        // Check if there are at least two devices to calculate latency
        if (edges.size() > 1) {
            // For 1 service scenario, variance in latency comes from 3 places:
            //  - Latency between cloud->firstEdge
            //  - Latency of final journey:
            //      - firstEdge->cloud (return for routing to gateway)
            //      - cloud->gateway
            //      - gateway->user (most variance I assume)
            latency += pr.getRequestLatency();
//            latency += determineLatency(cloudId, edges.get(0));
            for (int i = 0; i < edges.size() - 2; i++) { // We DONT count the last journey
                int sourceDevice = edges.get(i);
                int destDevice = edges.get(i + 1);
                latency += determineLatency(sourceDevice, destDevice);
            }
        }
        return latency;
    }

    private double determineLatency(int srcId, int destId) {
        /*
        Destination MyFogDevice may be a user. Source MyFogDevice is always edge server.
         */

        if (srcId==destId) return 0.0;

        int srcIndex = indices.get(srcId);
        int destIndex = indices.get(destId);

        double l = globalLatencies[srcIndex][destIndex];
        if (l >= 0) return l;

        if (MicroservicePlacementConfig.NETWORK_TOPOLOGY != MicroservicePlacementConfig.CENTRALISED) throw new NullPointerException("Wrong topology.");
        SPPFogDevice src = (SPPFogDevice) getDevice(srcId);
        SPPFogDevice dest = (SPPFogDevice) getDevice(destId);


        if (Objects.equals(src.getDeviceType(), SPPFogDevice.FCN) && Objects.equals(dest.getDeviceType(), SPPFogDevice.FCN)){
            return globalLatencies[srcIndex][cloudIndex] + globalLatencies[cloudIndex][destIndex];
        }
        else { // dest is user device
            throw new NullPointerException("We don't calculate latency with users anymore");
//            if (!dest.isUserDevice()) {
//                throw new IllegalArgumentException("Destination device must be a user device");
//            }
//
//            int parentId = dest.getParentId();
//            if (parentId == srcId) return dest.getUplinkLatency();
//
//            int parentIndex = indices.get(parentId);
//            // Uplinklatency in milliseconds
//            // BUT fogDevice constructor converts it to seconds (simulation timestep unit)
//            return globalLatencies[srcIndex][cloudIndex] + globalLatencies[cloudIndex][parentIndex] + dest.getUplinkLatency();
        }
    }

//    // Class to track resource state during placement decisions
//    public static class DeviceState implements Comparable<DeviceState>{
//        private final Integer deviceId;
//        private final Map<String, Double> remainingResources;
//        private final Map<String, Double> totalResources;
//
//        public DeviceState(Integer deviceId, Map<String, Double> initialResources, double mips, double ram, double storage) {
//            this.deviceId = deviceId;
//            this.remainingResources = new LinkedHashMap<>(initialResources);
//            this.totalResources = new LinkedHashMap<>();
//            this.totalResources.put("cpu", mips);
//            this.totalResources.put("ram", ram);
//            this.totalResources.put("storage", storage);
//        }
//
//        public DeviceState(DeviceState other) {
//            this.deviceId = other.deviceId;
//            this.remainingResources = new LinkedHashMap<>(other.remainingResources);
//            this.totalResources = new LinkedHashMap<>(other.totalResources);
//        }
//
//
//        public boolean canFit(double cpuReq, double ramReq, double storageReq) {
//            return remainingResources.get("cpu") >= cpuReq &&
//                    remainingResources.get("ram") >= ramReq &&
//                    remainingResources.get("storage") >= storageReq;
//        }
//
//        public void allocate(double cpuReq, double ramReq, double storageReq) {
//            remainingResources.put("cpu", remainingResources.get("cpu") - cpuReq);
//            remainingResources.put("ram", remainingResources.get("ram") - ramReq);
//            remainingResources.put("storage", remainingResources.get("storage") - storageReq);
//        }
//
//        public void deallocate(double cpuReq, double ramReq, double storageReq) {
//            remainingResources.put("cpu", remainingResources.get("cpu") + cpuReq);
//            remainingResources.put("ram", remainingResources.get("ram") + ramReq);
//            remainingResources.put("storage", remainingResources.get("storage") + storageReq);
//        }
//
//        public Integer getId() {
//            return deviceId;
//        }
//
//        public double getCPU() {
//            return this.remainingResources.get("cpu");
//        }
//
//        public double getRAM() {
//            return this.remainingResources.get("ram");
//        }
//
//        public double getStorage() {
//            return this.remainingResources.get("storage");
//        }
//
//        public double getCPUUtil() {
//            double totalCPU = this.totalResources.get("cpu");
//            double availableCPU = this.remainingResources.get("cpu");
//            return (totalCPU - availableCPU) / totalCPU;
//        }
//
//        public double getRAMUtil() {
//            double totalRAM = this.totalResources.get("ram");
//            double availableRAM = this.remainingResources.get("ram");
//            return (totalRAM - availableRAM) / totalRAM;
//        }
//
//        // For standard deviation
//        public double getCPUUsage() {
//            double totalCPU = this.totalResources.get("cpu");
//            double availableCPU = this.remainingResources.get("cpu");
//            return (totalCPU - availableCPU);
//        }
//
//        public double getRAMUsage() {
//            double totalRAM = this.totalResources.get("ram");
//            double availableRAM = this.remainingResources.get("ram");
//            return (totalRAM - availableRAM);
//        }
//
//        // For efficiency, in the pooling optimisation in SimulatedAnnealing heuristic
//        // Alternative is copy constructor, this should take less time
//        public void resetTo(DeviceState other) {
//            if (!Objects.equals(deviceId, other.getId())) throw new NullPointerException("DeviceStates don't match id in reset call");
//
//            this.remainingResources.clear();
//            this.remainingResources.putAll(other.remainingResources);
//
//            this.totalResources.clear();
//            this.totalResources.putAll(other.totalResources);
//        }
//
//        @Override
//        public int compareTo(DeviceState other) {
//            // Sort by CPU Util, then by RAM Util if CPU is equal, from smallest to biggest
//            int cpuCompare = Double.compare(
//                    this.getCPUUtil(), // Smallest first
//                    other.getCPUUtil()
//            );
//            if (cpuCompare != 0) return cpuCompare;
//
//            int ramCompare = Double.compare(
//                    this.getRAMUtil(), // Smallest first
//                    other.getRAMUtil()
//            );
//            if (ramCompare != 0) return ramCompare;
//
//            // Use device ID as final tiebreaker for stable sorting
//            return Integer.compare(this.deviceId, other.deviceId);
//        }
//    }

    /**
     * Lightweight representation of a fog‑node's resources.
     * Copying is cheap → safe to duplicate in inner optimisation loops.
     */
    public static class DeviceState implements Comparable<DeviceState> {

        /* ---------- immutable identity ---------- */
        private final int id;

        /* ---------- immutable capacity ---------- */
        private final double totalCpu;      // MIPS
        private final double totalRam;      // MB
        private final double totalStorage;  // MB

        /* ---------- mutable remainder ---------- */
        private double freeCpu;
        private double freeRam;
        private double freeStorage;

        /* ---------- constructors ---------- */

        /** real device → state object  */
        public DeviceState(int id,
                           double totalCpu,
                           double totalRam,
                           double totalStorage,
                           double initialCpu,
                           double initialRam,
                           double initialStorage) {
            this.id            = id;
            this.totalCpu      = totalCpu;
            this.totalRam      = totalRam;
            this.totalStorage  = totalStorage;

            this.freeCpu       = initialCpu;
            this.freeRam       = initialRam;
            this.freeStorage   = initialStorage;
        }

        // For backward compatibility (non-aco algos)
        public DeviceState(int id,
                           Map<String, Double> dev,
                           double initialCpu,
                           double initialRam,
                           double initialStorage) {
            this.id            = id;
            this.freeCpu      = dev.get(ControllerComponent.CPU);
            this.freeRam      = dev.get(ControllerComponent.RAM);
            this.freeStorage  = dev.get(ControllerComponent.STORAGE);

            this.totalCpu       = initialCpu;
            this.totalRam       = initialRam;
            this.totalStorage   = initialStorage;
        }

        /** **cheap** copy‑ctor used inside ACO / SA loops */
        public DeviceState(DeviceState src) {
            this.id            = src.id;

            this.totalCpu      = src.totalCpu;
            this.totalRam      = src.totalRam;
            this.totalStorage  = src.totalStorage;

            this.freeCpu       = src.freeCpu;
            this.freeRam       = src.freeRam;
            this.freeStorage   = src.freeStorage;
        }

        /* ---------- resource helpers ---------- */

        public boolean canFit(double cpu, double ram, double storage) {
            return freeCpu >= cpu && freeRam >= ram && freeStorage >= storage;
        }

        public void allocate(double cpu, double ram, double storage) {
            freeCpu     -= cpu;
            freeRam     -= ram;
            freeStorage -= storage;
        }

        public void deallocate(double cpu, double ram, double storage) {
            freeCpu     += cpu;
            freeRam     += ram;
            freeStorage += storage;
        }

        /* utilisation & diagnostics */
        public double getCPUUtil() { return 1.0 - freeCpu/totalCpu; }
        public double getRAMUtil() { return 1.0 - freeRam/totalRam; }
        public int getId()      { return id; }

        public double getCPU() {
            return freeCpu;
        }

        public double getRAM() {
            return freeRam;
        }

        public double getStorage() {
            return freeStorage;
        }

        /* ---------- Comparable<DeviceState> ---------- */
        @Override
        public int compareTo(DeviceState o) {
            int byCpu = Double.compare(getCPUUtil(), o.getCPUUtil());
            if (byCpu != 0) return byCpu;
            int byRam = Double.compare(getRAMUtil(), o.getRAMUtil());
            if (byRam != 0) return byRam;
            return Integer.compare(id, o.id);
        }
    }


    class RelativeLatencyDeviceState implements Comparable<RelativeLatencyDeviceState> {

        FogDevice fogDevice;
        Double latencyToClosestHost;
        FogDevice closestEdgeNode;
        double[][] globalLatencies;

        RelativeLatencyDeviceState(FogDevice fogDevice, FogDevice closestEdgeNode,
                                   double[][] globalLatencies) {

            this.fogDevice = fogDevice;
            this.closestEdgeNode = closestEdgeNode;
            this.globalLatencies = globalLatencies;


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
        public int compareTo(RelativeLatencyDeviceState other) {
            try {
                // First compare by latency
                int latencyCompare = Double.compare(this.latencyToClosestHost, other.latencyToClosestHost);
                if (latencyCompare != 0) {
                    return latencyCompare;
                }
                
                // If latencies are equal, use device ID as a tiebreaker
                return Integer.compare(this.fogDevice.getId(), other.fogDevice.getId());
            } catch (Exception ex) {
                Logger.error("An error occurred", ex.getMessage());
                return 0;
            }
        }
    }

    public static class PRContextAwareEntry {
        private String microserviceName;
        private Integer deviceId;
        private Integer sensorId;
        private Integer prIndex;

        public PRContextAwareEntry(String microserviceName, Integer deviceId,
                                   Integer sensorId, Integer prIndex) {
            this.microserviceName = microserviceName;
            this.deviceId = deviceId;
            this.sensorId = sensorId;
            this.prIndex = prIndex;
        }

        public String getMicroserviceName() { return microserviceName; }
        public Integer getDeviceId() { return deviceId; }
        public Integer getSensorId() { return sensorId; }
        public Integer getPrIndex() { return prIndex; }
    }

    /**
     * Sets the seed for random number generation to ensure reproducible results.
     * @param seed The seed value to use
     */
    public void setSeed(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }
    
    /**
     * Gets the seeded random number generator for use by child classes.
     * @return A consistently seeded Random object
     */
    protected Random getRandom() {
        return random;
    }

}

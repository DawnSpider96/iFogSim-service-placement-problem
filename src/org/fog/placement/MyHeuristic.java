package org.fog.placement;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.utils.Logger;
import org.fog.utils.MicroservicePlacementConfig;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.MyMonitor;

import java.util.*;
import java.util.function.Consumer;

public abstract class MyHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */
    protected List<FogDevice> fogDevices; // ALL fog devices in the network
    protected List<FogDevice> edgeFogDevices = new ArrayList<>(); // Fog devices in the network that are in consideration for placement
    protected List<PlacementRequest> placementRequests; // requests to be processed
    protected Map<Integer, Map<String, Double>> resourceAvailability;
    protected Map<String, Application> applicationInfo = new HashMap<>(); // map app name to Application
    protected Map<String, String> moduleToApp = new HashMap<>();
    protected Map<PlacementRequest, Integer> closestNodes = new HashMap<>();

    int fonID;

    // Maps DeviceId to index (on latencies and `fogDevices` state)
    protected Map<Integer, Integer> indices;
    protected int cloudIndex = -1;
    protected double [][] globalLatencies;

    // Temporary State
    protected Map<Integer, Double> currentCpuLoad;
    protected Map<Integer, Double> currentRamLoad;
    protected Map<Integer, Double> currentStorageLoad;
    protected Map<Integer, List<String>> currentModuleMap = new HashMap<>();
    protected Map<Integer, Map<String, Double>> currentModuleLoadMap = new HashMap<>();
    protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum = new HashMap<>();
    Map<Integer, LinkedHashMap<String, Integer>> mappedMicroservices = new HashMap<>();


    public MyHeuristic(int fonID) {
        setFONId(fonID);
    }

    public void setFONId(int id) {
        fonID = id;
    }

    public int getFonID() {
        return fonID;
    }

    @Override
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
            closestNodes.put(placementRequest, getDevice(placementRequest.getGatewayDeviceId()).getParentId());

            // already placed modules
            mappedMicroservices.put(placementRequest.getPlacementRequestId(), new LinkedHashMap<>(placementRequest.getPlacedMicroservices()));

            //special modules  - predefined cloud placements
            Application app =  applicationInfo.get(placementRequest.getApplicationId());
            for (String microservice : app.getSpecialPlacementInfo().keySet()) {
                for (String deviceName : app.getSpecialPlacementInfo().get(microservice)) {
                    FogDevice device = getDeviceByName(deviceName);
                    tryPlacingMicroserviceNoAggregate(microservice, device, app, m->{}, placementRequest.getPlacementRequestId());
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
    protected void tryPlacingMicroserviceNoAggregate(String microservice, FogDevice device, Application app, Consumer<String> onPlaced, int prId) {
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

            mappedMicroservices.get(prId).put(microservice, deviceId);

            //currentModuleLoad
            if (!currentModuleLoadMap.get(deviceId).containsKey(microservice))
                currentModuleLoadMap.get(deviceId).put(microservice, getModule(microservice, app).getMips());
            else
                currentModuleLoadMap.get(deviceId).put(microservice, getModule(microservice, app).getMips() + currentModuleLoadMap.get(deviceId).get(microservice)); // todo Simon says isn't this already vertical scaling? But is on PR side not FogDevice side

            //currentModuleInstance
            if (!currentModuleInstanceNum.get(deviceId).containsKey(microservice))
                currentModuleInstanceNum.get(deviceId).put(microservice, 1);
            else
                currentModuleInstanceNum.get(deviceId).put(microservice, currentModuleInstanceNum.get(deviceId).get(microservice) + 1);

            onPlaced.accept(microservice);
        }
    }

    protected abstract int tryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest);

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
    protected abstract int fillToPlace(int placementCompleteCount, Map<PlacementRequest, List<String>> toPlace, List<PlacementRequest> placementRequests);

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
    // TODO Simon says output is one list, which is a bit awkward
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
            MyFogDevice mfd = (MyFogDevice) fogDevice;
            if (Objects.equals(mfd.getDeviceType(), MyFogDevice.FCN)) {
                deviceIdsToInclude.add(mfd.getId());
            }
        }
        edgeFogDevices = new ArrayList<>();
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

        setCurrentCpuLoad(new HashMap<Integer, Double>());
        setCurrentRamLoad(new HashMap<Integer, Double>());
        setCurrentStorageLoad(new HashMap<Integer, Double>());
        setCurrentModuleMap(new HashMap<>());
        for (FogDevice dev : fogDevices) {
            int id = dev.getId();
            getCurrentCpuLoad().put(id, 0.0);
            getCurrentRamLoad().put(id, 0.0);
            getCurrentStorageLoad().put(id, 0.0);
            getCurrentModuleMap().put(id, new ArrayList<>());
            currentModuleLoadMap.put(id, new HashMap<String, Double>());
            currentModuleInstanceNum.put(id, new HashMap<String, Integer>());
        }

        indices = new HashMap<>();
        for (int i=0 ; i<fogDevices.size() ; i++) {
            indices.put(fogDevices.get(i).getId(), i);
            // While we're at it, determine cloud's index
            if (fogDevices.get(i).getName() == "cloud") cloudIndex = i;
        }
        globalLatencies = fillGlobalLatencies(fogDevices.size(), indices);
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
                AppModule module = app.getModuleByName(moduleName);
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
        List<DeviceState> snapshots = new ArrayList<>();
        for (FogDevice fd : edgeFogDevices) {
            int deviceId = fd.getId();
            snapshots.add(new DeviceState(deviceId, resourceAvailability.get(deviceId),
                    fd.getHost().getTotalMips(), fd.getHost().getRam(), fd.getHost().getStorage()));
        }
        MyMonitor.getSnapshots().put(CloudSim.clock(), snapshots);
        // FogBroker.getBatchNumber()

    }

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
     * @return A map reflecting the updated entries after cleaning.
     * PRid ->  Map(microservice name -> target deviceId)
     */
    protected Map<Integer, Map<String, Integer>> cleanPlacementRequests(List <PlacementRequest> placementRequests, Map<Integer, LinkedHashMap<String, Integer>> mappedMicroservices) {
        /*
        Returns HashMap containing all the services placed IN THIS CYCLE to each device
        */
        Map<Integer, Map<String, Integer>> placement = new HashMap<>();
        Map<PlacementRequest, Double> latencies = new HashMap<>();
        for (PlacementRequest placementRequest : placementRequests) {
            List<String> toRemove = new ArrayList<>();
            // placement should include newly placed ones
            for (String microservice : mappedMicroservices.get(placementRequest.getPlacementRequestId()).keySet()) {
                if (placementRequest.getPlacedMicroservices().containsKey(microservice))
                    toRemove.add(microservice);
                else
                    placementRequest.getPlacedMicroservices().put(microservice, mappedMicroservices.get(placementRequest.getPlacementRequestId()).get(microservice));
            }
            for (String microservice : toRemove)
                mappedMicroservices.get(placementRequest.getPlacementRequestId()).remove(microservice);

            // Simon (170225) says update PR to shift first module (always clientModule) to last place
            // For metric collecting purposes
            Map.Entry<String, Integer> clientModuleEntry = placementRequest.getPlacedMicroservices().entrySet().iterator().next();
            placementRequest.getPlacedMicroservices().remove(clientModuleEntry.getKey());
            placementRequest.getPlacedMicroservices().put(clientModuleEntry.getKey(), clientModuleEntry.getValue());
            latencies.put(placementRequest,determineLatencyOfDecision(placementRequest));

            // Update output
            placement.put(placementRequest.getPlacementRequestId(), mappedMicroservices.get(placementRequest.getPlacementRequestId()));
        }
        MyMonitor.getLatencies().put(CloudSim.clock(), latencies);
        return placement;
    }

    /**
     * State queried: applicationInfo, currentModuleInstanceNum
     *
     * @param prStatus Map of placement requests to their result. Was outputted by MapModules()
     *
     * @return Placement Decision containing:
     *  - perDevice
     *  - ServiceDiscovery
     *  - prStatus
     *  - Targets
     */
    protected PlacementLogicOutput generatePlacementDecision(Map<PlacementRequest, Integer> prStatus) {
        // placements: PRid ->  Map(microservice name -> target deviceId)
        Map<Integer, Map<String, Integer>> placements = cleanPlacementRequests(placementRequests, mappedMicroservices);

        //todo it assumed that modules are not shared among applications.
        // <deviceid, < app, list of modules to deploy > this is to remove deploying same module more than once on a certain device.
        Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice = new HashMap<>();
        Map<Integer, List<Pair<String, Integer>>> serviceDiscoveryInfo = new HashMap<>();
        if (placements != null) {
            for (int prID : placements.keySet()) {
                //retrieve application
                PlacementRequest placementRequest = null;
                for (PlacementRequest pr : placementRequests) {
                    if (pr.getPlacementRequestId() == prID)
                        placementRequest = pr;
                }
                Application application = applicationInfo.get(placementRequest.getApplicationId());
                for (String microserviceName : placements.get(prID).keySet()) {
                    int deviceID = placements.get(prID).get(microserviceName);

                    //service discovery info propagation
                    List<Integer> clientDevices = getClientServiceNodeIds(application, microserviceName, placementRequest.getPlacedMicroservices(), placements.get(prID));
                    for (int clientDevice : clientDevices) {
                        if (serviceDiscoveryInfo.containsKey(clientDevice))
                            serviceDiscoveryInfo.get(clientDevice).add(new Pair<>(microserviceName, deviceID));
                        else {
                            List<Pair<String, Integer>> s = new ArrayList<>();
                            s.add(new Pair<>(microserviceName, deviceID));
                            serviceDiscoveryInfo.put(clientDevice, s);
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

        Map<PlacementRequest, Integer> targets = determineTargets(perDevice);


        return new MyPlacementLogicOutput(perDevice, serviceDiscoveryInfo, prStatus, targets);
    }


    /**
    * State that can be used:
    *   - List<PlacementRequest> placementRequests
    *   - Map<PlacementRequest, Integer> closestNodes
    *  - Map<Integer, Application> applicationInfo
    * @param perDevice
    * @return Map of each PR to the deviceId that the FogBroker will inform to begin execution
    * */
    protected abstract Map<PlacementRequest, Integer> determineTargets(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice);

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
        for (AppModule appModule : app.getModules()) {
            if (appModule.getName().equals(moduleName))
                return appModule;
        }
        return null;
    }

    protected FogDevice getDevice(int deviceId) {
        for (FogDevice fogDevice : fogDevices) {
            if (fogDevice.getId() == deviceId)
                return fogDevice;
        }
        return null;
    }

    private double determineLatencyOfDecision(PlacementRequest pr) {
        // Simon (170225) says we are currently NOT considering execution time
        // Hence only placement targets are considered
        // placedMicroservices are ordered
        double latency = 0;
        List<Integer> deviceIds = new ArrayList<>(pr.getPlacedMicroservices().values());

        // Check if there are at least two devices to calculate latency
        if (deviceIds.size() > 1) {
            for (int i = 0; i < deviceIds.size() - 1; i++) {
                int sourceDevice = deviceIds.get(i);
                int destDevice = deviceIds.get(i + 1);
                latency += getLatency(sourceDevice, destDevice);
            }
        }
        return latency;
    }

    private double getLatency(int srcDevice, int destDevice) {
        /*
        Destination MyFogDevice may be a user. Source MyFogDevice is always edge server.
         */
        int srcIndex = indices.get(srcDevice);
        int destIndex = indices.get(destDevice);

        double l = globalLatencies[srcIndex][destIndex];
        if (l >= 0) return l;

        assert MicroservicePlacementConfig.NETWORK_TOPOLOGY == MicroservicePlacementConfig.CENTRALISED;
        MyFogDevice src = (MyFogDevice) getDevice(srcDevice);
        MyFogDevice dest = (MyFogDevice) getDevice(destDevice);


        if (src.getDeviceType() == MyFogDevice.FCN && dest.getDeviceType() == MyFogDevice.FCN){
            return globalLatencies[srcIndex][cloudIndex] + globalLatencies[cloudIndex][destIndex];
        }
        else { // dest is user device
            assert dest.getDeviceType() == MyFogDevice.GENERIC_USER ||
                    dest.getDeviceType() == MyFogDevice.AMBULANCE_USER ||
                    dest.getDeviceType() == MyFogDevice.OPERA_USER:
                    "Destination device must be Mobile User";
            int parentIndex = indices.get(dest.getParentId());
            // TODO check that uplinklatency in milliseconds
            return globalLatencies[srcIndex][cloudIndex] + globalLatencies[cloudIndex][parentIndex] + dest.getUplinkLatency();
        }
    }

    // Class to track resource state during placement decisions
    public static class DeviceState implements Comparable<DeviceState>{
        private final Integer deviceId;
        private final Map<String, Double> remainingResources;
        private final Map<String, Double> totalResources;

        public DeviceState(Integer deviceId, Map<String, Double> initialResources, double mips, double ram, double storage) {
            this.deviceId = deviceId;
            this.remainingResources = new HashMap<>(initialResources);
            this.totalResources = new HashMap<>();
            this.totalResources.put("cpu", mips);
            this.totalResources.put("ram", ram);
            this.totalResources.put("storage", storage);
        }

        public DeviceState(DeviceState other) {
            this.deviceId = other.deviceId;
            this.remainingResources = new HashMap<>(other.remainingResources);
            this.totalResources = new HashMap<>(other.totalResources);
        }


        public boolean canFit(double cpuReq, double ramReq, double storageReq) {
            return remainingResources.get("cpu") >= cpuReq &&
                    remainingResources.get("ram") >= ramReq &&
                    remainingResources.get("storage") >= storageReq;
        }

        public void allocate(double cpuReq, double ramReq, double storageReq) {
            remainingResources.put("cpu", remainingResources.get("cpu") - cpuReq);
            remainingResources.put("ram", remainingResources.get("ram") - ramReq);
            remainingResources.put("storage", remainingResources.get("storage") - storageReq);
        }

        public void deallocate(double cpuReq, double ramReq, double storageReq) {
            remainingResources.put("cpu", remainingResources.get("cpu") + cpuReq);
            remainingResources.put("ram", remainingResources.get("ram") + ramReq);
            remainingResources.put("storage", remainingResources.get("storage") + storageReq);
        }

        public Integer getId() {
            return deviceId;
        }

        public double getCPU() {
            return this.remainingResources.get("cpu");
        }

        public double getRAM() {
            return this.remainingResources.get("ram");
        }

        public double getCPUUtil() {
            double totalCPU = this.totalResources.get("cpu");
            double availableCPU = this.remainingResources.get("cpu");
            return (totalCPU - availableCPU) / totalCPU;
        }

        public double getRAMUtil() {
            double totalRAM = this.totalResources.get("ram");
            double availableRAM = this.remainingResources.get("ram");
            return (totalRAM - availableRAM) / totalRAM;
        }

        @Override
        public int compareTo(DeviceState other) {
            // Sort by CPU Util, then by RAM Util if CPU is equal, from smallest to biggest
            int cpuCompare;
            cpuCompare = Double.compare(
                    this.getCPUUtil(), // Smallest first
                    other.getCPUUtil()
            );
            if (cpuCompare != 0) return cpuCompare;

            return Double.compare(
                    this.getRAMUtil(), // Smallest first
                    other.getRAMUtil()
            );
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
        public int compareTo(RelativeLatencyDeviceState other) {
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

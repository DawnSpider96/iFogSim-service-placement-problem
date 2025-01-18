package org.fog.placement;

import org.apache.commons.math3.util.Pair;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;

import java.util.*;
import java.util.function.Consumer;

public abstract class MyHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */
    protected List<FogDevice> fogDevices; // ALL fog devices in the network
    protected List<FogDevice> availableFogDevices = new ArrayList<>(); // Fog devices in the network that are in consideration for placement
    protected List<PlacementRequest> placementRequests; // requests to be processed
    protected Map<Integer, Map<String, Double>> resourceAvailability;
    protected Map<String, Application> applicationInfo = new HashMap<>(); // map app name to Application
    protected Map<String, String> moduleToApp = new HashMap<>();
    protected Map<PlacementRequest, Integer> closestNodes = new HashMap<>();

    int fonID;

    // Temporary State
    protected Map<Integer, Double> currentCpuLoad;
    protected Map<Integer, Double> currentRamLoad;
    protected Map<Integer, Double> currentStorageLoad;
    protected Map<Integer, List<String>> currentModuleMap = new HashMap<>();
    protected Map<Integer, Map<String, Double>> currentModuleLoadMap = new HashMap<>();
    protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum = new HashMap<>();
    Map<Integer, Map<String, Integer>> mappedMicroservices = new HashMap<>();


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
            mappedMicroservices.put(placementRequest.getPlacementRequestId(), new HashMap<>(placementRequest.getPlacedMicroservices()));

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
        for (FogDevice fogDevice : this.fogDevices) {
            if (deviceIdsToInclude.contains(fogDevice.getId())) {
                availableFogDevices.add(fogDevice);
            }
        }

        this.placementRequests = prs;
        this.resourceAvailability = resourceAvailability;
        this.applicationInfo = applicationInfo;
        mappedMicroservices = new HashMap<>();
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
    protected Map<Integer, Map<String, Integer>> cleanPlacementRequests(List <PlacementRequest> placementRequests, Map<Integer, Map<String, Integer>> mappedMicroservices) {
        Map<Integer, Map<String, Integer>> placement = new HashMap<>();
        for (PlacementRequest placementRequest : placementRequests) {
            List<String> toRemove = new ArrayList<>();
            //placement should include newly placed ones
            for (String microservice : mappedMicroservices.get(placementRequest.getPlacementRequestId()).keySet()) {
                if (placementRequest.getPlacedMicroservices().containsKey(microservice))
                    toRemove.add(microservice);
                else
                    placementRequest.getPlacedMicroservices().put(microservice, mappedMicroservices.get(placementRequest.getPlacementRequestId()).get(microservice));
            }
            for (String microservice : toRemove)
                mappedMicroservices.get(placementRequest.getPlacementRequestId()).remove(microservice);

            //update placed modules in placement request as well
            placement.put(placementRequest.getPlacementRequestId(), mappedMicroservices.get(placementRequest.getPlacementRequestId()));
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

}

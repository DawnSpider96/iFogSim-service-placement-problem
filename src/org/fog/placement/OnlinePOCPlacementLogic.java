package org.fog.placement;

import org.apache.commons.math3.util.Pair;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;

import java.util.*;

public class OnlinePOCPlacementLogic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */
    List<FogDevice> fogDevices; //fog devices considered by FON for placements of requests
    List<PlacementRequest> placementRequests; // requests to be processed
    protected Map<Integer, Map<String, Double>> resourceAvailability;
    private Map<String, Application> applicationInfo = new HashMap<>();
    private Map<String, String> moduleToApp = new HashMap<>();

    int fonID;

    protected Map<Integer, Double> currentCpuLoad;
    protected Map<Integer, Double> currentRamLoad;
    protected Map<Integer, Double> currentStorageLoad;
    protected Map<Integer, List<String>> currentModuleMap = new HashMap<>();
    protected Map<Integer, Map<String, Double>> currentModuleLoadMap = new HashMap<>();
    protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum = new HashMap<>();

    // todo Simon says we shall initialise mappedMicroservices before every PRP cycle
    Map<Integer, Map<String, Integer>> mappedMicroservices = new HashMap<>();
    ; //mappedMicroservice

    public OnlinePOCPlacementLogic(int fonID) {
        setFONId(fonID);
    }

    public void setFONId(int id) {
        fonID = id;
    }

    public int getFonID() {
        return fonID;
    }

    @Override
    public PlacementLogicOutput run(List<FogDevice> fogDevices, Map<String, Application> applicationInfo, Map<Integer, Map<String, Double>> resourceAvailability, List<PlacementRequest> pr) {
        this.fogDevices = fogDevices;
        this.placementRequests = pr;
        this.resourceAvailability = resourceAvailability;
        this.applicationInfo = applicationInfo;

        setCurrentCpuLoad(new HashMap<Integer, Double>());
        setCurrentRamLoad(new HashMap<Integer, Double>());
        setCurrentStorageLoad(new HashMap<Integer, Double>());
        setCurrentModuleMap(new HashMap<>());
        for (FogDevice dev : fogDevices) {
            getCurrentCpuLoad().put(dev.getId(), 0.0);
            getCurrentRamLoad().put(dev.getId(), 0.0);
            getCurrentStorageLoad().put(dev.getId(), 0.0);
            getCurrentModuleMap().put(dev.getId(), new ArrayList<>());
            currentModuleLoadMap.put(dev.getId(), new HashMap<String, Double>());
            currentModuleInstanceNum.put(dev.getId(), new HashMap<String, Integer>());
        }
        // todo Simon says we initialise mappedMicroservices before every PRP cycle along with currentModuleLoadMap and currentModuleInstanceNum
        mappedMicroservices = new HashMap<>();

        mapModules();
        PlacementLogicOutput placement = generatePlacementDecision();
        updateResources(resourceAvailability);
        postProcessing();
        return placement;
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

    private PlacementLogicOutput generatePlacementDecision() {
        Map<Integer, Map<String, Integer>> placement = new HashMap<>();
        for (PlacementRequest placementRequest : placementRequests) {
            List<String> toRemove = new ArrayList<>();
            //placement should include newly placed ones
            for (String microservice : mappedMicroservices.get(placementRequest.getSensorId()).keySet()) {
                if (placementRequest.getPlacedServices().containsKey(microservice))
                    toRemove.add(microservice);
                else
                    placementRequest.getPlacedServices().put(microservice, mappedMicroservices.get(placementRequest.getSensorId()).get(microservice));
            }
            for (String microservice : toRemove)
                mappedMicroservices.get(placementRequest.getSensorId()).remove(microservice);

            //update placed modules in placement request as well
            placement.put(placementRequest.getSensorId(), mappedMicroservices.get(placementRequest.getSensorId()));
        }

        //todo it assumed that modules are not shared among applications.
        // <deviceid, < app, list of modules to deploy > this is to remove deploying same module more than once on a certain device.
        Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice = new HashMap<>();
        Map<Integer, List<SPPHeuristic.PRContextAwareEntry>> serviceDiscoveryInfo = new HashMap<>();
        Map<PlacementRequest, Integer> prStatus = new HashMap<>();
        if (placement != null) {
            for (int prID : placement.keySet()) {
                ContextPlacementRequest placementRequest = null;
                for (PlacementRequest pr : placementRequests) {
                    if (pr.getSensorId() == prID) {
                        placementRequest = (ContextPlacementRequest) pr;
                        break;
                    }
                }

                if (placementRequest == null) {
                    Logger.error("PlacementRequest query Error", "Could not find placement request for prID: " + prID);
                    continue;
                }
                Application application = applicationInfo.get(placementRequest.getApplicationId());

                for (String microserviceName : placement.get(prID).keySet()) {
                    int deviceID = placement.get(prID).get(microserviceName);

                    // Get client devices that need this service discovery info
                    List<Integer> clientDevices = getClientServiceNodeIds(application,
                            microserviceName,
                            placementRequest.getPlacedServices(),
                            placement.get(prID));

                    for (int clientDevice : clientDevices) {
                        // Create standard service discovery entry (Pair)
                        Pair<String, Integer> sdEntry = new Pair<>(microserviceName, deviceID);

                        // Create PR-aware entry with context
                        SPPHeuristic.PRContextAwareEntry entry = new SPPHeuristic.PRContextAwareEntry(
                                microserviceName,
                                deviceID,
                                placementRequest.getSensorId(),
                                placementRequest.getPrIndex()
                        );
                        if (serviceDiscoveryInfo.containsKey(clientDevice)) {
                            serviceDiscoveryInfo.get(clientDevice).add(entry);
                        } else {
                            List<SPPHeuristic.PRContextAwareEntry> entries = new ArrayList<>();
                            entries.add(entry);
                            serviceDiscoveryInfo.put(clientDevice, entries);
                        }
                    }
                }
                // all prs get placed in this placement algorithm
                prStatus.put(placementRequest, -1);
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

        Map<PlacementRequest, Integer> targets = new HashMap();
        // todo Simon says that ALL the parent edge servers of the users that made PRs must receive deployments. Otherwise error.
        for (PlacementRequest pr : placementRequests) {
            int parentOfGateway = Objects.requireNonNull(getDevice(pr.getRequester())).getParentId();
            boolean targeted = false;
            for (int target : perDevice.keySet()) {
                if (parentOfGateway == target) {
                    targeted = true;
                    targets.put(pr, parentOfGateway);
                    break;
                }
            }
            if (!targeted) {
                Logger.error("Deployment Error", "Deployment Request is not being sent to "
                        + parentOfGateway + ", the parent of gateway device " + Objects.requireNonNull(getDevice(pr.getRequester())).getName());
            }
        }

        return new ContextAwarePlacement(perDevice, serviceDiscoveryInfo, prStatus, targets);
    }

    public List<Integer> getClientServiceNodeIds(Application application, String
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


    public List<String> getClientServices(Application application, String microservice) {
        List<String> clientServices = new LinkedList<>();

        for (AppEdge edge : application.getEdges()) {
            if (edge.getDestination().equals(microservice) && edge.getDirection() == Tuple.UP)
                clientServices.add(edge.getSource());
        }
        return clientServices;
    }


    @Override
    public void postProcessing() {

    }

    public void mapModules() {
        Map<PlacementRequest, Integer> currentTargets = new HashMap<>();
        //initiate with the  parent of the client device for this
        for (PlacementRequest placementRequest : placementRequests) {
            currentTargets.put(placementRequest, getDevice(placementRequest.getRequester()).getParentId());

            // already placed modules
            mappedMicroservices.put(placementRequest.getSensorId(), new HashMap<>(placementRequest.getPlacedServices()));

            //special modules  - predefined cloud placements
            Application app =  applicationInfo.get(placementRequest.getApplicationId());
            for (String microservice : app.getSpecialPlacementInfo().keySet()) {
                for (String deviceName : app.getSpecialPlacementInfo().get(microservice)) {
                    FogDevice device = getDeviceByName(deviceName);
                    int deviceId = device.getId();

                    if (getModule(microservice, app).getMips() + getCurrentCpuLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.CPU)
                    && getModule(microservice, app).getRam() + getCurrentRamLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.RAM)
                    && getModule(microservice, app).getSize() + getCurrentStorageLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.STORAGE)) {
                        Logger.debug("ModulePlacementEdgeward", "Placement of operator " + microservice + " on device " + device.getName() + " successful.");
                        getCurrentCpuLoad().put(deviceId, getModule(microservice, app).getMips() + getCurrentCpuLoad().get(deviceId));
                        getCurrentRamLoad().put(deviceId, getModule(microservice, app).getRam() + getCurrentRamLoad().get(deviceId));
                        getCurrentStorageLoad().put(deviceId, getModule(microservice, app).getSize() + getCurrentStorageLoad().get(deviceId));
                        System.out.println("Placement of operator " + microservice + " on device " + device.getName() + " successful.");

                        moduleToApp.put(microservice, app.getAppId());

                        if (!currentModuleMap.get(deviceId).contains(microservice))
                            currentModuleMap.get(deviceId).add(microservice);

                        mappedMicroservices.get(placementRequest.getSensorId()).put(microservice, deviceId);

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

                        break;
                    }
                }
            }
        }


        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();
//        Map<PlacementRequest, Integer> clusterNode = new HashMap<>();


        int placementCompleteCount = 0;
        while (placementCompleteCount < placementRequests.size()) {
            if (toPlace.isEmpty()) {
                for (PlacementRequest placementRequest : placementRequests) {
                    Application app = applicationInfo.get(placementRequest.getApplicationId());
                    // modulesToPlace returns all the modules from the APP which 1. Have not been placed 2. All their dependent modules (from UP or DOWN) within their PR have been placed
                    // NOTE: Every PR (primary key placementRequestId) has its own set of placed modules (stored in mappedMicroservices).
                    // Meaning each module in all PRs has a separate set of dependent modules, which are from the same PR
                    // As argument we pass the list of set modules FOR THAT PR that have been placed. But in the function we are iterating through ALL modules in the app
                    List<String> modulesToPlace = getModulesToPlace(mappedMicroservices.get(placementRequest.getSensorId()).keySet(), app);
                    if (modulesToPlace.isEmpty())
                        placementCompleteCount++;
                    else
                        toPlace.put(placementRequest, modulesToPlace);
                }
            }
            for (PlacementRequest placementRequest : placementRequests) {
                Application app = applicationInfo.get(placementRequest.getApplicationId());
                int deviceId = currentTargets.get(placementRequest); // NOTE: Initially contains parent ID of gateway device (MOBILE USER). Changes depending on how we "forward" the PR (if previous target device lacked resources).
                // if not cluster
                if (deviceId != -1) {
                    FogDevice device = getDevice(deviceId);
                    List<String> placed = new ArrayList<>();
                    if (toPlace.containsKey(placementRequest)) {
                        for (String microservice : toPlace.get(placementRequest)) {
                            // try to place
                            if (getModule(microservice, app).getMips() + getCurrentCpuLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.CPU)
                            && getModule(microservice, app).getRam() + getCurrentRamLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.RAM)
                            && getModule(microservice, app).getSize() + getCurrentStorageLoad().get(deviceId) <= resourceAvailability.get(deviceId).get(ControllerComponent.STORAGE)) {
                                Logger.debug("ModulePlacementEdgeward", "Placement of operator " + microservice + " on device " + device.getName() + " successful.");
                                getCurrentCpuLoad().put(deviceId, getModule(microservice, app).getMips() + getCurrentCpuLoad().get(deviceId));
                                getCurrentRamLoad().put(deviceId, getModule(microservice, app).getRam() + getCurrentRamLoad().get(deviceId));
                                getCurrentStorageLoad().put(deviceId, getModule(microservice, app).getSize() + getCurrentStorageLoad().get(deviceId));
                                System.out.println("Placement of operator " + microservice + " on device " + device.getName() + " successful.");

                                moduleToApp.put(microservice, app.getAppId());

                                if (!currentModuleMap.get(deviceId).contains(microservice))
                                    currentModuleMap.get(deviceId).add(microservice);

                                mappedMicroservices.get(placementRequest.getSensorId()).put(microservice, deviceId);

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

                                placed.add(microservice);
                            }
                        }
                        for (String m : placed) {
                            toPlace.get(placementRequest).remove(m);
                        }
                        if (!toPlace.get(placementRequest).isEmpty()) {
                            currentTargets.put(placementRequest, device.getParentId());
                        }
                        if (toPlace.get(placementRequest).isEmpty())
                            toPlace.remove(placementRequest);
                    }
                } else {
                    System.out.println("CLUSTER ISSUE: deviceID is -1");
                }
            }
        }

    }

    private FogDevice getDeviceByName(String deviceName) {
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

    private AppModule getModule(String moduleName, Application app) {
        for (AppModule appModule : app.getModules()) {
            if (appModule.getName().equals(moduleName))
                return appModule;
        }
        return null;
    }

    private FogDevice getDevice(int deviceId) {
        for (FogDevice fogDevice : fogDevices) {
            if (fogDevice.getId() == deviceId)
                return fogDevice;
        }
        return null;
    }

    private List<String> getModulesToPlace(Set<String> placedModules, Application app) {
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
}

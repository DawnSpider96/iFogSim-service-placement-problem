package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.utils.*;
import org.json.simple.JSONObject;

import java.util.*;


public class MyMicroservicesController extends SimEntity {

    protected List<FogDevice> fogDevices;
    protected List<Sensor> sensors;
    private Map<Integer, Integer> sensorToSequenceNumber = new HashMap<>();
    protected Map<String, Application> applications = new HashMap<>();
    protected PlacementLogicFactory placementLogicFactory = new PlacementLogicFactory();
    protected int placementLogic;
    //    protected List<Integer> clustering_levels;
    /**
     * A permanent set of Placement Requests, initialized with one per user device.
     * For PR generation, simulation will always make copies of these.
     */
    protected Map<PlacementRequest, Integer> placementRequestDelayMap = new HashMap<>();

    // For PR generation
    private List<MyFogDevice> userDevices = new ArrayList<>();
    private Map<Integer, Map<String, Double>> userResourceAvailability = new HashMap<>();
    /**
     * Interval (in simulation time units) at which periodic placement requests are generated.
     * This value is static and shared across all instances.
     */
    private double prGenerationInterval = MicroservicePlacementConfig.PLACEMENT_GENERATE_INTERVAL;


    /**
     * @param name
     * @param fogDevices
     * @param sensors
     * @param applications
     */
    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Application> applications, int placementLogic) {
        super(name);
        this.fogDevices = fogDevices;
        this.sensors = sensors;
//        this.clustering_levels = clusterLevels;
        this.placementLogic = placementLogic;
        for (Application app : applications) {
            this.applications.put(app.getAppId(), app);
        }

        init();

    }

    public MyMicroservicesController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Application> applications, int placementLogic, Map<Integer, List<FogDevice>> monitored) {
        super(name);
        this.fogDevices = fogDevices;
        this.sensors = sensors;
//        this.clustering_levels = clusterLevels;
        this.placementLogic = placementLogic;
        for (Application app : applications) {
            this.applications.put(app.getAppId(), app);
        }
        init(monitored);
    }

    protected void init() {
        connectWithLatencies();

//        if (Config.ENABLE_STATIC_CLUSTERING) {
//            for (Integer id : clustering_levels)
//                createClusterConnections(id, fogDevices, Config.clusteringLatency);
//        }
//        printClusterConnections();

        initializeControllers(placementLogic);
        generateRoutingTable();
    }

    protected void init(Map<Integer, List<FogDevice>> monitored) {
        connectWithLatencies();

//        if (!Config.ENABLE_STATIC_CLUSTERING) {
//            for (Integer id : clustering_levels)
//                createClusterConnections(id, fogDevices, Config.clusteringLatency);
//        }
//        printClusterConnections();

        initializeControllers(placementLogic, monitored);
        generateRoutingTable();
    }

    protected void initializeControllers(int placementLogic) {
        for (FogDevice device : fogDevices) {
            LoadBalancer loadBalancer = new RRLoadBalancer();
            MyFogDevice cdevice = (MyFogDevice) device;

            //responsible for placement decision making
            if (cdevice.getDeviceType().equals(MyFogDevice.FON) || cdevice.getDeviceType().equals(MyFogDevice.CLOUD)) {
                List<FogDevice> monitoredDevices = getDevicesForFON(cdevice);
                MicroservicePlacementLogic microservicePlacementLogic = placementLogicFactory.getPlacementLogic(placementLogic, cdevice.getId());
                cdevice.initializeController(loadBalancer, microservicePlacementLogic, getResourceInfo(monitoredDevices), applications, monitoredDevices);
            } else if (cdevice.getDeviceType().equals(MyFogDevice.FCN) || cdevice.getDeviceType().equals(MyFogDevice.GENERIC_USER)) {
                cdevice.initializeController(loadBalancer);
            }
            else {
                System.out.println("UNKNOWN FOGDEVICE TYPE DETECTED");
            }
        }
    }

    protected void initializeControllers(int placementLogic, Map<Integer, List<FogDevice>> monitored) {
        for (FogDevice device : fogDevices) {
            LoadBalancer loadBalancer = new RRLoadBalancer();
            MyFogDevice cdevice = (MyFogDevice) device;

            //responsible for placement decision making
            if (cdevice.getDeviceType().equals(MyFogDevice.FON) || cdevice.getDeviceType().equals(MyFogDevice.CLOUD)) {
                List<FogDevice> monitoredDevices = monitored.get(cdevice.getFonId());
                MicroservicePlacementLogic microservicePlacementLogic = placementLogicFactory.getPlacementLogic(placementLogic, cdevice.getId());
                cdevice.initializeController(loadBalancer, microservicePlacementLogic, getResourceInfo(monitoredDevices), applications, monitoredDevices);
            } else if (cdevice.getDeviceType().equals(MyFogDevice.FCN) || cdevice.getDeviceType().equals(MyFogDevice.GENERIC_USER)) {
                cdevice.initializeController(loadBalancer);
            }
            else {
                System.out.println("UNKNOWN FOGDEVICE TYPE DETECTED");
            }
        }
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
//            case FogEvents.TRANSMIT_PR:
//                transmitPr(ev);
            case FogEvents.CONTROLLER_RESOURCE_MANAGE:
                manageResources();
                break;
            case FogEvents.GENERATE_PERIODIC_PR:
                generatePeriodicPlacementRequests();
                break;
            case FogEvents.USER_RESOURCE_UPDATE:
                processUserResourceUpdate(ev);
                break;
            case FogEvents.STOP_SIMULATION:
                CloudSim.stopSimulation();
                System.out.println("=========================================");
                System.out.println("============== METRICS ==================");
                System.out.println("=========================================");
                printTimeDetails();
                printPowerDetails();
                printCostDetails();
                printNetworkUsageDetails();
                printQoSDetails();
                System.exit(0);
                break;
        }
    }

    protected void generateRoutingTable() {
        Map<Integer, Map<Integer, Integer>> routing = ShortestPathRoutingGenerator.generateRoutingTable(fogDevices);

        for (FogDevice f : fogDevices) {
            ((MyFogDevice) f).addRoutingTable(routing.get(f.getId()));
        }

    }

    public void startEntity() {
        // Keep track of user resources, then 
        // schedule first periodic PR generation
        initializeUserResources();
        send(getId(), prGenerationInterval, FogEvents.GENERATE_PERIODIC_PR);

        // todo Simon says Placement Decisions will be made dynamically, but only by the Cloud!
        // todo Hence no need for an initialisation
        if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC")
            Logger.error("Simulation not Dynamic error", "Simulation mode should be dynamic");
        if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC")
            initiatePlacementRequestProcessingDynamic();

//        if (MicroservicePlacementConfig.ENABLE_RESOURCE_DATA_SHARING) {
//            shareResourceDataAmongClusterNodes();
//        }

        send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
        send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);
    }

//    protected void shareResourceDataAmongClusterNodes() {
//        for (FogDevice f : fogDevices) {
//            if (((MyFogDevice) f).getIsInCluster()) {
//                for (int deviceId : ((MyFogDevice) f).getClusterMembers()) {
//                    Pair<Integer, Map<String, Double>> resources = new Pair<>(f.getId(), ((MyFogDevice) f).getResourceAvailabilityOfDevice());
//                    sendNow(deviceId, FogEvents.UPDATE_RESOURCE_INFO, resources);
//                }
//            }
//        }
//    }

    public void initializeUserResources() {
        for (MyFogDevice device : userDevices) {
            if (device.getDeviceType().equals(MyFogDevice.GENERIC_USER)) {
                Map<String, Double> resources = new HashMap<>();
                resources.put(ControllerComponent.CPU, (double) device.getHost().getTotalMips());
                resources.put(ControllerComponent.RAM, (double) device.getHost().getRam());
                resources.put(ControllerComponent.STORAGE, (double) device.getHost().getStorage());
                userResourceAvailability.put(device.getId(), resources);
            }
        }
    }

    public void updateUserResourceUsage(int userId, String resourceType, double usage, boolean isIncrease) {
        if (userResourceAvailability.containsKey(userId)) {
            Map<String, Double> resources = userResourceAvailability.get(userId);
            double currentValue = resources.get(resourceType);
            if (isIncrease) {
                resources.put(resourceType, currentValue + usage);
            } else {
                resources.put(resourceType, currentValue - usage);
            }
        }
        else{
            Logger.error("Control Flow Error", "Tried to update user resource usage of a non-user device");
        }
    }


    /**
     * Checks whether a user device has sufficient resources to host a service.
     * <p>
     * This method is intended for use with user devices only. It verifies whether the user device
     * identified by {@code userId} has enough available CPU (MIPS), RAM, and storage to host
     * the first service (service) in the application loop defined by the given
     * {@link PlacementRequest}.
     * <p>
     * The specific microservice to check must already be specified in the {@code placedMicroservices}
     * field of the {@code PlacementRequest}. If no microservice is placed on the user device in
     * the request, the method assumes the device is not involved and returns {@code true}.
     * <p>
     * The method also validates that the {@link Application} instance corresponding to the request
     * is present and that resource requirements are accurately matched against current availability.
     *
     * @param userId the ID of the user device
     * @param pr the placement request containing the application and placement information
     * @return {@code true} if the user device can host the microservice; {@code false} otherwise
     */
    public boolean userCanFit(int userId, PlacementRequest pr) {
        if (!userResourceAvailability.containsKey(userId)) {
            Logger.error("Control Flow Error", "Tried to check resources of a non-user device.");
            return false;
        }
        
        String moduleToCheck = null;
        for (String module : pr.getPlacedMicroservices().keySet()) {
            if (pr.getPlacedMicroservices().get(module) == userId) {
                moduleToCheck = module;
                break;
            }
        }
        
        if (moduleToCheck == null) {
            return true; // No modules placed on this user
        }
        
        Application app = null;
        for (SimEntity entity : CloudSim.getEntityList()) {
            if (entity instanceof MyMicroservicesController) {
                app = ((MyMicroservicesController) entity).getApplicationById(pr.getApplicationId());
                break;
            }
        }
        
        if (app == null) {
            return false;
        }
        AppModule appModule = app.getModuleByName(moduleToCheck);
        Map<String, Double> resources = userResourceAvailability.get(userId);
        
        return resources.get(ControllerComponent.CPU) >= appModule.getMips() &&
            resources.get(ControllerComponent.RAM) >= appModule.getRam() &&
            resources.get(ControllerComponent.STORAGE) >= appModule.getSize();
    }

    public Application getApplicationById(String appId) {
        return applications.get(appId);
    }

    /**
     * Registers a user device for periodic PR generation
     */
    public void registerUserDevice(MyFogDevice device) {
        if (device.getDeviceType().equals(MyFogDevice.GENERIC_USER)) {
            userDevices.add(device);
        }
    }

    public int getNextSequenceNumber(int sensorId) {
        int currentSeq = sensorToSequenceNumber.getOrDefault(sensorId, 0);
        int nextSeq = currentSeq + 1;
        sensorToSequenceNumber.put(sensorId, nextSeq);
        return nextSeq;
    }

    public void resetSequenceCounters() {
        sensorToSequenceNumber.clear();
    }

    public PlacementRequest createPlacementRequest(Sensor sensor, Map<String, Integer> placedMicroservices) {
        int sequenceNumber = getNextSequenceNumber(sensor.getId());

        // Create the placement request with the unique sequence number as the prId
        return new PlacementRequest(
                sensor.getAppId(),  // applicationId
                sensor.getId(),
                sequenceNumber,     // prId - now using a unique sequence per sensor
                sensor.getGatewayDeviceId(), // parent fog device
                placedMicroservices
        );
    }

    /**
     * Creates a new {@link PlacementRequest} with a unique, incremented placement request ID (prId)
     * for the given sensor.
     * <p>
     * Placement requests are uniquely identified by a combination of sensor ID and placement request ID (prId).
     * Each sensor maintains its own independent sequence of prIds. This method is part of a central component
     * responsible for tracking the latest prId for each sensor.
     * </p>
     * <p>
     * Given a {@code previousRequest}, this method generates the next prId for the same sensor and creates
     * a new {@link PlacementRequest} using that updated prId. The newly created request retains all other
     * details from the previous request (e.g., application ID, requester, placed microservices), ensuring
     * continuity while assigning a unique prId.
     * </p>
     *
     * @param previousRequest the previous {@link PlacementRequest} for the same sensor.
     * @return a new {@link PlacementRequest} with the next prId for the sensor.
     */
    public PlacementRequest createSubsequentPlacementRequest(PlacementRequest previousRequest) {
        int sequenceNumber = getNextSequenceNumber(previousRequest.getSensorId());

        return new PlacementRequest(
                previousRequest.getApplicationId(),
                previousRequest.getSensorId(),
                sequenceNumber,
                previousRequest.getRequester(),
                new LinkedHashMap<>(previousRequest.getPlacedMicroservices())
        );
    }

    /**
     * Periodically generates and sends placement requests from user devices based on a predefined set.
     * <p>
     * This method iterates over all user devices and attempts to generate new placement requests
     * by creating deep copies of corresponding {@link PlacementRequest} objects stored in
     * {@code placementRequestDelayMap}. These requests are permanent templates and remain unchanged;
     * the newly generated copies are entirely independent, with no shared references (e.g., new
     * {@code LinkedHashMap} is created for microservice placements).
     * <p>
     * Before sending a request, the method checks whether the user device has sufficient resources
     * using {@code userCanFit(...)}, ensuring that CPU, RAM, and storage requirements are met.
     * If the user device is eligible, the request is transmitted to it along with the corresponding
     * {@link Application} object.
     * <p>
     * This function is periodically scheduled using the static {@code prGenerationInterval}, which
     * defines the interval between consecutive invocations. In future versions, this interval may
     * be varied per-request by using the delay values from {@code placementRequestDelayMap}.
     */
    public void generatePeriodicPlacementRequests() {
        for (MyFogDevice userDevice : userDevices) {
            // Find existing placement request for this user
            PlacementRequest existingPR = null;
            for (PlacementRequest pr : placementRequestDelayMap.keySet()) {
                if (pr.getRequester() == userDevice.getId()) {
                    existingPR = pr;
                    break;
                }
            }
            
            if (existingPR != null) {
                PlacementRequest newPR = createSubsequentPlacementRequest(existingPR);

                if (userCanFit(userDevice.getId(), newPR)) {
                    JSONObject jsonSend = new JSONObject();
                    jsonSend.put("PR", newPR);
                    jsonSend.put("app", applications.get(newPR.getApplicationId()));
                    // TODO Should we use the delay from placementRequestDelayMap instead?
                    sendNow(userDevice.getId(), FogEvents.TRANSMIT_PR, jsonSend);
                } else {
                    String reason = "Insufficient resources on user device " + userDevice.getName();
                    Logger.error("Resource Error", reason);
                    MyMonitor.getInstance().recordFailedPR(newPR, reason);
                }
            }
        }
        
        send(getId(), prGenerationInterval, FogEvents.GENERATE_PERIODIC_PR);
    }


    protected void initiatePlacementRequestProcessingDynamic() {
//        for (PlacementRequest p : placementRequestDelayMap.keySet()) {
////            processPlacedModules(p);
//            JSONObject jsonSend = new JSONObject();
//            jsonSend.put("PR", p);
//            jsonSend.put("app", applications.get(p.getApplicationId()));
//            if (placementRequestDelayMap.get(p) == 0) {
//                sendNow(p.getRequester(), FogEvents.TRANSMIT_PR, jsonSend);
//            } else
//                send(p.getRequester(), placementRequestDelayMap.get(p), FogEvents.TRANSMIT_PR, jsonSend);
//        }
        if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC) {
            for (FogDevice f : fogDevices) {
                // todo Simon says for the Offline POC there are no proxy servers, so the cloud processes all PRs
                //  The second OR condition was added for Offline POC, whether it stays tbc
                if (((MyFogDevice) f).getDeviceType() == MyFogDevice.FON || ((MyFogDevice) f).getDeviceType() == MyFogDevice.CLOUD) {
                    sendNow(f.getId(), FogEvents.PROCESS_PRS);
                }
            }
        }
    }

    private void processUserResourceUpdate(SimEvent ev) {
        JSONObject objj = (JSONObject) ev.getData();
        int userId = (int) objj.get("id");
        AppModule module = (AppModule) objj.get("module");
        boolean isDecrease = (boolean) objj.get("isDecrease");

        // Update resource availability for user device
        if (userResourceAvailability.containsKey(userId)) {
            updateUserResourceUsage(userId, ControllerComponent.CPU, module.getMips(), !isDecrease);
            updateUserResourceUsage(userId, ControllerComponent.RAM, module.getRam(), !isDecrease);
            updateUserResourceUsage(userId, ControllerComponent.STORAGE, module.getSize(), !isDecrease);

            String action = isDecrease ? "Decreased" : "Restored";
            Logger.debug("Resource Management",
                    action + " resources for user " + CloudSim.getEntityName(userId) + " for module " + module.getName());
        } else {
            Logger.error("Resource Management Error",
                    "Tried to update resources for non-user device " + CloudSim.getEntityName(userId));
        }
    }

//    protected void initiatePlacementRequestProcessing() {
//        for (PlacementRequest p : placementRequestDelayMap.keySet()) {
//            // todo Install the starting modules of the PR
//            processPlacedModules(p);
//
//            int fonId = ((MyFogDevice) getFogDeviceById(p.getGatewayDeviceId())).getFonId();
//            if (placementRequestDelayMap.get(p) == 0) {
//                sendNow(fonId, FogEvents.RECEIVE_PR, p);
//            } else
//                // NOTE: Here is TRANSMIT_PR for the CONTROLLER. All other instances are for FogDevice
//                send(getId(), placementRequestDelayMap.get(p), FogEvents.TRANSMIT_PR, p);
//        }
//        if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC) {
//            for (FogDevice f : fogDevices) {
//                // todo Simon says for the Offline POC there are no proxy servers, so the cloud processes all PRs
//                // todo The second OR condition was added for Offline POC, whether it stays tbc
//                if (((MyFogDevice) f).getDeviceType() == MyFogDevice.FON || ((MyFogDevice) f).getDeviceType() == MyFogDevice.CLOUD) {
//                    sendNow(f.getId(), FogEvents.PROCESS_PRS);
//                }
//            }
//        }
//    }

//    protected void processPlacedModules(PlacementRequest p) {
//        for (String placed : p.getPlacedMicroservices().keySet()) {
//            int deviceId = p.getPlacedMicroservices().get(placed);
//            Application application = applications.get(p.getApplicationId());
//            sendNow(deviceId, FogEvents.ACTIVE_APP_UPDATE, application);
//            sendNow(deviceId, FogEvents.APP_SUBMIT, application);
//            sendNow(deviceId, FogEvents.LAUNCH_MODULE, new AppModule(application.getModuleByName(placed)));
//        }
//    }



//    private void transmitPr(SimEvent ev) {
//        PlacementRequest placementRequest = (PlacementRequest) ev.getData();
//        int fonId = ((MyFogDevice) getFogDeviceById(placementRequest.getGatewayDeviceId())).getFonId();
//        sendNow(fonId, FogEvents.RECEIVE_PR, placementRequest);
//    }


    protected void printQoSDetails() {
        System.out.println("=========================================");
        System.out.println("APPLICATION QOS SATISFACTION");
        System.out.println("=========================================");
        double success = 0;
        double total = 0;
        for (Integer loopId : TimeKeeper.getInstance().getLoopIdToLatencyQoSSuccessCount().keySet()) {
            success += TimeKeeper.getInstance().getLoopIdToLatencyQoSSuccessCount().get(loopId);
            total += TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loopId);
        }

        double successPercentage = success / total * 100;
        System.out.println("Makespan" + " ---> " + successPercentage);
    }

    protected void printCostDetails() {
        System.out.println("Cost of execution in cloud = " + getCloud().getTotalCost());
    }

    @Override
    public void shutdownEntity() {
    }

    protected void manageResources() {
        // todo Simon says this does nothing, doesnt it???
        send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
    }

    protected void printNetworkUsageDetails() {
        System.out.println("Total network usage = " + NetworkUsageMonitor.getNetworkUsage() / Config.MAX_SIMULATION_TIME);
    }

    protected FogDevice getCloud() {
        for (FogDevice dev : fogDevices)
            if (dev.getName().equals("cloud"))
                return dev;
        return null;
    }

    protected void printPowerDetails() {
        StringBuilder energyInfo = new StringBuilder();
        for (FogDevice fogDevice : fogDevices) {
            String energyPerDevice = fogDevice.getName() + " : Energy Consumed = " + fogDevice.getEnergyConsumption() + "\n";
            energyInfo.append(energyPerDevice);
        }
        System.out.println(energyInfo.toString());
    }

    protected String getStringForLoopId(int loopId) {
        for (String appId : applications.keySet()) {
            Application app = applications.get(appId);
            for (AppLoop loop : app.getLoops()) {
                if (loop.getLoopId() == loopId)
                    return loop.getModules().toString();
            }
        }
        return null;
    }

    protected void printTimeDetails() {
        TimeKeeper t = TimeKeeper.getInstance();
//        Calendar c = Calendar.getInstance();
        System.out.println("Simon START TIME : " + TimeKeeper.getInstance().getSimulationStartTime());
        System.out.println("Simon END TIME : " + Calendar.getInstance().getTimeInMillis());
        System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        for (Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()) {
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
            System.out.println(getStringForLoopId(loopId) + " ---> " + TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
        }
        System.out.println("=========================================");
        System.out.println("AVERAGE CPU EXECUTION DELAY PER TUPLE TYPE");
        System.out.println("=========================================");

        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }

        System.out.println("=========================================");
    }

    protected void printResourceConsumptionDetails() {
        // TODO Simon (040225) says print something useful instead of the device IDs
        //  Like utilisation? Standard deviation of utilisation? Ask Dr Cabrera
        //  Also NOTE that the entries are not sorted in key (timestamp) order, annoying
        //  Maybe need TreeMap
        Map<Double, List<MyHeuristic.DeviceState>> ss = MyMonitor.getInstance().getSnapshots();
        ss.forEach((key, value) ->
                value.forEach((deviceState) -> System.out.println(key + ": " + deviceState.getId())));
    }

    protected Map<Integer, Map<String, Double>> getResourceInfo(List<FogDevice> fogDevices) {
        Map<Integer, Map<String, Double>> resources = new HashMap<>();
        for (FogDevice device : fogDevices) {
            Map<String, Double> perDevice = new HashMap<>();
            perDevice.put(ControllerComponent.CPU, (double) device.getHost().getTotalMips());
            perDevice.put(ControllerComponent.RAM, (double) device.getHost().getRam());
            perDevice.put(ControllerComponent.STORAGE, (double) device.getHost().getStorage());
            resources.put(device.getId(), perDevice);
        }
        return resources;
    }


    public void submitPlacementRequests(List<PlacementRequest> placementRequests, int delay) {
        for (PlacementRequest p : placementRequests) {
            placementRequestDelayMap.put(p, delay);
        }
    }

    protected FogDevice getFogDeviceById(int id) {
        for (FogDevice f : fogDevices) {
            if (f.getId() == id)
                return f;
        }
        return null;
    }

    protected void connectWithLatencies() {
        for (FogDevice fogDevice : fogDevices) {
            if (fogDevice.getParentId() >= 0) {
                FogDevice parent = (FogDevice) CloudSim.getEntity(fogDevice.getParentId());
                if (parent == null)
                    continue;
                double latency = fogDevice.getUplinkLatency();
                parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
                parent.getChildrenIds().add(fogDevice.getId());
            }
        }
    }

    protected List<FogDevice> getDevicesForFON(FogDevice f) {
        List<FogDevice> fogDevices = new ArrayList<>();
        fogDevices.add(f);
        ((MyFogDevice) f).setFonID(f.getId());
        List<FogDevice> connected = new ArrayList<>();
        connected.add(f);
        boolean changed = true;
        while (changed) {
            changed = false;
            List<FogDevice> rootNodes = new ArrayList<>();
            for (FogDevice d : connected)
                rootNodes.add(d);
            for (FogDevice rootD : rootNodes) {
                for (int child : rootD.getChildrenIds()) {
                    FogDevice device = getFogDeviceById(child);
                    connected.add(device);
                    if (!fogDevices.contains(device)) {
                        MyFogDevice mfd = (MyFogDevice) device;
//                        if (mfd.getDeviceType() == MyFogDevice.FCN) {
                        fogDevices.add(mfd);
//                        }
                        mfd.setFonID(f.getId());
                        changed = true;
//                    FogDevice device = getFogDeviceById(cluster);
//                    connected.add(device);
                    }
                }
//                for (int cluster : ((MyFogDevice) rootD).getClusterMembers()) {
//                    if (!fogDevices.contains(device)) {
//                        fogDevices.add(device);
//                        ((MyFogDevice) device).setFonID(f.getId());
//                        changed = true;
//                    }
//                }
                connected.remove(rootD);

            }
        }
        int parentId = f.getParentId();
        if (parentId != -1) {
            MyFogDevice fogDevice = (MyFogDevice) getFogDeviceById(parentId);
            if (fogDevice.getDeviceType().equals(MyFogDevice.CLOUD))
                fogDevices.add(fogDevice);
        }

        return fogDevices;
    }

    public Map<Integer, Map<String, Double>> getUserResourceAvailability() {
        return userResourceAvailability;
    }

//    protected static void createClusterConnections(int levelIdentifier, List<FogDevice> fogDevices, Double clusterLatency) {
//        Map<Integer, List<FogDevice>> fogDevicesByParent = new HashMap<>();
//        for (FogDevice fogDevice : fogDevices) {
//            if (fogDevice.getLevel() == levelIdentifier) {
//                if (fogDevicesByParent.containsKey(fogDevice.getParentId())) {
//                    fogDevicesByParent.get(fogDevice.getParentId()).add(fogDevice);
//                } else {
//                    List<FogDevice> sameParentList = new ArrayList<>();
//                    sameParentList.add(fogDevice);
//                    fogDevicesByParent.put(fogDevice.getParentId(), sameParentList);
//                }
//            }
//        }
//
//        for (int parentId : fogDevicesByParent.keySet()) {
//            List<Integer> clusterNodeIds = new ArrayList<>();
//            for (FogDevice fogdevice : fogDevicesByParent.get(parentId)) {
//                clusterNodeIds.add(fogdevice.getId());
//            }
//            for (FogDevice fogDevice : fogDevicesByParent.get(parentId)) {
//                List<Integer> clusterNodeIdsTemp = new ArrayList<>(clusterNodeIds);
//                clusterNodeIds.remove((Object) fogDevice.getId());
//                ((MyFogDevice) fogDevice).setClusterMembers(clusterNodeIds);
//                Map<Integer, Double> latencyMap = new HashMap<>();
//                for (int id : clusterNodeIds) {
//                    latencyMap.put(id, clusterLatency);
//                }
//                ((MyFogDevice) fogDevice).setClusterMembersToLatencyMap(latencyMap);
//                ((MyFogDevice) fogDevice).setIsInCluster(true);
//                clusterNodeIds = clusterNodeIdsTemp;
//
//            }
//        }
//    }

//    protected void printClusterConnections() {
//        StringBuilder clusterString = new StringBuilder();
//        clusterString.append("Cluster formation : ");
//        // <ParentNode,ClusterNodes> Assuming than clusters are formed among nodes with same parent
//        HashMap<String, List<MyFogDevice>> clusters = new HashMap<>();
//        for (FogDevice f : fogDevices) {
//            MyFogDevice cDevice = (MyFogDevice) f;
//            if (cDevice.getIsInCluster()) {
//                FogDevice parent = getFogDeviceById(cDevice.getParentId());
//                if (clusters.containsKey(parent.getName()))
//                    clusters.get(parent.getName()).add(cDevice);
//                else
//                    clusters.put(parent.getName(), new ArrayList<>(Arrays.asList(cDevice)));
//            }
//        }
//        for (String parent : clusters.keySet()) {
//            List<MyFogDevice> clusterNodes = clusters.get(parent);
//            clusterString.append("Parent node : " + parent + " -> cluster Nodes : ");
//            for (MyFogDevice device : clusterNodes) {
//                int count = device.getClusterMembers().size();
//                clusterString.append(device.getName() + ", ");
//                for (Integer deviceId : device.getClusterMembers()) {
//                    if (!clusterNodes.contains(getFogDeviceById(deviceId))) {
//                        Logger.error("Cluster formation Error", "Error : " + getFogDeviceById(deviceId).getName() + " is added as a cluster node of " + device.getName());
//                    }
//                }
//                if (count + 1 != clusterNodes.size())
//                    Logger.error("Cluster formation Error", "Error : number of cluster nodes does not match");
//            }
//
//            clusterString.append("\n");
//        }
//        System.out.println(clusterString);
//    }

}

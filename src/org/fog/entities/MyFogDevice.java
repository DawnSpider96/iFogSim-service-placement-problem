package org.fog.entities;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.placement.MicroservicePlacementLogic;
import org.fog.placement.MyPlacementLogicOutput;
import org.fog.placement.PlacementLogicOutput;
import org.fog.utils.*;
import org.json.simple.JSONObject;

import java.util.*;

/**
 * Created by Samodha Pallewatta
 */
public class MyFogDevice extends FogDevice {

	/**
	 * Device type (1.client device 2.FCN 3.FON 4.Cloud)
	 * in this work client device only holds the clientModule of the app and does not participate in processing and placement of microservices ( microservices can be shared among users,
	 * thus for security resons client devices are not used for that)
	 */
	protected String deviceType = null;
//	public static final String CLIENT = "client";
	public static final String AMBULANCE_USER = "ambulanceUser";
	public static final String OPERA_USER = "operaUser";
	public static final String GENERIC_USER = "genericUser"; // random movement, no objective
	public static final String FCN = "fcn"; // fog computation node
	public static final String FON = "fon"; // fog orchestration node
	public static final String CLOUD = "cloud"; // cloud datacenter

	public int toClient = 0;


	/**
	 * closest FON id. If this device is a FON its own id is assigned
	 */
	protected int fonID = -1;
	protected int sensorID = -1;

	/**
	 * used to forward tuples towards the destination device
	 * map of <destinationID,nextDeviceID> based on shortest path.
	 */
	protected Map<Integer, Integer> routingTable = new HashMap<>();


	protected ControllerComponent controllerComponent;

	protected List<PlacementRequest> placementRequests = new ArrayList<>();

	public MyFogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth, double clusterLinkBandwidth, double uplinkLatency, double ratePerMips, String deviceType) throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
		setClusterLinkBandwidth(clusterLinkBandwidth);
		setDeviceType(deviceType);
	}

	@Override
	protected void registerOtherEntity() {

		// for energy consumption update
		sendNow(getId(), FogEvents.RESOURCE_MGMT);

	}

	@Override
	protected void processOtherEvent(SimEvent ev) {
		switch (ev.getTag()) {
			case FogEvents.PROCESS_PRS:
				processPlacementRequests();
				break;
			case FogEvents.RECEIVE_PR:
				addPlacementRequest((PlacementRequest) ev.getData());
				break;
			case FogEvents.UPDATE_SERVICE_DISCOVERY:
				updateServiceDiscovery(ev);
				break;
			case FogEvents.TRANSMIT_PR:
				JSONObject object = (JSONObject) ev.getData();
				PlacementRequest pr = (PlacementRequest) object.get("PR");
				Application application = (Application) object.get("app");
				/// Periodically resend the same placement request
				PlacementRequest prNew = new PlacementRequest(pr.getApplicationId(), pr.getPlacementRequestId(), pr.getGatewayDeviceId(), new HashMap<String, Integer>(pr.getPlacedMicroservices()));
				Map<String, Object> newObject = new HashMap<>();
				newObject.put("PR", prNew);
				newObject.put("app", application);
				JSONObject jsonObject = new JSONObject(newObject);
				send(getId(), MicroservicePlacementConfig.PLACEMENT_GENERATE_INTERVAL, FogEvents.TRANSMIT_PR, jsonObject);

				installStartingModule(pr, application);
				transmitPR(pr);
				break;
			case FogEvents.MANAGEMENT_TUPLE_ARRIVAL:
				processManagementTuple(ev);
				break;
			case FogEvents.UPDATE_RESOURCE_INFO:
				updateResourceInfo(ev);
				break;
			case FogEvents.MODULE_UNINSTALL:
				moduleUninstall(ev);
				break;
			case FogEvents.NODE_EXECUTION_FINISHED:
				finishNodeExecution(ev);
				break;
			case FogEvents.EXECUTION_START_REQUEST:
				startExecution(ev);
				break;
//			case FogEvents.START_DYNAMIC_CLUSTERING:
//				//This message is received by the devices to start their clustering
//				processClustering(this.getParentId(), this.getId(), ev);
//				updateClusterConsInRoutingTable();
//				break;
			default:
				super.processOtherEvent(ev);
				break;
		}
	}

	private void updateResourceInfo(SimEvent ev) {
		Pair<Integer, Map<String, Double>> pair = (Pair<Integer, Map<String, Double>>) ev.getData();
		int deviceId = pair.getFirst();
		getControllerComponent().updateResourceInfo(deviceId, pair.getSecond());
	}

	public Map<String, Double> getResourceAvailabilityOfDevice() {
		return getControllerComponent().resourceAvailability.get(getId());
	}


	public void addPlacementRequest(PlacementRequest pr) {
		placementRequests.add(pr);
		if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.SEQUENTIAL && placementRequests.size() == 1)
			sendNow(getId(), FogEvents.PROCESS_PRS);
	}

//	private void sendThroughFreeClusterLink(Tuple tuple, Integer clusterNodeID) {
//		double networkDelay = tuple.getCloudletFileSize() / getClusterLinkBandwidth();
//		setClusterLinkBusy(true);
//		double latency = (getClusterMembersToLatencyMap()).get(clusterNodeID);
//		send(getId(), networkDelay, FogEvents.UPDATE_CLUSTER_TUPLE_QUEUE);
//
//		if (tuple instanceof ManagementTuple) {
//			send(clusterNodeID, networkDelay + latency + ((ManagementTuple) tuple).processingDelay, FogEvents.MANAGEMENT_TUPLE_ARRIVAL, tuple);
//			//todo
////            if (Config.ENABLE_NETWORK_USAGE_AT_PLACEMENT)
////                NetworkUsageMonitor.sendingManagementTuple(latency, tuple.getCloudletFileSize());
//		} else {
//			send(clusterNodeID, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
//			NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
//		}
//	}

	protected void setDeviceType(String deviceType) {
		if (deviceType.equals(MyFogDevice.GENERIC_USER) || deviceType.equals(MyFogDevice.FCN) ||
				deviceType.equals(MyFogDevice.FON) || deviceType.equals(MyFogDevice.CLOUD) ||
				deviceType.equals(MyFogDevice.AMBULANCE_USER) || deviceType.equals(MyFogDevice.OPERA_USER))
			this.deviceType = deviceType;
		else
			Logger.error("Incompatible Device Type", "Device type not included in device type enums in MyFogDevice class");
	}

	public String getDeviceType() {
		return deviceType;
	}

	public void addRoutingTable(Map<Integer, Integer> routingTable) {
		this.routingTable = routingTable;
	}

	public Map<Integer, Integer> getRoutingTable() {
		return routingTable;
	}

	protected void processTupleArrival(SimEvent ev) {

		Tuple tuple = (Tuple) ev.getData();

		Logger.debug(getName(), "Received tuple " + tuple.getCloudletId() + " with tupleType = " + tuple.getTupleType() + "\t| Source : " +
				CloudSim.getEntityName(ev.getSource()) + " | Dest : " + CloudSim.getEntityName(ev.getDestination()));

		if (deviceType.equals(MyFogDevice.CLOUD)) {
			updateCloudTraffic();
		}

		send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);

		if (FogUtils.appIdToGeoCoverageMap.containsKey(tuple.getAppId())) {
		}

		if (tuple.getDirection() == Tuple.ACTUATOR) {
			sendTupleToActuator(tuple);
			return;
		}

		// TODO Simon says this might need to change, seriously
		//  With my current implementation of OnlinePOC it so happens that there is only one VM at a time
		//  but surely this cannot be the default???
		// Why are we taking the first VM only???
		if (getHost().getVmList().size() > 0) {
			AppModule operator = null;
			for (Vm vm : getHost().getVmList()){
				AppModule a = (AppModule) vm;
				if (Objects.equals(a.getName(), tuple.getDestModuleName())){
					operator = a;
					break;
				}
			}
			if (CloudSim.clock() > 0) {
				getHost().getVmScheduler().deallocatePesForVm(operator);
				getHost().getVmScheduler().allocatePesForVm(operator, new ArrayList<Double>() {
					protected static final long serialVersionUID = 1L;

					{
						add((double) getHost().getTotalMips());
					}
				});
			}
		}

		if (deviceType.equals(MyFogDevice.CLOUD) && tuple.getDestModuleName() == null) {
			sendNow(getControllerId(), FogEvents.TUPLE_FINISHED, null);
		}

		// these are resultant tuples and created periodic tuples
		if (tuple.getDestinationDeviceId() == -1) {
			// ACTUATOR tuples already handled above. Only UP and DOWN left
			if (tuple.getDirection() == Tuple.UP) {
				int destination = controllerComponent.getDestinationDeviceId(tuple.getDestModuleName());
				if (destination == -1) {
					System.out.println("Service DiscoveryInfo missing. Tuple routing stopped for : " + tuple.getDestModuleName());
					return;
				}
				tuple.setDestinationDeviceId(destination);
				tuple.setSourceDeviceId(getId());
			} else if (tuple.getDirection() == Tuple.DOWN) {
				int destination = tuple.getDeviceForMicroservice(tuple.getDestModuleName());
				tuple.setDestinationDeviceId(destination);
				tuple.setSourceDeviceId(getId());
			}
		}

		if (tuple.getDestinationDeviceId() == getId()) {
			int vmId = -1;
			for (Vm vm : getHost().getVmList()) {
				if (((AppModule) vm).getName().equals(tuple.getDestModuleName()))
					vmId = vm.getId();
			}
			if (vmId < 0
					|| (tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) &&
					tuple.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId)) {
				return;
			}
			tuple.setVmId(vmId);
			tuple.addToTraversedMicroservices(getId(), tuple.getDestModuleName());

			updateTimingsOnReceipt(tuple);

			executeTuple(ev, tuple.getDestModuleName());
		} else {
			if (tuple.getDestinationDeviceId() != -1) {
				int nextDeviceToSend = routingTable.get(tuple.getDestinationDeviceId());
				if (nextDeviceToSend == parentId)
					sendUp(tuple);
				else if (childrenIds.contains(nextDeviceToSend))
					sendDown(tuple, nextDeviceToSend);
				else if (getClusterMembers().contains(nextDeviceToSend))
					sendToCluster(tuple, nextDeviceToSend);
				else {
					Logger.error("Routing error", "Routing table of " + getName() + "does not contain next device for destination Id" + tuple.getDestinationDeviceId());

				}
			} else {
				// TODO DONT DELETE BREAKPOINT, I dont know what control flow would actually lead to this, so I put a breakpoint
				if (tuple.getDirection() == Tuple.DOWN) {
					if (appToModulesMap.containsKey(tuple.getAppId())) {
						if (appToModulesMap.get(tuple.getAppId()).contains(tuple.getDestModuleName())) {
							int vmId = -1;
							for (Vm vm : getHost().getVmList()) {
								if (((AppModule) vm).getName().equals(tuple.getDestModuleName()))
									vmId = vm.getId();
							}
							if (vmId < 0
									|| (tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) &&
									tuple.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId)) {
								return;
							}
							tuple.setVmId(vmId);
							//Logger.error(getName(), "Executing tuple for operator " + moduleName);

							updateTimingsOnReceipt(tuple);

							executeTuple(ev, tuple.getDestModuleName());

							return;
						}
					}


					for (int childId : getChildrenIds())
						sendDown(tuple, childId);

				} else {
					Logger.error("Routing error", "Destination id -1 for UP tuple");
				}

			}
		}
	}

	// todo Simon says we call the uninstallation of modules here
	//  It checks ALL the VMs on the PowerHost belonging to this FogDevice (datacenter) to see if their Cloudlet's execution is complete
	//  Since OnlinePOC services one Cloudlet per VM, we will uninstall after verifying that Cloudlet execution is complete
	@Override
	protected void checkCloudletCompletion() {
		boolean cloudletCompleted = false;
		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {

					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					// todo Simon says that for OnlinePOC, every policy (AppModuleAllocationPolicy) should only be supervising ONE PowerHost
					Cloudlet cl2 = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl2 != null) {
						Logger.error("Cloudlet Finished List size error","Expected exactly one finished cloudlet in the CloudletFinishedList for VM ID " + vm.getId() + ", but found more.");
					}
					if (cl == null) {
						Logger.error("Cloudlet Finished List size error","Expected exactly one finished cloudlet in the CloudletFinishedList for VM ID " + vm.getId() + ", but found none.");
					}
					else {
						cloudletCompleted = true;
						Tuple tuple = (Tuple) cl;
						TimeKeeper.getInstance().tupleEndedExecution(tuple);
						Application application = getApplicationMap().get(tuple.getAppId());
						Logger.debug(getName(), "Completed execution of tuple " + tuple.getCloudletId() + " on " + tuple.getDestModuleName());
						List<Tuple> resultantTuples = application.getResultantTuples(tuple.getDestModuleName(), tuple, getId(), vm.getId());
						for (Tuple resTuple : resultantTuples) {
							resTuple.setModuleCopyMap(new HashMap<String, Integer>(tuple.getModuleCopyMap()));
							resTuple.getModuleCopyMap().put(((AppModule) vm).getName(), vm.getId());
							updateTimingsOnSending(resTuple);
							sendToSelf(resTuple);
						}
						sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

						JSONObject obj = new JSONObject();
						obj.put("module", vm);
						sendNow(getId(), FogEvents.MODULE_UNINSTALL, obj);

						JSONObject objj = new JSONObject();
						objj.put("module", vm);
						objj.put("id", getId());
						if (deviceType == FCN) sendNow(getFonId(), FogEvents.NODE_EXECUTION_FINISHED, objj);

						// Retrieve the map for CPU usages
//						Map<Integer, Map<Double, Map<String, List<Double>>>> cpuUsages = MyMonitor.getCpuUsages();
//
//						if (!cpuUsages.containsKey(getId())) {
//							// If not, initialize it with a new map
//							cpuUsages.put(getId(), new HashMap<>());
//						}
//
//						cpuUsages.get(getId()).put(CloudSim.clock(), getHost().getVmScheduler().getMipsMap());

					}
				}
			}
		}
		if (cloudletCompleted)
			updateAllocatedMips(null);
	}

	public void finishNodeExecution(SimEvent ev) {
		JSONObject objj = (JSONObject) ev.getData();
		controllerComponent.finishNodeExecution(objj);
	}

	/**
	 * Both cloud and FON participates in placement process. But Simon says there are no FON devices.
	 */
	public void initializeController(LoadBalancer loadBalancer, MicroservicePlacementLogic mPlacement, Map<Integer, Map<String, Double>> resourceAvailability, Map<String, Application> applications, List<FogDevice> fogDevices) {
		if (getDeviceType() == MyFogDevice.FON || getDeviceType() == MyFogDevice.CLOUD) {
			controllerComponent = new ControllerComponent(getId(), loadBalancer, mPlacement, resourceAvailability, applications, fogDevices);
		} else
			Logger.error("Controller init failed", "FON controller initialized for device " + getName() + " of type " + getDeviceType());
	}

	/**
	 * FCN and Client devices
	 */
	public void initializeController(LoadBalancer loadBalancer) {
		if (getDeviceType() != MyFogDevice.CLOUD) {
			controllerComponent = new ControllerComponent(getId(), loadBalancer);
			controllerComponent.updateResources(getId(), ControllerComponent.CPU, getHost().getTotalMips());
			controllerComponent.updateResources(getId(), ControllerComponent.RAM, getHost().getRam());
			controllerComponent.updateResources(getId(), ControllerComponent.STORAGE, getHost().getStorage());
		}
	}

	public ControllerComponent getControllerComponent() {
		return controllerComponent;
	}

	public List<PlacementRequest> getPlacementRequests() {
		return placementRequests;
	}

	public void setPlacementRequests(List<PlacementRequest> placementRequests) {
		this.placementRequests = placementRequests;
	}

	protected void processPlacementRequests() {

		if (!this.deviceType.equals(MyFogDevice.CLOUD)) {
			Logger.error("FON exists error", "Placement Request NOT being processed by cloud! Check if device type is FON.");
		}

		if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC && placementRequests.size() == 0) {
			send(getId(), MicroservicePlacementConfig.PLACEMENT_PROCESS_INTERVAL, FogEvents.PROCESS_PRS);
			return;
		}
		long startTime = System.nanoTime();

		List<PlacementRequest> placementRequests = new ArrayList<>();

		if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC) {
			placementRequests.addAll(this.placementRequests);
			this.placementRequests.clear();
		} else if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.SEQUENTIAL) {
			placementRequests.add(this.placementRequests.get(0));
			this.placementRequests.remove(0);
		}

		MyPlacementLogicOutput placementLogicOutput = (MyPlacementLogicOutput) getControllerComponent().executeApplicationPlacementLogic(placementRequests);
		long endTime = System.nanoTime();
		System.out.println("Placement Algorithm Completed. Time : " + (endTime - startTime) / 1e6);

		Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice = placementLogicOutput.getPerDevice();
		Map<Integer, List<Pair<String, Integer>>> serviceDicovery = placementLogicOutput.getServiceDiscoveryInfo();
		Map<PlacementRequest, Integer> placementRequestStatus = placementLogicOutput.getPrStatus();
		Map<PlacementRequest, Integer> targets = placementLogicOutput.getTargets();
		int fogDeviceCount = 0; // todo Simon says I still don't know what this variable does. Currently unused (050125).
		StringBuilder placementString = new StringBuilder();

		// Simon says (140125) we send perDevice and all updated PRs to FogBroker
		// before sending deployment requests to devices specified in perDevice
		JSONObject forFogBroker = new JSONObject();
		forFogBroker.put("targets", targets);
		forFogBroker.put("perDevice", perDevice);
		forFogBroker.put("batchNumber", FogBroker.getBatchNumber());

		sendNow(CloudSim.getFogBrokerId(), FogEvents.RECEIVE_PLACEMENT_DECISION, forFogBroker);

		for (int deviceID : perDevice.keySet()) {
			MyFogDevice f = (MyFogDevice) CloudSim.getEntity(deviceID);
			if (!f.getDeviceType().equals(MyFogDevice.CLOUD))
				fogDeviceCount++;
			placementString.append(CloudSim.getEntity(deviceID).getName() + " : ");
			for (Application app : perDevice.get(deviceID).keySet()) {
				if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC") {
					Logger.error("Simulation static mode error", "Simulation should not be static.");
				}
			}
			if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC") {
				transmitModulesToDeploy(deviceID, perDevice.get(deviceID), FogBroker.getBatchNumber());
			}
			placementString.append("\n");
		}
		FogBroker.setBatchNumber(FogBroker.getBatchNumber() + 1);

		System.out.println(placementString.toString());
		for (int clientDevice : serviceDicovery.keySet()) {
			for (Pair serviceData : serviceDicovery.get(clientDevice)) {
				if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC") {
					transmitServiceDiscoveryData(clientDevice, serviceData);
				} else if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC") {
					Logger.error("Simulation static mode error", "Simulation should not be static.");
				}
			}
		}

		for (PlacementRequest pr : placementRequestStatus.keySet()) {
			if (placementRequestStatus.get(pr) != -1) {
				if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC")
					transmitPR(pr, placementRequestStatus.get(pr));

				else if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC")
//					sendNow(placementRequestStatus.get(pr), FogEvents.RECEIVE_PR, pr);
					Logger.error("Simulation static mode error", "Simulation should not be static.");

			}
		}

		if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC)
			send(getId(), MicroservicePlacementConfig.PLACEMENT_PROCESS_INTERVAL, FogEvents.PROCESS_PRS);
		else if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.SEQUENTIAL && !this.placementRequests.isEmpty())
			sendNow(getId(), FogEvents.PROCESS_PRS);
	}

	public List<Integer> getClientServiceNodeIds(Application application, String
			microservice, Map<String, Integer> placed, Map<String, Integer> placementPerPr) {
		List<String> clientServices = getClientServices(application, microservice);
		List<Integer> nodeIDs = new LinkedList<>();
		for (String clientService : clientServices) {
			if (placed.get(clientService) != null)
				nodeIDs.add(placed.get(clientService));
			else
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

	protected void updateServiceDiscovery(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		Pair<String, Integer> placement = (Pair<String, Integer>) object.get("service data");
		String action = (String) object.get("action");
		if (action.equals("ADD"))
			this.controllerComponent.addServiceDiscoveryInfo(placement.getFirst(), placement.getSecond());
		else if (action.equals("REMOVE"))
			this.controllerComponent.removeServiceDiscoveryInfo(placement.getFirst(), placement.getSecond());
	}

	// todo NOTE: Triggered by LAUNCH_MODULE event
	protected void processModuleArrival(SimEvent ev) {
		// assumed that a new object of AppModule is sent
		//todo what if an existing module is sent again in another placement cycle -> vertical scaling instead of having two vms
		AppModule module = (AppModule) ev.getData();
		String appId = module.getAppId();
		if (!appToModulesMap.containsKey(appId)) {
			appToModulesMap.put(appId, new ArrayList<String>());
		}
		if (!appToModulesMap.get(appId).contains(module.getName())) {
			appToModulesMap.get(appId).add(module.getName());
//			processVmCreate(ev, false);
			// Adds entry to mipmap of the VMScheduler of the Host
			boolean result = getVmAllocationPolicy().allocateHostForVm(module);
			if (result) {
				getVmList().add(module);
				if (module.isBeingInstantiated()) {
					module.setBeingInstantiated(false);
				}
				// todo Simon says no periodic tuples because the apps have no periodic edges.
				//  Commented out just in case
//				initializePeriodicTuples(module);
				// getAllocatedMipsforVm checks the mipmap of the VMScheduler of the Host
				//
				module.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(module).getVmScheduler()
						.getAllocatedMipsForVm(module));

				System.out.println("Module " + module.getName() + " created on " + getName() + " under processModuleArrival()");
				Logger.debug("Module deploy success", "Module " + module.getName() + " placement on " + getName() + " successful. vm id : " + module.getId());
			} else {
				Logger.error("Module deploy error", "Module " + module.getName() + " placement on " + getName() + " failed");
				System.out.println("Module " + module.getName() + " placement on " + getName() + " failed");
			}
		} else {
			// todo Simon says this should be where the vertical scaling occurs.
			//  Temporarily we allow the installation of a second module
			System.out.println("Module " + module.getName() + " already deployed on " + getName());
			boolean result = getVmAllocationPolicy().allocateHostForVm(module);
			if (result) {
				getVmList().add(module);
				if (module.isBeingInstantiated()) {
					module.setBeingInstantiated(false);
				}
				module.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(module).getVmScheduler()
						.getAllocatedMipsForVm(module));

				System.out.println("Nevertheless, Module " + module.getName() + " created on " + getName() + " under processModuleArrival()");
				Logger.debug("Module deploy success", "Module " + module.getName() + " placement on " + getName() + " successful. vm id : " + module.getId());
			} else {
				Logger.error("Module deploy error", "Module " + module.getName() + " placement on " + getName() + " failed");
				System.out.println("Module " + module.getName() + " placement on " + getName() + " failed");
			}
		}


	}

	@Override
	protected void moduleReceive(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		AppModule appModule = (AppModule) object.get("module");
		Application app = (Application) object.get("application");
		System.out.println(CloudSim.clock() + getName() + " is receiving " + appModule.getName());

		sendNow(getId(), FogEvents.APP_SUBMIT, app);
		sendNow(getId(), FogEvents.LAUNCH_MODULE, appModule);
		ModuleLaunchConfig moduleLaunchConfig = new ModuleLaunchConfig(appModule, 1);
		sendNow(getId(), FogEvents.LAUNCH_MODULE_INSTANCE, moduleLaunchConfig);

		NetworkUsageMonitor.sendingModule((double) object.get("delay"), appModule.getSize());
		MigrationDelayMonitor.setMigrationDelay((double) object.get("delay"));
	}


	@Override
	protected void moduleSend(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		AppModule appModule = (AppModule) object.get("module");
		System.out.println(getName() + " is sending " + appModule.getName());
		NetworkUsageMonitor.sendingModule((double) object.get("delay"), appModule.getSize());
		MigrationDelayMonitor.setMigrationDelay((double) object.get("delay"));

		if (moduleInstanceCount.containsKey(appModule.getAppId()) && moduleInstanceCount.get(appModule.getAppId()).containsKey(appModule.getName())) {
			int moduleCount = moduleInstanceCount.get(appModule.getAppId()).get(appModule.getName());
			if (moduleCount > 1)
				moduleInstanceCount.get(appModule.getAppId()).put(appModule.getName(), moduleCount - 1);
			else {
				moduleInstanceCount.get(appModule.getAppId()).remove(appModule.getName());
				appToModulesMap.get(appModule.getAppId()).remove(appModule.getName());
				sendNow(getId(), FogEvents.RELEASE_MODULE, appModule);
			}
		}
	}

	protected void moduleUninstall(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		AppModule appModule = (AppModule) object.get("module");
		System.out.println(getName() + " is uninstalling " + appModule.getName());
//		NetworkUsageMonitor.sendingModule((double) object.get("delay"), appModule.getSize());
//		MigrationDelayMonitor.setMigrationDelay((double) object.get("delay"));

		if (moduleInstanceCount.containsKey(appModule.getAppId()) && moduleInstanceCount.get(appModule.getAppId()).containsKey(appModule.getName())) {
			int moduleCount = moduleInstanceCount.get(appModule.getAppId()).get(appModule.getName());
			if (moduleCount > 1)
				moduleInstanceCount.get(appModule.getAppId()).put(appModule.getName(), moduleCount - 1);
			else {
				moduleInstanceCount.get(appModule.getAppId()).remove(appModule.getName());
				appToModulesMap.get(appModule.getAppId()).remove(appModule.getName());
			}
			sendNow(getId(), FogEvents.RELEASE_MODULE, appModule);
		} else {
			Logger.error("Module uninstall error", "Module " + appModule.getName() + " not found on " + getName());
			System.out.println("Module " + appModule.getName() + " not found on " + getName());
		}
	}


	public void setFonID(int fonDeviceId) {
		fonID = fonDeviceId;
	}

	public int getFonId() {
		return fonID;
	}

	/**
	 * Cloud will not allocate the clientModule (starting module) because onus is on Users.
	 * Hence, User will install it on self before transmitting PR to cloud.
	 * PR will contain clientModule under "placedMicroservices" field.
	 *
	 */

	private void installStartingModule(PlacementRequest pr, Application application) {
		// Find the first module placed on the given device (there should only be one)
		String placedModule = pr.getPlacedMicroservices()
				.entrySet()
				.stream()
				.filter(entry -> entry.getValue().equals(getId()))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);

		if (placedModule != null) {
			// Update the application and launch the module on the device
			sendNow(getId(), FogEvents.ACTIVE_APP_UPDATE, application);
			sendNow(getId(), FogEvents.APP_SUBMIT, application);
			AppModule am = new AppModule(application.getModuleByName(placedModule));
			sendNow(getId(), FogEvents.LAUNCH_MODULE, am);
			ModuleLaunchConfig moduleLaunchConfig = new ModuleLaunchConfig(am, 1);
			sendNow(getId(), FogEvents.LAUNCH_MODULE_INSTANCE, moduleLaunchConfig);
		} else {
			Logger.error("Module Placement", "Placement Request with target " + getId() + " for PlacementRequest " + pr.getPlacementRequestId() + "sent to this device instead.");
		}
	}

	/**
	 * Used by Client Devices to generate management tuple with pr and send it to cloud
	 *
	 * @param pr
	 */
	private void transmitPR(PlacementRequest pr) {
		transmitPR(pr, fonID);
	}

	private void transmitPR(PlacementRequest placementRequest, Integer fonID) {
		// todo Simon says this might be the part where edge server (gateway device connected to sensor) forwards the PR to cloud!!!
		// todo NOTE delay between self and the cloud is taken into account using the ManagementTuple
		ManagementTuple prTuple = new ManagementTuple(placementRequest.getApplicationId(), FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.PLACEMENT_REQUEST);
		prTuple.setPlacementRequest(placementRequest);
		prTuple.setDestinationDeviceId(fonID);
		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, prTuple);
	}

	private void transmitServiceDiscoveryData(int clientDevice, Pair serviceData) {
		ManagementTuple sdTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.SERVICE_DISCOVERY_INFO);
		sdTuple.setServiceDiscoveryInfor(serviceData);
		sdTuple.setDestinationDeviceId(clientDevice);
		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, sdTuple);
	}

	private void transmitModulesToDeploy(int deviceID, Map<Application, List<ModuleLaunchConfig>> applicationListMap, int batchNumber) {
		ManagementTuple moduleTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.DEPLOYMENT_REQUEST);
		moduleTuple.setDeployementSet(applicationListMap);
		moduleTuple.setDestinationDeviceId(deviceID);
		moduleTuple.setBatchNumber(batchNumber);
		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, moduleTuple);
	}

//	private void transmitModulesToDeploy(int deviceID, Map<Application, List<ModuleLaunchConfig>> applicationListMap, PlacementRequest pr) {
//		// Simon says this is sent only to gateway devices
//		ManagementTuple moduleTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.DEPLOYMENT_REQUEST);
//		moduleTuple.setDeployementSet(applicationListMap);
//		moduleTuple.setDestinationDeviceId(deviceID);
//		moduleTuple.setPlacementRequest(pr);
//		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, moduleTuple);
//	}

	private void processManagementTuple(SimEvent ev) {
		ManagementTuple tuple = (ManagementTuple) ev.getData();
		if (tuple.getDestinationDeviceId() == getId()) {
			switch (tuple.managementTupleType) {
				case ManagementTuple.PLACEMENT_REQUEST:
					// TODO Simon says we might have to change things such that RECEIVE_PR is sent to cloud straight
					// todo Especially since this (management tuple) simulates the request travelling up physically through the network
					sendNow(getId(), FogEvents.RECEIVE_PR, tuple.getPlacementRequest());
					break;

				case ManagementTuple.SERVICE_DISCOVERY_INFO:
					JSONObject serviceDiscoveryAdd = new JSONObject();
					serviceDiscoveryAdd.put("service data", tuple.getServiceDiscoveryInfor());
					serviceDiscoveryAdd.put("action", "ADD");
					sendNow(getId(), FogEvents.UPDATE_SERVICE_DISCOVERY, serviceDiscoveryAdd);
					break;

				case ManagementTuple.DEPLOYMENT_REQUEST:
					deployModulesAndExecute(tuple.getDeployementSet(), tuple.getBatchNumber());
					break;

				case ManagementTuple.RESOURCE_UPDATE:
					sendNow(getId(), FogEvents.UPDATE_RESOURCE_INFO, tuple.getResourceData());
					break;

				default:
					throw new IllegalArgumentException("Unknown ManagementTuple type: " + tuple.managementTupleType);
			}
		}
		else if (tuple.getDestinationDeviceId() != -1) {
			int nextDeviceToSend = routingTable.get(tuple.getDestinationDeviceId());
			if (nextDeviceToSend == parentId)
				sendUp(tuple);
			else if (childrenIds.contains(nextDeviceToSend))
				sendDown(tuple, nextDeviceToSend);
			else if (getClusterMembers().contains(nextDeviceToSend))
				sendToCluster(tuple, nextDeviceToSend);
			else
				Logger.error("Routing error", "Routing table of " + getName() + "does not contain next device for destination Id" + tuple.getDestinationDeviceId());
		} else
			Logger.error("Routing error", "Management tuple destination id is -1");
	}

	private void deployModulesAndExecute(Map<Application, List<ModuleLaunchConfig>> deploymentSet, int batchNumber) {
		for (Application app : deploymentSet.keySet()) {
			//ACTIVE_APP_UPDATE
			sendNow(getId(), FogEvents.ACTIVE_APP_UPDATE, app);
			//APP_SUBMIT
			sendNow(getId(), FogEvents.APP_SUBMIT, app);
			for (ModuleLaunchConfig moduleLaunchConfig : deploymentSet.get(app)) {
				String microserviceName = moduleLaunchConfig.getModule().getName();
				//LAUNCH_MODULE
				if (MicroservicePlacementConfig.SIMULATION_MODE == "STATIC") {
//					sendNow(getId(), FogEvents.LAUNCH_MODULE, new AppModule(app.getModuleByName(microserviceName)));
					Logger.error("Simulation static mode error", "Simulation should not be static.");
				} else if (MicroservicePlacementConfig.SIMULATION_MODE == "DYNAMIC") {
					send(getId(), MicroservicePlacementConfig.MODULE_DEPLOYMENT_TIME, FogEvents.LAUNCH_MODULE, new AppModule(app.getModuleByName(microserviceName)));}
				sendNow(getId(), FogEvents.LAUNCH_MODULE_INSTANCE, moduleLaunchConfig);
			}
		}
		// Simon says (140125) FogDevice will send ack to FogBroker a bit AFTER LAUNCH_MODULE is processed
		send(CloudSim.getFogBrokerId(), MicroservicePlacementConfig.MODULE_DEPLOYMENT_TIME + CloudSim.getMinTimeBetweenEvents(), FogEvents.RECEIVE_INSTALL_NOTIF, batchNumber);

		// Simon says (140125) that we are shelving this functionality for a while
		// Instead, fog broker will send the tuples
		// Simon says if self is a target, send EXECUTION_START_REQUEST to self's child (user device)
//		if (pr != null) {
//			int childId = pr.getGatewayDeviceId();
//			send(childId, MicroservicePlacementConfig.MODULE_DEPLOYMENT_TIME, FogEvents.EXECUTION_START_REQUEST);
//		}
	}

	private void startExecution(SimEvent ev) {
		// Simon says (140125) we will be shelving this functionality for now
		Logger.error("Unintended event error", "Mobile users should not be notified to executing!");
		// Simon says this should only be executed by users
//		if (!(deviceType.equals(AMBULANCE_USER) || deviceType.equals(OPERA_USER) || deviceType.equals(GENERIC_USER))) {
//			Logger.error("Device Type Error", "This device should be a user.");
//		}
//		if (ev.getSource() != parentId) {
//			Logger.error("Parent Error", "This request should have been sent from parent.");
//		}
//		if (getSensorID() == -1) {
//			Logger.error("Child Error", "This user should have a sensor.");
//		}
//		int childSensorId = getSensorID();
//		sendNow(childSensorId, FogEvents.EMIT_TUPLE);
	}

	/**
	 * Updating the number of modules of an application module on this device
	 *
	 * @param ev instance of SimEvent containing the module and no of instances
	 */
	protected void updateModuleInstanceCount(SimEvent ev) {
		ModuleLaunchConfig config = (ModuleLaunchConfig) ev.getData();
		String appId = config.getModule().getAppId();
		String moduleName = config.getModule().getName();
		if (!moduleInstanceCount.containsKey(appId)) {
			Map<String, Integer> m = new HashMap<>();
			m.put(moduleName, config.getInstanceCount());
			moduleInstanceCount.put(appId, m);
		} else if (!moduleInstanceCount.get(appId).containsKey(moduleName)) {
			moduleInstanceCount.get(appId).put(moduleName, config.getInstanceCount());
		} else {
			int count = config.getInstanceCount() + moduleInstanceCount.get(appId).get(moduleName);
			moduleInstanceCount.get(appId).put(moduleName, count);
		}

		// in FONs resource availability is updated by placement algorithm
//		if (getDeviceType() != FON) {
//			double mips = getControllerComponent().getAvailableResource(getId(), ControllerComponent.CPU) - (config.getModule().getMips() * config.getInstanceCount());
//			getControllerComponent().updateResources(getId(), ControllerComponent.CPU, mips);
//			double ram = getControllerComponent().getAvailableResource(getId(), ControllerComponent.RAM) - (config.getModule().getRam() * config.getInstanceCount());
//			getControllerComponent().updateResources(getId(), ControllerComponent.RAM, ram);
//			double storage = getControllerComponent().getAvailableResource(getId(), ControllerComponent.STORAGE) - (config.getModule().getSize() * config.getInstanceCount());
//			getControllerComponent().updateResources(getId(), ControllerComponent.STORAGE, storage);
//		}

		// todo Simon says this block should apply to all devices since there are no FON heads and cloud resource availability doesn't exist
		//  However, the cloud's knowledge of FCNs' resource availability is updated by NODE_EXECUTION_FINISHED event
		//  This function is triggered by RELEASE_MODULE, hence updates the FCN's resource availability. But is it necessary??
		double mips = getControllerComponent().getAvailableResource(getId(), ControllerComponent.CPU) - (config.getModule().getMips() * config.getInstanceCount());
		getControllerComponent().updateResources(getId(), ControllerComponent.CPU, mips);
		double ram = getControllerComponent().getAvailableResource(getId(), ControllerComponent.RAM) - (config.getModule().getRam() * config.getInstanceCount());
		getControllerComponent().updateResources(getId(), ControllerComponent.RAM, ram);
		double storage = getControllerComponent().getAvailableResource(getId(), ControllerComponent.STORAGE) - (config.getModule().getSize() * config.getInstanceCount());
		getControllerComponent().updateResources(getId(), ControllerComponent.STORAGE, storage);

		if (isInCluster && MicroservicePlacementConfig.ENABLE_RESOURCE_DATA_SHARING) {
			for (Integer deviceId : getClusterMembers()) {
				ManagementTuple managementTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.RESOURCE_UPDATE);
				Pair<Integer, Map<String, Double>> data = new Pair<>(getId(), getControllerComponent().resourceAvailability.get(getId()));
				managementTuple.setResourceData(data);
				managementTuple.setDestinationDeviceId(deviceId);
				sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, managementTuple);
			}
		}
	}

	protected void sendDownFreeLink(Tuple tuple, int childId) {
		if (tuple instanceof ManagementTuple) {
			double networkDelay = tuple.getCloudletFileSize() / getDownlinkBandwidth();
			setSouthLinkBusy(true);
			double latency = getChildToLatencyMap().get(childId);
			send(getId(), networkDelay, FogEvents.UPDATE_SOUTH_TUPLE_QUEUE);
			send(childId, networkDelay + latency + ((ManagementTuple) tuple).processingDelay, FogEvents.MANAGEMENT_TUPLE_ARRIVAL, tuple);
			//todo
//            if (Config.ENABLE_NETWORK_USAGE_AT_PLACEMENT)
//                NetworkUsageMonitor.sendingManagementTuple(latency, tuple.getCloudletFileSize());
		} else
			super.sendDownFreeLink(tuple, childId);
	}

	protected void sendUpFreeLink(Tuple tuple) {
		if (tuple instanceof ManagementTuple) {
			double networkDelay = tuple.getCloudletFileSize() / getUplinkBandwidth();
			setNorthLinkBusy(true);
			send(getId(), networkDelay, FogEvents.UPDATE_NORTH_TUPLE_QUEUE);
			send(parentId, networkDelay + getUplinkLatency() + ((ManagementTuple) tuple).processingDelay, FogEvents.MANAGEMENT_TUPLE_ARRIVAL, tuple);
			//todo
//            if (Config.ENABLE_NETWORK_USAGE_AT_PLACEMENT)
//                NetworkUsageMonitor.sendingManagementTuple(getUplinkLatency(), tuple.getCloudletFileSize());
		} else {
			super.sendUpFreeLink(tuple);
		}

	}

	public void updateRoutingTable(int destId, int nextId) {
		routingTable.put(destId, nextId);
	}

	private void updateClusterConsInRoutingTable() {
		for(int deviceId:clusterMembers){
			routingTable.put(deviceId,deviceId);
		}
	}

	public void removeMonitoredDevice(FogDevice fogDevice) {
		controllerComponent.removeMonitoredDevice(fogDevice);
	}

	public void addMonitoredDevice(FogDevice fogDevice) {
		controllerComponent.addMonitoredDevice(fogDevice);
	}

	public int getSensorID() {
		return sensorID;
	}

	public void setSensorID(int sensorID) {
		this.sensorID = sensorID;
	}

//	public static final Comparator<MyFogDevice> BY_CPU_THEN_RAM = Comparator
//			.comparingInt(MyFogDevice::getCpu)
//			.thenComparingInt(MyFogDevice::getRam);
}

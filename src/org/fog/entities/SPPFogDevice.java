package org.fog.entities;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.placement.MicroservicePlacementLogic;
import org.fog.placement.SPPHeuristic;
import org.fog.placement.ContextAwarePlacement;
import org.fog.placement.PlacementSimulationController;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.*;
import org.json.simple.JSONObject;

import java.util.*;

/**
 * Created by Joseph Poon.
 */
public class SPPFogDevice extends FogDevice {

	/**
	 * Device type (1.client device 2.FCN 3.FON 4.Cloud)
	 * in this work client device only holds the clientModule of the app and does not participate in processing and placement of microservices ( microservices can be shared among users,
	 * thus for security resons client devices are not used for that)
	 */
	protected String deviceType = null;
	public static final String AMBULANCE_USER = "ambulanceUser";
	public static final String OPERA_USER = "operaUser";
	public static final String GENERIC_USER = "genericUser"; // random movement, no objective
	public static final String IMMOBILE_USER = "immobileUser";
	public static final String FCN = "fcn"; // fog computation node
	public static final String FON = "fon"; // fog orchestration node
	public static final String CLOUD = "cloud"; // cloud datacenter

	public int toClient = 0;

	/**
	 * Interval (in simulation time units) at which periodic placement requests are processed.
	 */
	private double placementProcessInterval = 300.0; // Default value matches MicroservicePlacementConfig

	/**
	 * closest FON id. If this device is a FON its own id is assigned
	 */
	protected int fonID = -1;
	private int microservicesControllerId = -1;

	/**
	 * used to forward tuples towards the destination device
	 * map of <destinationID,nextDeviceID> based on shortest path.
	 */
	protected Map<Integer, Integer> routingTable = new HashMap<>();


	protected ControllerComponent controllerComponent;

	protected List<PlacementRequest> placementRequests = new ArrayList<>();

	public SPPFogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth, double clusterLinkBandwidth, double uplinkLatency, double ratePerMips, String deviceType) throws Exception {
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
				// PlacementSimulationController handles periodic generation now
				//  otherwise this device would autonomously send.
				//  This means rescheduling the TRANSMIT_PR,
				//  according local to poisson distribution.

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
				Logger.debug("Deprecation warning", "finishNodeExecution is deprecated");
				finishNodeExecution(ev);
				break;
			case FogEvents.EXECUTION_START_REQUEST:
				startExecution(ev);
				break;
//			case FogEvents.START_DYNAMIC_CLUSTERING:
//				processClustering(this.getParentId(), this.getId(), ev);
//				updateClusterConsInRoutingTable();
//				break;
			default:
				super.processOtherEvent(ev);
				break;
		}
	}


	/**
	 * Updates the resource information of a device based on an incoming event.
	 * <p>
	 * The event data is expected to contain a {@link Pair}, where:
	 * <ul>
	 *   <li>The first element is the {@code deviceId} of the reporting device.</li>
	 *   <li>The second element is a {@code Map<String, Double>} representing the resource usage.</li>
	 * </ul>
	 * <p>
	 * Note: The resource values in the map MUST BE <b>delta (incremental)</b> values, NOT absolute totals.
	 * These values should be added to (or subtracted from) the current resource usage values rather
	 * than replacing them outright.
	 *
	 * @param ev the simulation event containing the device ID and delta resource values
	 */
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

	protected void setDeviceType(String deviceType) {
		// First temporarily set the deviceType for isUserDevice() to work
		this.deviceType = deviceType;
		
		// Then validate it's a known type
		if (isUserDevice() || deviceType.equals(SPPFogDevice.FCN) ||
				deviceType.equals(SPPFogDevice.FON) || deviceType.equals(SPPFogDevice.CLOUD))
			this.deviceType = deviceType;
		else {
			// Reset to null since it wasn't valid
			this.deviceType = null;
			Logger.error("Incompatible Device Type", "Device type not included in device type enums in MyFogDevice class");
		}
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
				CloudSim.getEntityName(ev.getSource()) + " | Dest : " + CloudSim.getEntityName(ev.getDestination()) +
				" | sensorId : " + tuple.getSensorId() + " | prIndex : " + tuple.getPrIndex());

		if (deviceType.equals(SPPFogDevice.CLOUD)) {
			updateCloudTraffic();
		}

		send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);

		if (FogUtils.appIdToGeoCoverageMap.containsKey(tuple.getAppId())) {
		}

		// Case where tuple is at the end of AppLoop
		//  AND self is the user device of the final destination actuator
		if (tuple.getDirection() == Tuple.ACTUATOR) {
			sendTupleToActuator(tuple);
			return;
		}


		if (deviceType.equals(SPPFogDevice.CLOUD) && tuple.getDestModuleName() == null) {
			sendNow(getControllerId(), FogEvents.TUPLE_FINISHED, null);
		}

		// Case where sender device (self) processes resultant tuple/ periodic tuple
		//  Previously produced by self
		if (tuple.getDestinationDeviceId() == -1) {
			// ACTUATOR tuples already handled above. Only UP and DOWN left
			if (tuple.getDirection() == Tuple.UP) {
				int destination = -1;
				if (tuple.getSensorId() != null && tuple.getPrIndex() != null) {
					destination = controllerComponent.getDestinationDeviceIdWithContext(
							tuple.getDestModuleName(), tuple.getSensorId(), tuple.getPrIndex());
				} else {
					throw new NullPointerException("CRITICAL ERROR: Tuple has no prIndex or sensorId.");
				}
//				int destination = controllerComponent.getDestinationDeviceId(tuple.getDestModuleName());
				if (destination == -1) {
					System.out.println("Service DiscoveryInfo missing, failed. Tuple routing stopped for : " + tuple.getDestModuleName());
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

		// Case where target device (self) receives tuple
		if ((!getHost().getVmList().isEmpty()) && (tuple.getDestinationDeviceId() == getId())) {
			AppModule operator = null;
			if (tuple.getDirection() == Tuple.UP) {
				// Find first free VM (with same module name)
				for (Vm vm : getHost().getVmList()) {
					AppModule a = (AppModule) vm;
					if (Objects.equals(a.getName(), tuple.getDestModuleName())) {
						TupleScheduler ts = (TupleScheduler) a.getCloudletScheduler();
						int numberOfCloudletsExecuting = ts.runningCloudlets();
						if (numberOfCloudletsExecuting == 0) {
							operator = a;
							break;
						} else if (numberOfCloudletsExecuting == 1) {
							System.out.println("Encountered full VM.");
						} else {
							Logger.debug("Control Flow Error", "This vm has more than 1 Cloudlet!");
						}
					}
				}
			}
			else if (tuple.getDirection() == Tuple.DOWN) {
				// Find target VM.
				// Target VM's unique (per simulation) id will be in moduleCopyMap state.
				for (Vm vm : getHost().getVmList()) {
					AppModule a = (AppModule) vm;
					if (tuple.getModuleCopyMap().get(tuple.getDestModuleName()) == a.getId()) {
						// Still must check that VM is free
						int numberOfCloudletsExecuting = ((TupleScheduler) a.getCloudletScheduler()).runningCloudlets();
						if ( numberOfCloudletsExecuting == 0) {
							operator = a;
							break;
						}
						else {
							throw new NullPointerException("Target vm is already full!");
						}
					}
				}
			}
            assert operator != null;
            int vmId = operator.getId();

			/*
			 We now use VmSchedulerTimeShared (no overbooking),
			  we must change the way we allocate/deallocate Pes to the VM
			  Previously we visited the VmScheduler TWICE: Once during installation and once during execution (here),
			  because of overbooking. Now we shouldn't,
			  because allocatePesForVm mutates the `availableMips` state of VmScheduler (for a wrongful second time here).
			*/

			// VmId will always have a value under OnlinePOC
			// because operator cannot be null
			if(tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) &&
					tuple.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId) {
				Logger.debug("Control Flow Error", "VmId should match tuple's destination vmId");
			}
			tuple.setVmId(vmId);
			tuple.addToTraversedMicroservices(getId(), tuple.getDestModuleName());

			updateTimingsOnReceipt(tuple);

			executeTuple(ev, tuple.getDestModuleName());
		} else {
			// Case where self is unrelated to the tuple, just forwarding. Also, followup to above case where destination was just determined.
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

	@Override
	protected void executeTuple(SimEvent ev, String moduleName) {
		Logger.debug(getName(), "Executing tuple on module " + moduleName);
		Tuple tuple = (Tuple) ev.getData();
		AppModule module = getModuleByName(moduleName);

		// Maintain instance tracking logic for UP tuples (unchanged from overbooking implementation)
		if (tuple.getDirection() == Tuple.UP) {
			String srcModule = tuple.getSrcModuleName();
			if (!module.getDownInstanceIdsMaps().containsKey(srcModule))
				module.getDownInstanceIdsMaps().put(srcModule, new ArrayList<Integer>());
			if (!module.getDownInstanceIdsMaps().get(srcModule).contains(tuple.getSourceModuleId()))
				module.getDownInstanceIdsMaps().get(srcModule).add(tuple.getSourceModuleId());

			int instances = -1;
			for (String _moduleName : module.getDownInstanceIdsMaps().keySet()) {
				instances = Math.max(module.getDownInstanceIdsMaps().get(_moduleName).size(), instances);
			}
			module.setNumInstances(instances);
		}

		// Check that the module has an allocation in the mipsMap
		Map<String, List<Double>> mipsMap = getHost().getVmScheduler().getMipsMap();
		if (!mipsMap.containsKey(module.getUid()) || mipsMap.get(module.getUid()).isEmpty() ||
				mipsMap.get(module.getUid()).get(0) <= 0) {
			Logger.error("Vm potentially Null Error", "Module " + moduleName + " does not have MIPS allocated for execution in device "
					+ getName() + ". Terminating tuple execution.");
			return; // Cannot execute without allocation
		}

		// Start the tuple execution timer
		TimeKeeper.getInstance().tupleStartedExecution(tuple);

		// Process the cloudlet submission (don't change any allocations)
		processCloudletSubmit(ev, false);

		// Update energy consumption and cost calculations
		updateEnergyConsumption();
	}

	@Override
	protected void updateAllocatedMips(String incomingOperator) {
		// TODO If VMScheduler does overbooking,
		//   deallocation/reallocation logic goes here.
		updateEnergyConsumption();
	}


	/** Uninstallation of modules is called here
	  It checks ALL the VMs on the PowerHost belonging to this FogDevice (datacenter) to see if their Cloudlet's execution is complete
	  Since OnlinePOC services one Cloudlet per VM, we will uninstall after verifying that Cloudlet execution is complete
	 **/
	@Override
	protected void checkCloudletCompletion() {
		boolean cloudletCompleted = false;
		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {

					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					// Every policy (AppModuleAllocationPolicy) should only be supervising ONE PowerHost
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

							// For service discovery identification
							resTuple.setSensorId(tuple.getSensorId());
							resTuple.setPrIndex(tuple.getPrIndex());

							updateTimingsOnSending(resTuple);
							sendToSelf(resTuple);
						}
						sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

						JSONObject obj = new JSONObject();
						obj.put("module", vm);
						obj.put("tuple", tuple);
						sendNow(getId(), FogEvents.MODULE_UNINSTALL, obj);
						
						if (isUserDevice() && microservicesControllerId != -1) {
							JSONObject objj = new JSONObject();
							objj.put("module", vm);
							objj.put("id", getId());
							objj.put("isDecrease", false);
							sendNow(microservicesControllerId, FogEvents.USER_RESOURCE_UPDATE, objj);
						}
					}
				}
			}
		}
		if (cloudletCompleted)
			updateAllocatedMips(null); // Dynamically reallocated mips from finished Modules
	}

	@Deprecated
	public void finishNodeExecution(SimEvent ev) {
		// We send resource updates via ManagementTuple instead
		// The cloud controller will update resources when it receives the ManagementTuple with resource information
		
		JSONObject objj = (JSONObject) ev.getData();
		controllerComponent.finishNodeExecution(objj);
		
		Logger.debug("Resource Update Process", "Received direct NODE_EXECUTION_FINISHED event. This is deprecated - resources should be updated via ManagementTuple.");
	}

	/**
	 * Both cloud and FON participates in placement process.
	 *  Currently I use no FON devices.
	 */
	public void initializeController(LoadBalancer loadBalancer, MicroservicePlacementLogic mPlacement, Map<Integer, Map<String, Double>> resourceAvailability, Map<String, Application> applications, List<FogDevice> fogDevices) {
		if (getDeviceType() == SPPFogDevice.FON || getDeviceType() == SPPFogDevice.CLOUD) {
			controllerComponent = new ControllerComponent(getId(), loadBalancer, mPlacement, resourceAvailability, applications, fogDevices);
		} else
			Logger.error("Controller init failed", "Wrong device type: " + getDeviceType());
	}

	/**
	 * FCN and Client devices
	 */
	public void initializeController(LoadBalancer loadBalancer) {
		if (getDeviceType() != SPPFogDevice.CLOUD) {
			controllerComponent = new ControllerComponent(getId(), loadBalancer);

			// Initialize resource map directly instead of updateResources calls
			Map<String, Double> initialResources = new HashMap<>();
			initialResources.put(ControllerComponent.CPU, (double) getHost().getTotalMips());
			initialResources.put(ControllerComponent.RAM, (double) getHost().getRam());
			initialResources.put(ControllerComponent.STORAGE, (double) getHost().getStorage());

			controllerComponent.initializeResources(getId(), initialResources);
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

		if (!this.deviceType.equals(SPPFogDevice.CLOUD)) {
			Logger.error("FON exists error", "Placement Request NOT being processed by cloud! Check if device type is FON.");
		}

		if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC && placementRequests.size() == 0) {
			// Use the local placementProcessInterval field
			send(getId(), placementProcessInterval, FogEvents.PROCESS_PRS);
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

		ContextAwarePlacement placementLogicOutput = (ContextAwarePlacement) getControllerComponent().executeApplicationPlacementLogic(placementRequests);
		long endTime = System.nanoTime();
		System.out.println("Placement Algorithm Completed. Time : " + (endTime - startTime) / 1e6);

		Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice = placementLogicOutput.getPerDevice();
		Map<Integer, List<SPPHeuristic.PRContextAwareEntry>> serviceDiscovery = placementLogicOutput.getServiceDiscoveryInfoV2();
		Map<PlacementRequest, Integer> placementRequestStatus = placementLogicOutput.getPrStatus();
		Map<PlacementRequest, Integer> targets = placementLogicOutput.getTargets();
		int fogDeviceCount = 0; // todo I still don't know what this variable does. Currently unused (050125).
		StringBuilder placementString = new StringBuilder();

		// Send perDevice and all updated PRs to FogBroker
		// MUST be before sending deployment requests to devices specified in perDevice
		// 	PRs are retrived from targets.keySet()
		JSONObject forFogBroker = new JSONObject();
		forFogBroker.put("targets", targets);
		forFogBroker.put("perDevice", perDevice);
		forFogBroker.put("cycleNumber", FogBroker.getCycleNumber());

		sendNow(CloudSim.getFogBrokerId(), FogEvents.RECEIVE_PLACEMENT_DECISION, forFogBroker);

		// Propagate service discovery entries to relevant FogDevices
		for (int clientDevice : serviceDiscovery.keySet()) {
			if (Objects.equals(MicroservicePlacementConfig.SIMULATION_MODE, "DYNAMIC")) {
				for (SPPHeuristic.PRContextAwareEntry entry : serviceDiscovery.get(clientDevice)) {
					transmitServiceDiscoveryData(clientDevice, entry);
				}
			}
			else if (Objects.equals(MicroservicePlacementConfig.SIMULATION_MODE, "STATIC")) {
				Logger.error("Simulation static mode error", "Simulation should not be static.");
			}
		}

		// Inform edge servers to install modules
		for (int deviceID : perDevice.keySet()) {
			SPPFogDevice f = (SPPFogDevice) CloudSim.getEntity(deviceID);
			if (!f.getDeviceType().equals(SPPFogDevice.CLOUD))
				fogDeviceCount++;
			placementString.append(CloudSim.getEntity(deviceID).getName() + " : ");
			if (Objects.equals(MicroservicePlacementConfig.SIMULATION_MODE, "DYNAMIC")) {
				transmitModulesToDeploy(deviceID, perDevice.get(deviceID), FogBroker.getCycleNumber());
			}
			else {
				Logger.error("Simulation static mode error", "Simulation mode should be dynamic.");
			}
			placementString.append("\n");
		}

		FogBroker.setCycleNumber(FogBroker.getCycleNumber() + 1);
		System.out.println(placementString);

		// TODO Recycle failed placement requests here if applicable.

		if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.PERIODIC) {
			// Use the local placementProcessInterval field
			send(getId(), placementProcessInterval, FogEvents.PROCESS_PRS);
		}
		else if (MicroservicePlacementConfig.PR_PROCESSING_MODE == MicroservicePlacementConfig.SEQUENTIAL && !this.placementRequests.isEmpty())
			sendNow(getId(), FogEvents.PROCESS_PRS);
	}

	/**
	 * Handles the {@code UPDATE_SERVICE_DISCOVERY} event by updating the service discovery
	 * information for a Fog Node.
	 * <p>
	 * This function is strictly called by Fog Nodes in response to the
	 * {@code UPDATE_SERVICE_DISCOVERY} event. It retrieves the associated service data
	 * and action from the event payload and accordingly updates the controller's service
	 * discovery information.
	 * </p>
	 *
	 * @param ev the simulation event containing the update information.
	 *           The event's data must be a {@code JSONObject} with the following structure:
	 *           <ul>
	 *             <li>{@code "service data"}: a {@code MyHeuristic.PRContextAwareEntry} containing
	 *             all necessary service discovery information.</li>
	 *             <li>{@code "action"}: a {@code String}, either {@code "ADD"} or {@code "REMOVE"}.</li>
	 *           </ul>
	 */
	protected void updateServiceDiscovery(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		SPPHeuristic.PRContextAwareEntry entry = (SPPHeuristic.PRContextAwareEntry) object.get("service data");
		String action = (String) object.get("action");

		if (action.equals("ADD")) { // Upon installation
			this.controllerComponent.addServiceDiscoveryInfo(
					entry.getMicroserviceName(), entry.getDeviceId(), entry.getSensorId(), entry.getPrIndex());
		} else if (action.equals("REMOVE")) { // After execution finish
			this.controllerComponent.removeServiceDiscoveryInfo(
					entry.getMicroserviceName(), entry.getDeviceId(), entry.getSensorId(), entry.getPrIndex());
		}
	}

	// NOTE: Triggered by LAUNCH_MODULE event
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
				// TODO uncomment when apps have periodic edges.
				// initializePeriodicTuples(module);
				// getAllocatedMipsforVm checks the mipmap of the VMScheduler of the Host
				module.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(module).getVmScheduler()
						.getAllocatedMipsForVm(module));

				System.out.println("Module " + module.getName() + " created on " + getName() + " under processModuleArrival()");
				System.out.println("Current ENTITY_ID after module creation: " + org.fog.utils.FogUtils.getCurrentEntityId());
				Logger.debug("Module deploy success", "Module " + module.getName() + " placement on " + getName() + " successful. vm id : " + module.getId());
			} else {
				Logger.error("Module deploy error", "Module " + module.getName() + " placement on " + getName() + " failed");
				System.out.println("Module " + module.getName() + " placement on " + getName() + " failed");
			}
		} else {
			// todo possibly implement vertical scaling.
			//  Currently we allow the installation of multiple modules
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
				System.out.println("Current ENTITY_ID after module creation: " + org.fog.utils.FogUtils.getCurrentEntityId());
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

		NetworkUsageMonitor.sendingModule((double) object.get("delay"), (long) appModule.getSize());
		MigrationDelayMonitor.setMigrationDelay((double) object.get("delay"));
	}


	@Override
	protected void moduleSend(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		AppModule appModule = (AppModule) object.get("module");
		System.out.println(getName() + " is sending " + appModule.getName());
		NetworkUsageMonitor.sendingModule((double) object.get("delay"), (long) appModule.getSize());
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
		Tuple tuple = (Tuple) object.get("tuple");
		int prIndex = tuple.getPrIndex();
		int sensorId = tuple.getSensorId();
		System.out.printf("%s is uninstalling %s. Tuple sensorId %d and prIndex %d%n",
				getName(),
				appModule.getName(),
				sensorId,
				prIndex
				);
//		NetworkUsageMonitor.sendingModule((double) object.get("delay"), appModule.getSize());
//		MigrationDelayMonitor.setMigrationDelay((double) object.get("delay"));

		if (moduleInstanceCount.containsKey(appModule.getAppId()) && moduleInstanceCount.get(appModule.getAppId()).containsKey(appModule.getName())) {
			int moduleCount = moduleInstanceCount.get(appModule.getAppId()).get(appModule.getName());
			if (moduleCount > 1)
				moduleInstanceCount.get(appModule.getAppId()).put(appModule.getName(), moduleCount - 1);
			else if (moduleCount == 1){
				moduleInstanceCount.get(appModule.getAppId()).remove(appModule.getName());
				appToModulesMap.get(appModule.getAppId()).remove(appModule.getName());
			}
			else {
				Logger.error("Value error", String.format("Module instance count invalid: %d", moduleCount));
			}
			sendNow(getId(), FogEvents.RELEASE_MODULE, appModule);

			// Create incremental resource deltas (positive values to indicate resources being freed)
			Map<String, Double> resourceDeltas = new HashMap<>();
			resourceDeltas.put(ControllerComponent.CPU, (double) appModule.getMips());
			resourceDeltas.put(ControllerComponent.RAM, (double) appModule.getRam());
			resourceDeltas.put(ControllerComponent.STORAGE, (double) appModule.getSize());
			
			Pair<Integer, Map<String, Double>> localResourceInfo = new Pair<>(getId(), resourceDeltas);
			sendNow(getId(), FogEvents.UPDATE_RESOURCE_INFO, localResourceInfo);
			
			if (!deviceType.equals(SPPFogDevice.CLOUD)) {
				ManagementTuple resourceUpdateTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.RESOURCE_UPDATE);
				resourceUpdateTuple.setSourceDeviceId(getId());
				resourceUpdateTuple.setResourceData(localResourceInfo);
				resourceUpdateTuple.setDestinationDeviceId(getFonId());
				sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, resourceUpdateTuple);
				
				System.out.printf("Sending resource update ManagementTuple from %s to cloud for module %s with CPU=%f, RAM=%f, Storage=%f%n", 
						getName(), 
						appModule.getName(), 
						(double) appModule.getMips(),
						(double) appModule.getRam(),
						(double) appModule.getSize());
			}
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
	 */
	private void installStartingModule(PlacementRequest pr, Application application) {
		// Find the first module placed on the given device 
		// (There should only be one because this is an unfulfilled Placement Request)
		String placedModule = pr.getPlacedServices()
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

			if (isUserDevice() && microservicesControllerId != -1) {
				JSONObject resourceUpdate = new JSONObject();
				resourceUpdate.put("module", am);
				resourceUpdate.put("id", getId());
				resourceUpdate.put("isDecrease", true);
				sendNow(microservicesControllerId, FogEvents.USER_RESOURCE_UPDATE, resourceUpdate);
			}
		} else {
			Logger.error("Module Placement", "Placement Request with target " + getId() +
					" for PlacementRequest " + pr.getSensorId() + " sent to this device instead.");
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
		// NOTE delay between self (user) and the cloud is accounted for using the ManagementTuple.
		ManagementTuple prTuple = new ManagementTuple(placementRequest.getApplicationId(), FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.PLACEMENT_REQUEST);
		prTuple.setPlacementRequest(placementRequest);
		prTuple.setDestinationDeviceId(fonID);
		prTuple.setSourceDeviceId(getId());
		// Send to self, but the processManagementTuple will see destination device id and forward accordingly.
		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, prTuple);
	}

	private void transmitServiceDiscoveryData(int clientDevice, SPPHeuristic.PRContextAwareEntry entry) {
		System.out.printf("Sending service discovery entry to %d (%s), microservice %s, sensorId %d, prIndex %d%n",
				clientDevice,
				CloudSim.getEntityName(clientDevice),
				entry.getMicroserviceName(),
				entry.getSensorId(),
				entry.getPrIndex());

		ManagementTuple sdTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.SERVICE_DISCOVERY_INFO);
		sdTuple.setSourceDeviceId(getId());
		sdTuple.setServiceDiscoveryInfo(entry);
		sdTuple.setDestinationDeviceId(clientDevice);
		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, sdTuple);
	}

	private void transmitModulesToDeploy(int deviceID, Map<Application, List<ModuleLaunchConfig>> applicationListMap, int cycleNumber) {
		ManagementTuple moduleTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.DEPLOYMENT_REQUEST);
		moduleTuple.setSourceDeviceId(getId());
		moduleTuple.setDeployementSet(applicationListMap);
		moduleTuple.setDestinationDeviceId(deviceID);
		moduleTuple.setCycleNumber(cycleNumber);
		sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, moduleTuple);
	}

	private void processManagementTuple(SimEvent ev) {
		ManagementTuple tuple = (ManagementTuple) ev.getData();
		if (tuple.getDestinationDeviceId() == getId()) {
			switch (tuple.managementTupleType) {
				case ManagementTuple.PLACEMENT_REQUEST:
					// This tuple travelled through network from USER (requester) to CLOUD.
					sendNow(getId(), FogEvents.RECEIVE_PR, tuple.getPlacementRequest());
					break;

				case ManagementTuple.SERVICE_DISCOVERY_INFO:
					JSONObject serviceDiscoveryAdd = new JSONObject();
					serviceDiscoveryAdd.put("service data", tuple.getServiceDiscoveryInfo());
					serviceDiscoveryAdd.put("action", "ADD");
					sendNow(getId(), FogEvents.UPDATE_SERVICE_DISCOVERY, serviceDiscoveryAdd);
					break;

				case ManagementTuple.DEPLOYMENT_REQUEST:
					deployModulesAndExecute(tuple.getDeployementSet(), tuple.getCycleNumber());
					break;

				case ManagementTuple.RESOURCE_UPDATE:
					sendNow(getId(), FogEvents.UPDATE_RESOURCE_INFO, tuple.getResourceData());
					break;
					
				case ManagementTuple.INSTALL_NOTIFICATION:
					// Cloud forwards installation notification to FogBroker without network cost,
					// because FogBroker is attached to cloud.
					if (deviceType.equals(SPPFogDevice.CLOUD)) {
						JSONObject js = new JSONObject();
						js.put("deviceId", tuple.getSourceDeviceId());
						js.put("cycleNumber", tuple.getCycleNumber());
						sendNow(CloudSim.getFogBrokerId(), FogEvents.RECEIVE_INSTALL_NOTIF, js);
					}
					else throw new NullPointerException("Only cloud should receive this");
					break;
					
				case ManagementTuple.TUPLE_FORWARDING:
					Tuple startingTuple = tuple.getStartingTuple();
					
					if (deviceType.equals(SPPFogDevice.CLOUD)) {
//						startingTuple.setSourceDeviceId(getId());
//						sendNow(getId(), FogEvents.TUPLE_ARRIVAL, startingTuple);
						throw new NullPointerException("Control flow error.");
					} else {
						sendNow(getId(), FogEvents.TUPLE_ARRIVAL, startingTuple);
					}
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

	private void deployModulesAndExecute(Map<Application, List<ModuleLaunchConfig>> deploymentSet, int cycleNumber) {
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
					for (int i = 0 ; i < moduleLaunchConfig.getInstanceCount(); i++) {
						send(getId(), MicroservicePlacementConfig.MODULE_DEPLOYMENT_TIME, FogEvents.LAUNCH_MODULE, new AppModule(app.getModuleByName(microserviceName)));
					}
				}
				sendNow(getId(), FogEvents.LAUNCH_MODULE_INSTANCE, moduleLaunchConfig); // Updates local resource availability information
			}
		}
		
		// Send installation notification with appropriate network delay
		if (!deviceType.equals(SPPFogDevice.CLOUD)) {
			// Create installation notification tuple for non-cloud devices
			ManagementTuple installNotifTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.INSTALL_NOTIFICATION);
			installNotifTuple.setSourceDeviceId(getId());
			installNotifTuple.setCycleNumber(cycleNumber);
			installNotifTuple.setDestinationDeviceId(getFonId()); // Send to cloud via FON ID
			sendNow(getId(), FogEvents.MANAGEMENT_TUPLE_ARRIVAL, installNotifTuple);
		} else {
			// Cloud can directly notify FogBroker without network cost
			send(CloudSim.getFogBrokerId(), MicroservicePlacementConfig.MODULE_DEPLOYMENT_TIME + CloudSim.getMinTimeBetweenEvents(), FogEvents.RECEIVE_INSTALL_NOTIF, cycleNumber);
		}

		// FogBroker triggers execution, creating data tuples directly.
		//  Otherwise, user devices needs to determine if self is a target (first tuple executor)
		//  and tell its sensor to send the data tuple.
	}

	@Deprecated
	private void startExecution(SimEvent ev) {
		Logger.error("Unintended event error", "Mobile users should not be notified to executing!");
	}

	/**
	 * Updating the number of modules of an application module on this device
	 *
	 * @param ev instance of SimEvent containing the module and no of instances
	 */
	protected void updateModuleInstanceCount(SimEvent ev) {
		ModuleLaunchConfig config = (ModuleLaunchConfig) ev.getData();
		AppModule appModule = config.getModule();
		int instanceCount = config.getInstanceCount();
		String appId = appModule.getAppId();
		String moduleName = appModule.getName();
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

		//  This function is triggered by RELEASE_MODULE, hence updates the FCN's LOCAL resource availability.
		//  The cloud's knowledge of FCNs' resource availability is updated by NODE_EXECUTION_FINISHED event
		if (getDeviceType() != FON) {
			Map<String, Double> resourceDeltas = new HashMap<>();
			resourceDeltas.put(ControllerComponent.CPU, (double) -(appModule.getMips() * instanceCount));
			resourceDeltas.put(ControllerComponent.RAM, (double) -(appModule.getRam() * instanceCount));
			resourceDeltas.put(ControllerComponent.STORAGE, (double) -(appModule.getSize() * instanceCount));

			Pair<Integer, Map<String, Double>> resourceInfo = new Pair<>(getId(), resourceDeltas);
			sendNow(getId(), FogEvents.UPDATE_RESOURCE_INFO, resourceInfo);
		}

		if (isInCluster && MicroservicePlacementConfig.ENABLE_RESOURCE_DATA_SHARING) {
			for (Integer deviceId : getClusterMembers()) {
				ManagementTuple managementTuple = new ManagementTuple(FogUtils.generateTupleId(), ManagementTuple.NONE, ManagementTuple.RESOURCE_UPDATE);
				Pair<Integer, Map<String, Double>> data = new Pair<>(getId(), getControllerComponent().resourceAvailability.get(getId()));
				managementTuple.setSourceDeviceId(getId());
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

	public int getMicroservicesControllerId() {
		return microservicesControllerId;
	}

	public void setMicroservicesControllerId(int microservicesControllerId) {
		this.microservicesControllerId = microservicesControllerId;
	}

	/**
	 * Checks if this device is a user device (any type of user device).
	 * @return true if the device is a generic user, ambulance user, or opera user; false otherwise
	 */
	public boolean isUserDevice() {
		return deviceType.equals(GENERIC_USER) || 
		       deviceType.equals(AMBULANCE_USER) || 
		       deviceType.equals(OPERA_USER) ||
			   deviceType.equals(IMMOBILE_USER);
	}

	public double getPlacementProcessInterval() {
		return placementProcessInterval;
	}

	public void setPlacementProcessInterval(double placementProcessInterval) {
		this.placementProcessInterval = placementProcessInterval;
	}
}

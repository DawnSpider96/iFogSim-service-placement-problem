package org.fog.entities;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerDatacenterBroker;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.*;
import org.json.simple.JSONObject;

import java.util.*;

public class FogBroker extends PowerDatacenterBroker{

	// Simon says we'll just keep a record of every single PD for now, might be useful for metrics
	// todo Simon (130325) says we will scrap the idea, because metrics are taken straight from Cloud and stored in MyMonitor
	//  Instead we will clear FogBroker's state every time simulation start
	// batch number -> perDevice, which is Device -> (Application -> List <ModuleLaunchConfig which contains unique module object and instance count>)
	private static final Map<Integer, Map<Integer, Boolean>> checklist = new HashMap<>();
	private static final Map<Integer, Map<PlacementRequest, Integer>> toSend = new HashMap<>();
	// DeviceId -> List(VmId)
	private static final Map<Integer, Set<Integer>> activatedVMs = new HashMap<>();


	private static int cycleNumber = 1;

	private static final Map<Application, String> applicationToFirstMicroserviceMap = new HashMap<>();
	private static final Map<Application, List<String>> applicationToSecondMicroservicesMap = new HashMap<>();

	public FogBroker(String name) throws Exception {
		super(name);
	}

	// Simon (130325) says clear FogBroker for Experiment purposes
	public static void clear(){
		applicationToFirstMicroserviceMap.clear();
		applicationToSecondMicroservicesMap.clear();
		setCycleNumber(1);
		checklist.clear();
		toSend.clear();
		activatedVMs.clear();
	}

	// Simon (170125) says DatacenterBroker class has
	// some weird CloudSim functionality that we don't want
	@Override
	public void processEvent(SimEvent ev) {processOtherEvent(ev);};

	protected void processOtherEvent(SimEvent ev) {
		switch (ev.getTag()) {
			case FogEvents.RECEIVE_PLACEMENT_DECISION:
				processPlacementDecision(ev);
				break;
			case FogEvents.RECEIVE_INSTALL_NOTIF:
				handleInstallationNotification(ev.getSource(), (int) ev.getData());
				break;
			case FogEvents.EXECUTION_TIMEOUT:
				handleExecutionTimeout((int) ev.getData());
				break;
			case FogEvents.TUPLE_ACK:
				System.out.println("Tuple acknowledged by device " + ev.getSource());
				break;
			case CloudSimTags.CLOUDLET_RETURN:
				Tuple cl = (Tuple) ev.getData();
				System.out.println("Cloudlet " + cl.getCloudletId() + " came from device " + ev.getSource() + " and is received by broker.");
				break;
			default:
				super.processOtherEvent(ev);
				break;
		}
	}

	private void processPlacementDecision(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		Integer cycleNumber = (Integer) object.get("cycleNumber");
//		if (cycleNumber != this.cycleNumber) {
//			Logger.error("Batch Number Error", "Batch numbers don't match.");
//		}

		Map<PlacementRequest, Integer> targets = (Map<PlacementRequest, Integer>) object.get("targets");
		Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice =
				(Map<Integer, Map<Application, List<ModuleLaunchConfig>>>) object.get("perDevice");
		createChecklist(perDevice, cycleNumber);
		setToSend(targets, cycleNumber);
		send(getId(), MicroservicePlacementConfig.EXECUTION_TIMEOUT_TIME, FogEvents.EXECUTION_TIMEOUT, cycleNumber);
		Logger.debug("Notification", "FogBroker sent out execution Timeout");
//		setBatchNumber(getBatchNumber() + 1);
	}

	// perDevice: deviceId -> (Application -> List (Module, instanceCount))
	public void createChecklist(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice, int cycleNumber) {
        checklist.computeIfAbsent(cycleNumber, k -> new HashMap<>());
		for (Integer deviceId : perDevice.keySet()) {
			checklist.get(cycleNumber).put(deviceId, false);
		}
	}

	private void setToSend(Map<PlacementRequest, Integer> targets, int cycleNumber) {
		toSend.computeIfAbsent(cycleNumber, k -> new HashMap<>());
		toSend.put(cycleNumber, targets);
	}

	public void handleInstallationNotification(int deviceId, int cycleNumber) {
		if (checklist.get(cycleNumber).containsKey(deviceId)) {
			checklist.get(cycleNumber).put(deviceId, true); // Mark as acknowledged
			if (allAcknowledged(cycleNumber)) {
//				cancelTimeout(); // Cancel the execution timeout
				triggerExecution(cycleNumber); // Start tuple execution
			}
		}
	}

	private boolean allAcknowledged(int cycleNumber) {
		return checklist.get(cycleNumber).values().stream().allMatch(Boolean::booleanValue);
	}

	public void handleExecutionTimeout(int cycleNumber) {
		if (!allAcknowledged(cycleNumber)) {
			Logger.error("Execution timeout Error", "Not all devices acknowledged installation on batch " + cycleNumber);
		}
	}

	public void triggerExecution(int cycleNumber) {
		Map<PlacementRequest, Integer> ts = toSend.get(cycleNumber);
		for (Map.Entry<PlacementRequest, Integer> entry : ts.entrySet()) {
			MyPlacementRequest pr = (MyPlacementRequest) entry.getKey();
			Integer deviceId = entry.getValue();
			if (deviceId == null) {
				Logger.error("Missing Key Error", "toSend state was not updated properly.");
			}
			boolean transmitted = false;
			for (Application a : applicationToFirstMicroserviceMap.keySet()) {
				if (a.getAppId() == pr.getApplicationId()) {
					// Simon (020425) says pr conveniently contains (sensorId, prIndex) to populate Tuple with
					transmit(deviceId, a, pr);
					transmitted = true;
					break;
				}
			}
			if (!transmitted) {
				Logger.error("Missing Key Error", "Placement Request app id not found in known Applications!");
			}
		}
	}

	public void transmit(int targetId, Application app, MyPlacementRequest pr){
		String firstMicroservice = applicationToFirstMicroserviceMap.get(app);
		AppEdge _edge = null;
		for(AppEdge edge : app.getEdges()){
			if(edge.getSource().equals(firstMicroservice)) {
				_edge = edge;
				break;
			}
		}
		long cpuLength = (long) _edge.getTupleCpuLength();
		long nwLength = (long) _edge.getTupleNwLength();
		String tupleType = _edge.getTupleType();

		Tuple tuple = new Tuple(app.getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1, nwLength, Config.SENSOR_OUTPUT_SIZE,
				new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
		tuple.setUserId(getId());
		tuple.setTupleType(tupleType);

		// Simon (020245) says this necessary for Service Discovery identification
		tuple.setSensorId(pr.getSensorId());
		tuple.setPrIndex(pr.getPrIndex());

		tuple.setDestModuleName(_edge.getDestination());
		tuple.setSrcModuleName(firstMicroservice);

		// Retrieve the exact instance of the first microservice from the gateway device itself
		// Queries and mutates activatedVMs
		AppModule firstMicroserviceModule = getAppModule(pr, firstMicroservice);

		// Simon (180125) says this is a bit hacky
		// We are pretending the Tuple passed through clientModule though it didn't
		// Edit 4 fields of the Tuple. Imitate the state of tuple in OnlinePOC at the point it is processed in MyFogDevice.processTupleArrival
		Map<String, Integer> moduleCopyMap = new HashMap<>();
		moduleCopyMap.put(firstMicroservice, firstMicroserviceModule.getId());
		tuple.setModuleCopyMap(moduleCopyMap);

		tuple.setSourceModuleId(firstMicroserviceModule.getId());

		tuple.setSourceDeviceId(pr.getRequester());

		tuple.addToTraversedMicroservices(pr.getRequester(), firstMicroservice);
//		updateTimingsOnSending(resTuple);

		Logger.debug(getName(), "Sending tuple with tupleId = " + tuple.getCloudletId());

		tuple.setDestinationDeviceId(targetId);

		int actualTupleId = updateTimings(firstMicroservice, tuple.getDestModuleName(), app);
		tuple.setActualTupleId(actualTupleId);

		sendNow(targetId, FogEvents.TUPLE_ARRIVAL, tuple);
	}

	private static AppModule getAppModule(PlacementRequest pr, String targetService) {
		int deviceId = pr.getRequester();
		MyFogDevice device = (MyFogDevice) CloudSim.getEntity(deviceId);
		if (!activatedVMs.containsKey(deviceId)) activatedVMs.put(deviceId, new HashSet<Integer>());

		AppModule firstMicroserviceModule = null;
		// Simon (130325) says the activatedVms state in FogBroker keeps track of what VMs have already been assigned to
		for (Vm vm : device.getVmList()) {
			AppModule am = (AppModule) vm;
			// Find the first VM in the edge device's VMList that has not been already transmitted to,
			// ie the first VM with name equal to the client microservice (firstMicroservice)
			// and with ID not in activatedVMs state.
			if (Objects.equals(am.getName(), targetService) && !activatedVMs.get(deviceId).contains(am.getId())) {
				// Error catching: Cloudlet Scheduler of the VM must be idle.
				TupleScheduler ts = (TupleScheduler) am.getCloudletScheduler();
				if (ts.runningCloudlets() == 0) {
					firstMicroserviceModule = am;
					break;
				} else {
					Logger.debug("FogBroker Control Flow Error", "This VM should not have Cloudlets!");
				}
			}
		}
		if (firstMicroserviceModule==null) {
			MyFogDevice d = (MyFogDevice) CloudSim.getEntity(deviceId);
			Logger.error("FogBroker Control Flow Error", String.format(
					"Could not find a VM in device %s to transmit tuple to.\n%s",
					d.getName(),
					d.getVmList()
			));
		}
        activatedVMs.get(deviceId).add(firstMicroserviceModule.getId());
		return firstMicroserviceModule;
	}

	protected int updateTimings(String src, String dest, Application app){
		for(AppLoop loop : app.getLoops()){
			if(loop.hasEdge(src, dest)){

				int tupleId = TimeKeeper.getInstance().getUniqueId();
				if(!TimeKeeper.getInstance().getLoopIdToTupleIds().containsKey(loop.getLoopId()))
					TimeKeeper.getInstance().getLoopIdToTupleIds().put(loop.getLoopId(), new ArrayList<Integer>());
				TimeKeeper.getInstance().getLoopIdToTupleIds().get(loop.getLoopId()).add(tupleId);
				TimeKeeper.getInstance().getEmitTimes().put(tupleId, CloudSim.clock());
				return tupleId;
			}
		}
		return -1;
	}



	@Override
	public void startEntity() {

	}

	@Override
	public void shutdownEntity() {

	}

	public static int getCycleNumber(){
		return cycleNumber;
	}

	public static void setCycleNumber(int cycleNumber) {
		FogBroker.cycleNumber = cycleNumber;
	}

	public static Map<Application, String> getApplicationToFirstMicroserviceMap() {
		return applicationToFirstMicroserviceMap;
	}

	public static Map<Application, List<String>> getApplicationToSecondMicroservicesMap() {
		return applicationToSecondMicroservicesMap;
	}

	public static Map<Integer, Map<PlacementRequest, Integer>> getToSend() {
		return toSend;
	}

	public static Map<Integer, Map<Integer, Boolean>> getChecklist() {
		return checklist;
	}
}

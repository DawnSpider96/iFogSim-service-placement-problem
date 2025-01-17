package org.fog.entities;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerDatacenterBroker;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.utils.*;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class FogBroker extends PowerDatacenterBroker{

	// Simon says we'll just keep a record of every single PD for now
	// Might be useful for metrics
	// batch number -> perDevice, which is Device -> (Application -> List <ModuleLaunchConfig which contains unique module object and instance count>)
	private Map<Integer, Map<Integer, Boolean>> checklist = new HashMap<>();
	private Map<Integer, Map<PlacementRequest, Integer>> toSend = new HashMap<>();

	private static int batchNumber = 1;

	private static Map<Application, String> applicationToFirstMicroserviceMap = new HashMap<>();
	private static Map<Application, List<String>> applicationToSecondMicroservicesMap = new HashMap<>();

	public FogBroker(String name) throws Exception {
		super(name);
	}

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
			default:
				super.processOtherEvent(ev);
				break;
		}
	}

	private void processPlacementDecision(SimEvent ev) {
		JSONObject object = (JSONObject) ev.getData();
		Integer batchNumber = (Integer) object.get("batchNumber");
//		if (batchNumber != this.batchNumber) {
//			Logger.error("Batch Number Error", "Batch numbers don't match.");
//		}

		Map<PlacementRequest, Integer> targets = (Map<PlacementRequest, Integer>) object.get("targets");
		Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice =
				(Map<Integer, Map<Application, List<ModuleLaunchConfig>>>) object.get("perDevice");
		createChecklist(perDevice, batchNumber);
		setToSend(targets, batchNumber);
		send(getId(), MicroservicePlacementConfig.EXECUTION_TIMEOUT_TIME, FogEvents.EXECUTION_TIMEOUT, batchNumber);
		Logger.debug("Notification", "FogBroker sent out execution Timeout");
//		setBatchNumber(getBatchNumber() + 1);
	}

	// perDevice: deviceId -> (Application -> List (Module, instanceCount))
	public void createChecklist(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice, int batchNumber) {
        checklist.computeIfAbsent(batchNumber, k -> new HashMap<>());
		for (Integer deviceId : perDevice.keySet()) {
			checklist.get(batchNumber).put(deviceId, false);
		}
	}

	private void setToSend(Map<PlacementRequest, Integer> targets, int batchNumber) {
		toSend.computeIfAbsent(batchNumber, k -> new HashMap<>());
		toSend.put(batchNumber, targets);
	}

	public void handleInstallationNotification(int deviceId, int batchNumber) {
		if (checklist.get(batchNumber).containsKey(deviceId)) {
			checklist.get(batchNumber).put(deviceId, true); // Mark as acknowledged
			if (allAcknowledged(batchNumber)) {
//				cancelTimeout(); // Cancel the execution timeout
				triggerExecution(batchNumber); // Start tuple execution
			}
		}
	}

	private boolean allAcknowledged(int batchNumber) {
		return checklist.get(batchNumber).values().stream().allMatch(Boolean::booleanValue);
	}

	public void handleExecutionTimeout(int batchNumber) {
		if (!allAcknowledged(batchNumber)) {
			Logger.error("Execution timeout Error", "Not all devices acknowledged installation on batch " + batchNumber);
		}
	}

	public void triggerExecution(int batchNumber) {
		Map<PlacementRequest, Integer> ts = toSend.get(batchNumber);
		for (Map.Entry<PlacementRequest, Integer> entry : ts.entrySet()) {
			PlacementRequest pr = entry.getKey();
			Integer deviceId = entry.getValue();
			if (deviceId == null) {
				Logger.error("Missing Key Error", "toSend state was not updated properly.");
			}
			boolean transmitted = false;
			for (Application a : applicationToFirstMicroserviceMap.keySet()) {
				if (a.getAppId() == pr.getApplicationId()) {
					transmit(deviceId, a);
					transmitted = true;
					break;
				}
			}
			if (!transmitted) {
				Logger.error("Missing Key Error", "Placement Request app id not found in known Applications!");
			}
		}
	}

	public void transmit(int targetId, Application app){
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

		tuple.setDestModuleName(_edge.getDestination());
		tuple.setSrcModuleName(firstMicroservice);
		Logger.debug(getName(), "Sending tuple with tupleId = " + tuple.getCloudletId());

		tuple.setDestinationDeviceId(targetId);

		int actualTupleId = updateTimings(tupleType, tuple.getDestModuleName(), app);
		tuple.setActualTupleId(actualTupleId);

		sendNow(targetId, FogEvents.TUPLE_ARRIVAL, tuple);
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

	public static int getBatchNumber(){
		return batchNumber;
	}

	public static void setBatchNumber(int batchNumber) {
		FogBroker.batchNumber = batchNumber;
	}

	public static Map<Application, String> getApplicationToFirstMicroserviceMap() {
		return applicationToFirstMicroserviceMap;
	}

	public static Map<Application, List<String>> getApplicationToSecondMicroservicesMap() {
		return applicationToSecondMicroservicesMap;
	}

}

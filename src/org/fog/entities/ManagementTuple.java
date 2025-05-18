package org.fog.entities;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.application.Application;
import org.fog.placement.SPPHeuristic;
import org.fog.utils.ModuleLaunchConfig;

import java.util.List;
import java.util.Map;

/**
 * Created by Samodha Pallewatta on 9/2/2019.
 */
public class ManagementTuple extends Tuple {

    // management tuples are routed by device id, so direction doesn't matter/
    public static final int NONE = -1;

    public static final int PLACEMENT_REQUEST = 1;
    public static final int SERVICE_DISCOVERY_INFO = 2;
    public static final int RESOURCE_UPDATE = 3;
    public static final int DEPLOYMENT_REQUEST = 4;
    public static final int INSTALL_NOTIFICATION = 5;
    public static final int TUPLE_FORWARDING = 6;

    public int managementTupleType;
    protected PlacementRequest placementRequest;
    protected SPPHeuristic.PRContextAwareEntry serviceDiscoveryInfo;
    protected Map<Application, List<ModuleLaunchConfig>> deployementSet;
    protected Pair<Integer, Map<String, Double>> resourceData;
    protected Tuple startingTuple;

    // Note (sensorID, prIndex) fields serve different purposes in managementTuples and Tuples.
    //  Tuples use them to PASS information to other FogDevices for service discovery.
    //  In ManagementTuples they are the PAYLOADS to update service discovery entries with.

    public Integer getCycleNumber() {
        return cycleNumber;
    }

    public void setCycleNumber(Integer cycleNumber) {
        this.cycleNumber = cycleNumber;
    }

    protected Integer cycleNumber;

    //todo check use of this
    public Double processingDelay = 0.0;

    //todo cloudlet data hard coded
    public ManagementTuple(String appId, int cloudletId, int direction, int tupleType) {
        super(appId, cloudletId, direction, 5, 1, 50, 50, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        managementTupleType = tupleType;
    }

    public ManagementTuple(int cloudletId, int direction, int tupleType) {
        super("Management Tuple", cloudletId, direction, 5, 1, 50, 50, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        managementTupleType = tupleType;
    }

    public void setPlacementRequest(PlacementRequest placementRequest) {
        this.placementRequest = placementRequest;
    }

    public PlacementRequest getPlacementRequest() {
        return placementRequest;
    }

    public void setServiceDiscoveryInfo(SPPHeuristic.PRContextAwareEntry serviceDiscoveryInfo) {
        this.serviceDiscoveryInfo = serviceDiscoveryInfo;
    }

    public SPPHeuristic.PRContextAwareEntry getServiceDiscoveryInfo() {
        return serviceDiscoveryInfo;
    }

    public void setDeployementSet(Map<Application, List<ModuleLaunchConfig>> deployementSet) {
        this.deployementSet = deployementSet;
    }

    public Map<Application, List<ModuleLaunchConfig>> getDeployementSet() {
        return deployementSet;
    }

    public Pair<Integer, Map<String, Double>> getResourceData() {
        return resourceData;
    }

    public void setResourceData(Pair<Integer, Map<String, Double>> resourceData) {
        this.resourceData = resourceData;
    }

    public Tuple getStartingTuple() {
        return startingTuple;
    }

    public void setStartingTuple(Tuple startingTuple) {
        this.startingTuple = startingTuple;
    }
}

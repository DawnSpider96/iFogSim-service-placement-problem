package org.fog.placement;

import org.apache.commons.math3.util.Pair;
import org.fog.application.Application;
import org.fog.entities.PlacementRequest;
import org.fog.utils.ModuleLaunchConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyPlacementLogicOutput extends PlacementLogicOutput{

    // PlacementRequest contains state indicating gateway device ID (that made the PR)
    // Integer indicates which device will send the EXECUTION_START_REQUEST to gateway device
    Map<PlacementRequest, Integer> targets = new HashMap<>();

    public MyPlacementLogicOutput(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice, Map<Integer, List<Pair<String, Integer>>> serviceDiscoveryInfo, Map<PlacementRequest,Integer> prStatus, Map<PlacementRequest, Integer> targets) {
        super(perDevice, serviceDiscoveryInfo, prStatus);
        this.targets = targets;
    }

    public Map<PlacementRequest, Integer> getTargets() {
        return targets;
    }
}

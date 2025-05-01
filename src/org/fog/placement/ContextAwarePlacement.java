package org.fog.placement;

import org.fog.application.Application;
import org.fog.entities.PlacementRequest;
import org.fog.utils.ModuleLaunchConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContextAwarePlacement extends PlacementLogicOutput{

    // PlacementRequest contains state indicating gateway device ID (that made the PR)
    // Integer indicates which device will send the EXECUTION_START_REQUEST to gateway device
    Map<PlacementRequest, Integer> targets = new HashMap<>();

    public ContextAwarePlacement(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice,
                                 Map<Integer, List<SPPHeuristic.PRContextAwareEntry>> serviceDiscoveryInfo,
                                 Map<PlacementRequest, Integer> prStatus,
                                 Map<PlacementRequest, Integer> targets) {
        super(perDevice, prStatus);
        this.targets = targets;
        this.serviceDiscoveryInfoV2 = serviceDiscoveryInfo;
    }

    public Map<PlacementRequest, Integer> getTargets() {
        return targets;
    }
}

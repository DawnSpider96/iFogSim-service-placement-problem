package org.fog.test.perfeval;

import org.fog.utils.Logger;

import java.util.HashMap;
import java.util.Map;

public class SimulationConfig {
    final int numberOfEdge;
    final int numberOfUser;
    final Map<String, Integer> appLoopLengthPerType;
    final int placementLogic;
    final Map<String, Integer> usersPerType;

    public SimulationConfig(int numberOfEdge, int placementLogic,
                            Map<String, Integer> usersPerType,
                            Map<String, Integer> appLoopLengthPerType) {
        this.usersPerType = usersPerType;
        this.appLoopLengthPerType = appLoopLengthPerType;
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
        this.numberOfEdge = numberOfEdge;
        this.placementLogic = placementLogic;
    }

    @Override
    public String toString() {
        return String.format("numberOfEdge: %d, numberOfUser: %d, appLoopLengthPerType: %s, placementLogic: %d",
                numberOfEdge, numberOfUser, appLoopLengthPerType, placementLogic);
    }


    public Map<String, Integer> getAppLoopLengthPerType() {
        return appLoopLengthPerType;
    }

    public int getPlacementLogic() {
        return placementLogic;
    }

    public int getNumberOfEdge() {
        return numberOfEdge;
    }

    public int getNumberOfUser() {return numberOfUser;}
}

package org.fog.test.perfeval;

import org.fog.utils.Logger;

import java.util.HashMap;
import java.util.Map;

public class SimulationConfig {
    final int numberOfEdge;
    final int numberOfUser;
    final int appLoopLength;
    final int placementLogic;
    final Map<String, Integer> usersPerType;

    public SimulationConfig(int numberOfEdge, int appLoopLength, int placementLogic, Map<String, Integer> usersPerType) {
        this.usersPerType = usersPerType;
        this.numberOfUser = usersPerType.values().stream().mapToInt(Integer::intValue).sum();
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
        this.numberOfEdge = numberOfEdge;
        this.appLoopLength = appLoopLength;
        this.placementLogic = placementLogic;
    }

    @Override
    public String toString() {
        return String.format("numberOfEdge: %d, numberOfUser: %d, appLoopLength: %d, placementLogic, %d",
                numberOfEdge, numberOfUser, appLoopLength, placementLogic);
    }


    public int getAppLoopLength() {return appLoopLength;}

    public void setAppLoopLength(int appLoopLength) {
        this.appLoopLength = appLoopLength;
    }

    public int getPlacementLogic() {
        return placementLogic;
    }

    public void setPlacementLogic(int placementLogic) {
        this.placementLogic = placementLogic;
    }

    public int getNumberOfEdge() {
        return numberOfEdge;
    }

    public void setNumberOfEdge(int numberOfEdge) {
        this.numberOfEdge = numberOfEdge;
    }

    public int getNumberOfUser() {return numberOfUser;}

    public void setNumberOfUser(int numberOfUser) {this.numberOfUser = numberOfUser;}
}

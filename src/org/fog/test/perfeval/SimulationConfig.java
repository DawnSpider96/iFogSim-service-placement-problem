package org.fog.test.perfeval;

import org.fog.utils.Logger;

public class SimulationConfig {
    int numberOfEdge;
    int numberOfUser;
    int appLoopLength;
    int placementLogic;

    public SimulationConfig(int numberOfEdge, int numberOfUser, int appLoopLength, int placementLogic) {
        if (numberOfUser >= 196 || numberOfEdge > 300){
            Logger.error("Simulation Parameter error", "Not enough user/edge device location information!");
        }
        this.numberOfEdge = numberOfEdge;
        this.numberOfUser = numberOfUser;
        this.appLoopLength = appLoopLength;
        this.placementLogic = placementLogic;
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

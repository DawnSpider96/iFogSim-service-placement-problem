package org.fog.entities;

import java.util.Map;

public class ContextPlacementRequest extends PlacementRequest{

    // Simon (010425) says handling for generation of new PR with unique ID
    //  is handled in MyMicroservicesController

    private int prIndex; //
    private String userType; // Added userType field to classify placement requests

    public ContextPlacementRequest(String applicationId, int sensorId, int prIndex, int requester, String userType, Map<String,Integer> placedMicroservicesMap){
        super(applicationId, sensorId, requester, placedMicroservicesMap);
        this.prIndex = prIndex;
        this.userType = userType;
    }

    public ContextPlacementRequest(String applicationId, int sensorId, int prIndex, int requester, String userType){
        super(applicationId, sensorId, requester);
        this.prIndex = prIndex;
        this.userType = userType;
    }

    public int getPrIndex() {
        return prIndex;
    }
    
    public String getUserType() {
        return userType;
    }
}

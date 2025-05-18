package org.fog.entities;

import java.util.Map;

public class ContextPlacementRequest extends PlacementRequest{

    // Handling for generation of new PR with unique ID
    //  is handled in PlacementSimulationController

    private int prIndex; //
    private String userType; // Added userType field to classify placement requests
    private double requestLatency;

    public ContextPlacementRequest(String applicationId, int sensorId, int prIndex, int requester, String userType, Map<String,Integer> placedMicroservicesMap, double requestLatency){
        super(applicationId, sensorId, requester, placedMicroservicesMap);
        this.prIndex = prIndex;
        this.userType = userType;
        this.requestLatency = requestLatency;
    }

    public ContextPlacementRequest(String applicationId, int sensorId, int prIndex, int requester, String userType, double requestLatency){
        super(applicationId, sensorId, requester);
        this.prIndex = prIndex;
        this.userType = userType;
        this.requestLatency = requestLatency;
    }

    public int getPrIndex() {
        return prIndex;
    }
    
    public String getUserType() {
        return userType;
    }

    public double getRequestLatency() { return requestLatency; }
}

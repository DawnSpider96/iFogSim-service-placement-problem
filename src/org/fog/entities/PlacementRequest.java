package org.fog.entities;

import java.util.HashMap;
import java.util.Map;

public class PlacementRequest {
    private String applicationId;
    private Map<String,Integer> placedMicroservices; // microservice name to placed device id
    private int placementRequestId; //sensor Id
    private int requester; //device generating the request

    public PlacementRequest(String applicationId, int placementRequestId, int requester, Map<String,Integer> placedMicroservicesMap){
        this.applicationId = applicationId;
        this.placementRequestId = placementRequestId;
        this.requester = requester;
        this.placedMicroservices = placedMicroservicesMap;
    }

    public PlacementRequest(String applicationId,int placementRequestId,int requester){
        this.applicationId = applicationId;
        this.placementRequestId = placementRequestId;
        this.requester = requester;
        this.placedMicroservices = new HashMap<>();
    }

    public String getApplicationId(){
        return applicationId;
    }

    public int getPlacementRequestId() {
        return placementRequestId;
    }

    public int getRequester() {
        return requester;
    }

    public Map<String, Integer> getPlacedMicroservices() {
        return placedMicroservices;
    }
}

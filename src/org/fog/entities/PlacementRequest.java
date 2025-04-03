package org.fog.entities;

import java.util.HashMap;
import java.util.Map;

public class PlacementRequest {
    private String applicationId;
    private Map<String,Integer> placedMicroservices; // microservice name to placed device id
    private int sensorId; //sensor Id
    private int requester; //device generating the request

    public PlacementRequest(String applicationId, int sensorId, int requester, Map<String,Integer> placedMicroservicesMap){
        this.applicationId = applicationId;
        this.sensorId = sensorId;
        this.requester = requester;
        this.placedMicroservices = placedMicroservicesMap;
    }

    public PlacementRequest(String applicationId, int sensorId, int requester){
        this.applicationId = applicationId;
        this.sensorId = sensorId;
        this.requester = requester;
        this.placedMicroservices = new HashMap<>();
    }

    public String getApplicationId(){
        return applicationId;
    }

    public int getSensorId() {
        return sensorId;
    }

    public int getRequester() {
        return requester;
    }

    public Map<String, Integer> getPlacedMicroservices() {
        return placedMicroservices;
    }
}

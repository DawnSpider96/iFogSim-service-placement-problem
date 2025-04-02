package org.fog.entities;

import java.util.HashMap;
import java.util.Map;

public class MyPlacementRequest {
    private String applicationId;
    private Map<String,Integer> placedMicroservices; // microservice name to placed device id
    private int sensorId; // sensor Id
    private int prId; //
    private int requester; //device generating the request

    public MyPlacementRequest(String applicationId, int sensorId, int prId, int requester, Map<String,Integer> placedMicroservicesMap){
        this.applicationId = applicationId;
        this.sensorId = sensorId;
        this.prId = prId;
        this.requester = requester;
        this.placedMicroservices = placedMicroservicesMap;
    }

    public MyPlacementRequest(String applicationId, int sensorId, int prId, int requester){
        this.applicationId = applicationId;
        this.sensorId = sensorId;
        this.prId = prId;
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

    public int getPrId() {
        return prId;
    }
}

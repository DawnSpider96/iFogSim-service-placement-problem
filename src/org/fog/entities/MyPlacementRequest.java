package org.fog.entities;

import java.util.Map;

public class MyPlacementRequest extends PlacementRequest{

    // Simon (010425) says handling for generation of new PR with unique ID
    //  is handled in MyMicroservicesController

    private int prIndex; //

    public MyPlacementRequest(String applicationId, int sensorId, int prIndex, int requester, Map<String,Integer> placedMicroservicesMap){
        super(applicationId, sensorId, requester, placedMicroservicesMap);
        this.prIndex = prIndex;
    }

    public MyPlacementRequest(String applicationId, int sensorId, int prIndex, int requester){
        super(applicationId, sensorId, requester);
        this.prIndex = prIndex;
    }

    public int getPrIndex() {
        return prIndex;
    }
}

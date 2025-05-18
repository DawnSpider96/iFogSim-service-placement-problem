package org.fog.entities;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.utils.FogEvents;
import org.fog.utils.GeoLocation;
import org.fog.utils.distribution.Distribution;

import java.util.Objects;

public class PassiveSensor extends Sensor {

    // This child of Sensor does NOT perform Placement Request generation or Tuple generation.

    String userType;

    public PassiveSensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, Distribution transmitDistribution, int cpuLength, int nwLength, String tupleType, String destModuleName) {
        super(name, userId, appId, gatewayDeviceId, latency, geoLocation, transmitDistribution, cpuLength, nwLength, tupleType, destModuleName);
    }

    public PassiveSensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, Distribution transmitDistribution, String tupleType) {
        super(name, userId, appId, gatewayDeviceId, latency, geoLocation, transmitDistribution, tupleType);
    }

    public PassiveSensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution) {
        super(name, tupleType, userId, appId, transmitDistribution);

        // NOTE: THIS ASSUMES that the sensor's name ALWAYS starts with "s-"
        if (!name.startsWith("s-")) throw new NullPointerException("Check name!");
        String userType = name.substring(2).split("_")[0];

        // todo This is a hardcoded check. Edit based on your user types.
        if (!(Objects.equals(userType, SPPFogDevice.GENERIC_USER) ||
                Objects.equals(userType, SPPFogDevice.AMBULANCE_USER) ||
                Objects.equals(userType, SPPFogDevice.OPERA_USER) ||
                Objects.equals(userType, SPPFogDevice.IMMOBILE_USER))) {
            throw new NullPointerException("Invalid Type");
        }

        this.userType = userType;
    }

    // NOTE: Overridden to prevent EMIT_TUPLE event from being sent to self.
    // MyMicroservicesController
    @Override
    public void startEntity() {
        send(gatewayDeviceId, CloudSim.getMinTimeBetweenEvents(), FogEvents.SENSOR_JOINED, geoLocation);
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch(ev.getTag()){
            case FogEvents.TUPLE_ACK:
                //transmit(transmitDistribution.getNextValue());
                break;
            case FogEvents.EMIT_TUPLE:
                transmit();
                break;
            default:
                super.processEvent(ev);
                break;
        }
    }

    public String getUserType() {
        return userType;
    }

    // We use FogBroker as a central control entity that creates tuples.
    // Hence, sensors will be useless for now (140125)
    @Override
    public void transmit(){
        throw new NullPointerException("Sensor should not be emitting tuples");
    }


}

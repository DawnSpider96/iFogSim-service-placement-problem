package org.fog.entities;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.utils.FogEvents;
import org.fog.utils.GeoLocation;
import org.fog.utils.Logger;
import org.fog.utils.distribution.Distribution;

public class MySensor extends Sensor {

    public MySensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, Distribution transmitDistribution, int cpuLength, int nwLength, String tupleType, String destModuleName) {
        super(name, userId, appId, gatewayDeviceId, latency, geoLocation, transmitDistribution, cpuLength, nwLength, tupleType, destModuleName);
    }

    public MySensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, Distribution transmitDistribution, String tupleType) {
        super(name, userId, appId, gatewayDeviceId, latency, geoLocation, transmitDistribution, tupleType);
    }

    public MySensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution) {
        super(name, tupleType, userId, appId, transmitDistribution);
    }

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

    // Simon says we will use FogBroker as a central control entity that creates tuples.
    // Hence, sensors will be useless for now (140125)
    @Override
    public void transmit(){
        Logger.error("Unintended event error", "Sensors should not be emitting tuples.");
    }


}

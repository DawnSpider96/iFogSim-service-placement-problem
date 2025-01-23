package org.fog.placement;

import org.fog.mobilitydata.Location;

/**
 *
 * @author Christian Cabrera <cabrerac@scss.tcd.ie>
 * This class represents the memory of an ANT, used to deposite the pheromones in the backward propagation process.
 * Location field is null for ACO, !null for ACOMobility
 */

public class ANTMemory {
    private int nodeIndex;
    //    private OverlayContact contact;
    private double latency;
    private double cpuUtil;
    private double ramUtil;
    private Location location;
    private int targetFogDeviceId;

    public ANTMemory(int nodeIndex, double latency, double cpuUtil, double ramUtil, int targetFogDeviceId) {
        this(nodeIndex, latency, cpuUtil, ramUtil, null, targetFogDeviceId);
    }

    public int getTargetFogDeviceId() {
        return targetFogDeviceId;
    }

    public void setTargetFogDeviceId(int targetFogDeviceId) {
        this.targetFogDeviceId = targetFogDeviceId;
    }

    public ANTMemory(int nodeIndex, double latency, double cpuUtil, double ramUtil, Location location,
                     int targetFogDeviceId) {
        this.nodeIndex = nodeIndex;
        this.latency = latency;
        this.cpuUtil = cpuUtil;
        this.ramUtil = ramUtil;
        this.location = location;
        this.targetFogDeviceId = targetFogDeviceId;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

    public void setNodeIndex(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    public double getLatency() {
        return latency;
    }

    public void setLatency(double latency) {
        this.latency = latency;
    }

    public double getCpuUtil() {
        return cpuUtil;
    }

    public void setCpuUtil(double cpuUtil) {
        this.cpuUtil = cpuUtil;
    }

    public double getRamUtil() {
        return ramUtil;
    }

    public void setRamUtil(double ramUtil) {
        this.ramUtil = ramUtil;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}

package org.fog.entities;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.placement.MicroservicePlacementLogic;
import org.fog.placement.PlacementLogicOutput;
import org.json.simple.JSONObject;

import javax.naming.ldap.Control;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Samodha Pallewatta on 8/29/2019.
 */
public class ControllerComponent {

    protected LoadBalancer loadBalancer;
    protected MicroservicePlacementLogic microservicePlacementLogic = null;
    protected ServiceDiscovery serviceDiscoveryInfo;

    protected int deviceId;

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }


    // Resource Availability Info
    /**
     * Resource Identifiers
     */
    public static final String RAM = "ram";
    public static final String CPU = "cpu";
    public static final String STORAGE = "storage";

    /**
     * DeviceID,<ResourceIdentifier,AvailableResourceAmount>
     */
    protected Map<Integer, Map<String, Double>> resourceAvailability = new HashMap<>();


    //Application Info
    private Map<String, Application> applicationInfo = new HashMap<>();

    //FOg Architecture Info
    private List<FogDevice> fogDeviceList;


    /**
     * For FON
     *
     * @param loadBalancer
     * @param mPlacement
     */
    public ControllerComponent(Integer deviceId, LoadBalancer loadBalancer, MicroservicePlacementLogic mPlacement,
                               Map<Integer, Map<String, Double>> resourceAvailability, Map<String, Application> applicationInfo, List<FogDevice> fogDevices) {
        this.fogDeviceList = fogDevices;
        this.loadBalancer = loadBalancer;
        this.applicationInfo = applicationInfo;
        this.microservicePlacementLogic = mPlacement;
        this.resourceAvailability = resourceAvailability;
        setDeviceId(deviceId);
        serviceDiscoveryInfo = new ServiceDiscovery(deviceId);
    }

    /**
     * For FCN
     *
     * @param loadBalancer
     */
    public ControllerComponent(Integer deviceId, LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        setDeviceId(deviceId);
        serviceDiscoveryInfo = new ServiceDiscovery(deviceId);
    }

    /**
     * 1. execute placement logic -> returns the placement mapping.
     * 2. deploy on devices.
     * 3. update service discovery.
     */
    public PlacementLogicOutput executeApplicationPlacementLogic(List<PlacementRequest> placementRequests) {
        if (microservicePlacementLogic != null) {
            PlacementLogicOutput placement = microservicePlacementLogic.run(fogDeviceList, applicationInfo, resourceAvailability, placementRequests);
            return placement;
        }

        return null;
    }

    public void finishNodeExecution(JSONObject objj) {
        // Instead of absolute resource calculations:
        // resourceAvailability.get(deviceId).put(resourceType, newAbsoluteValue);

        // Use incremental updates:
        AppModule module = (AppModule) objj.get("module");
        int deviceId = (int) objj.get("id");

        // Calculate deltas
        Map<String, Double> resourceDeltas = new HashMap<>();
        resourceDeltas.put(ControllerComponent.CPU, (double) module.getMips());
        resourceDeltas.put(ControllerComponent.RAM, (double) module.getRam());
        resourceDeltas.put(ControllerComponent.STORAGE, (double) module.getSize());

        updateResourceInfo(deviceId, resourceDeltas);
    }

    public void addServiceDiscoveryInfo(String microserviceName, Integer deviceID) {
        this.serviceDiscoveryInfo.addServiceDIscoveryInfo(microserviceName, deviceID);
        System.out.println("Service Discovery Info ADDED (device:" +
                CloudSim.getEntityName(this.deviceId) +
                ") for microservice :" + microserviceName + " , destDevice : " +
                CloudSim.getEntityName(deviceID));
    }

    public int getDestinationDeviceId(String destModuleName) {
        return loadBalancer.getDeviceId(destModuleName, serviceDiscoveryInfo);
    }

    public Application getApplicationPerId(String appID) {
        return applicationInfo.get(appID);
    }

    public Double getAvailableResource(int deviceID, String resourceIdentifier) {
        if (resourceAvailability.containsKey(deviceID))
            return resourceAvailability.get(deviceID).get(resourceIdentifier);
        else
            return null;
    }

//    public void updateResources(int device, String resourceIdentifier, double remainingResourceAmount) {
//        if (resourceAvailability.containsKey(device))
//            resourceAvailability.get(device).put(resourceIdentifier, remainingResourceAmount);
//        else {
//            Map<String, Double> resources = new HashMap<>();
//            resources.put(resourceIdentifier, remainingResourceAmount);
//            resourceAvailability.put(device, resources);
//        }
//    }

//    public void updateResourceInfo(int deviceId, Map<String, Double> resources) {
//        resourceAvailability.put(deviceId, resources);
//    }

    public void initializeResources(int deviceId, Map<String, Double> initialResources) {
        resourceAvailability.put(deviceId, new HashMap<>(initialResources));
    }

    // Simon (010425) says we will use incremental amounts and do the arithmetic here at ControllerComponent
    // to prevent race conditions
    public void updateResourceInfo(int deviceId, Map<String, Double> resourceDeltas) {
        if (!resourceAvailability.containsKey(deviceId)) {
            resourceAvailability.put(deviceId, new HashMap<>(resourceDeltas));
        } else {
            Map<String, Double> currentResources = resourceAvailability.get(deviceId);
            for (String resourceType : resourceDeltas.keySet()) {
                if (currentResources.containsKey(resourceType)) {
                    currentResources.put(resourceType,
                            currentResources.get(resourceType) + resourceDeltas.get(resourceType));
                } else {
                    currentResources.put(resourceType, resourceDeltas.get(resourceType));
                }
            }
        }
    }

    public void updateResources(int deviceId, String resourceType, double delta) {
        if (!resourceAvailability.containsKey(deviceId)) {
            Map<String, Double> initialMap = new HashMap<>();
            initialMap.put(resourceType, delta);
            resourceAvailability.put(deviceId, initialMap);
        } else {
            Map<String, Double> resources = resourceAvailability.get(deviceId);
            if (resources.containsKey(resourceType)) {
                resources.put(resourceType, resources.get(resourceType) + delta);
            } else {
                resources.put(resourceType, delta);
            }
        }
    }

    public void removeServiceDiscoveryInfo(String microserviceName, Integer deviceID) {
        this.serviceDiscoveryInfo.removeServiceDIscoveryInfo(microserviceName, deviceID);
    }

    public void removeMonitoredDevice(FogDevice fogDevice) {
        this.fogDeviceList.remove(fogDevice);
    }

    public void addMonitoredDevice(FogDevice fogDevice) {
        this.fogDeviceList.add(fogDevice);
    }


}

class ServiceDiscovery {
    protected Map<String, List<Integer>> serviceDiscoveryInfo = new HashMap<>();
    int deviceId ;

    public ServiceDiscovery(Integer deviceId) {
        this.deviceId =deviceId;
    }

    public void addServiceDIscoveryInfo(String microservice, Integer device) {
        if (serviceDiscoveryInfo.containsKey(microservice)) {
            List<Integer> deviceList = serviceDiscoveryInfo.get(microservice);
            deviceList.add(device);
            serviceDiscoveryInfo.put(microservice, deviceList);
        } else {
            List<Integer> deviceList = new ArrayList<>();
            deviceList.add(device);
            serviceDiscoveryInfo.put(microservice, deviceList);
        }
    }

    public Map<String, List<Integer>> getServiceDiscoveryInfo() {
        return serviceDiscoveryInfo;
    }

    public void removeServiceDIscoveryInfo(String microserviceName, Integer deviceID) {
        if (serviceDiscoveryInfo.containsKey(microserviceName) && serviceDiscoveryInfo.get(microserviceName).contains(new Integer(deviceID))) {
            System.out.println("Service Discovery Info REMOVED (device:" + this.deviceId + ") for microservice :" + microserviceName + " , destDevice : " + deviceID);
            serviceDiscoveryInfo.get(microserviceName).remove(new Integer(deviceID));
            if (serviceDiscoveryInfo.get(microserviceName).size() == 0)
                serviceDiscoveryInfo.remove(microserviceName);
        }
    }
}










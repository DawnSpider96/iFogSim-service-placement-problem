package org.fog.entities;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.placement.MicroservicePlacementLogic;
import org.fog.placement.SPPHeuristic;
import org.fog.placement.PlacementLogicOutput;
import org.fog.utils.Logger;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Samodha Pallewatta on 8/29/2019.
 */
public class ControllerComponent {

    protected LoadBalancer loadBalancer;
    protected MicroservicePlacementLogic microservicePlacementLogic = null;
    protected PRAwareServiceDiscovery serviceDiscoveryInfo;

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
        serviceDiscoveryInfo = new PRAwareServiceDiscovery(deviceId);
    }

    /**
     * For FCN
     *
     * @param loadBalancer
     */
    public ControllerComponent(Integer deviceId, LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        setDeviceId(deviceId);
        serviceDiscoveryInfo = new PRAwareServiceDiscovery(deviceId);
    }

    public void addServiceDiscoveryInfo(String microserviceName, Integer deviceID, Integer sensorId, Integer prIndex) {
        ((PRAwareServiceDiscovery)this.serviceDiscoveryInfo).addServiceDiscoveryInfo(
                microserviceName, deviceID, sensorId, prIndex);
        System.out.println("Service Discovery Info ADDED (device:" +
                CloudSim.getEntityName(this.deviceId) +
                ") for microservice: " + microserviceName +
                " , destDevice: " + CloudSim.getEntityName(deviceID) +
                " , sensorId: " + sensorId +
                " , prIndex: " + prIndex);
    }

    public int getDestinationDeviceIdWithContext(String destModuleName, Integer sensorId, Integer prIndex) {
        Integer deviceId = ((PRAwareServiceDiscovery)serviceDiscoveryInfo).getDeviceIdByContext(destModuleName, sensorId, prIndex);

        if (deviceId != null) {
            return deviceId;
        }

        // CANNOT fall back to regular load balancing if no match found.
        // Will cause problems downstream 100%
        Logger.error("Control Flow Error", "PR-specific service discovery entry could not be found for module: " + destModuleName +
                " with sensorId " + sensorId + " and PR index: " + prIndex);
        throw new NullPointerException("CRITICAL ERROR: PR-specific service discovery entry not found for module: " + destModuleName +
                " with sensorId " + sensorId + " and PR index: " + prIndex);
    }

    public void removeServiceDiscoveryInfo(String microserviceName, Integer deviceID, Integer sensorId, Integer prIndex) {
        ((PRAwareServiceDiscovery)this.serviceDiscoveryInfo).removeServiceDiscoveryInfo(
                microserviceName, deviceID, sensorId, prIndex);
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

    // Deprecated.
    /**
     * @deprecated This method uses the standard service discovery mechanism without PR awareness.
     * Use addServiceDiscoveryInfo(String microserviceName, Integer deviceID, Integer sensorId, Integer prIndex) instead.
     */
    public void addServiceDiscoveryInfo(String microserviceName, Integer deviceID) {
        this.serviceDiscoveryInfo.addServiceDIscoveryInfo(microserviceName, deviceID);
        System.out.println("Service Discovery Info ADDED (on device:" +
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

    // NOTE: We use incremental amounts and do the arithmetic here at ControllerComponent
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

class PRAwareServiceDiscovery extends ServiceDiscovery {
    // List of all service discovery entries
    protected List<SPPHeuristic.PRContextAwareEntry> entries;
    // Index for faster lookups by microservice name
    protected Map<String, List<SPPHeuristic.PRContextAwareEntry>> microserviceIndex;

    public PRAwareServiceDiscovery(Integer deviceId) {
        super(deviceId);
        this.entries = new ArrayList<>();
        this.microserviceIndex = new HashMap<>();
    }

    // Enhanced method with both sensorId and prIndex
    public void addServiceDiscoveryInfo(String microservice, Integer deviceId, Integer sensorId, Integer prIndex) {
        SPPHeuristic.PRContextAwareEntry entry = new SPPHeuristic.PRContextAwareEntry(
            microservice, deviceId, sensorId, prIndex);
            
        // Add to main list
        entries.add(entry);
        
        // Add to microservice index for faster lookups
        if (!microserviceIndex.containsKey(microservice)) {
            microserviceIndex.put(microservice, new ArrayList<>());
        }
        microserviceIndex.get(microservice).add(entry);
    }

    // Enhanced removal method using the composite key
    public void removeServiceDiscoveryInfo(String microservice, Integer deviceId, Integer sensorId, Integer prIndex) {
        boolean found = false;
        
        // First check if we have entries for this microservice
        if (microserviceIndex.containsKey(microservice)) {
            List<SPPHeuristic.PRContextAwareEntry> microserviceEntries = microserviceIndex.get(microservice);
            Iterator<SPPHeuristic.PRContextAwareEntry> iterator = microserviceEntries.iterator();
            
            // Find and remove the matching entry
            while (iterator.hasNext()) {
                SPPHeuristic.PRContextAwareEntry entry = iterator.next();
                if (entry.getDeviceId().equals(deviceId) && 
                    entry.getSensorId().equals(sensorId) && 
                    entry.getPrIndex().equals(prIndex)) {
                    
                    // Remove from the index
                    iterator.remove();
                    // Remove from the main list
                    entries.remove(entry);
                    
                    found = true;
                    break;
                }
            }
            
            // Clean up empty lists in the index
            if (microserviceEntries.isEmpty()) {
                microserviceIndex.remove(microservice);
            }
        }
        
        // Throw an exception if the entry wasn't found
        if (!found) {
            throw new NullPointerException(String.format("CRITICAL ERROR: PR-specific service discovery entry not found! " +
                    "\nMicroservice name: %s,\nDevice ID: %d,\nSensor ID: %d,\nPR Index: %d",
                microservice,
                deviceId,
                sensorId,
                prIndex
            ));
        }
    }

    public Integer getDeviceIdByContext(String microservice, Integer sensorId, Integer prIndex) {
        if (microserviceIndex.containsKey(microservice)) {
            for (SPPHeuristic.PRContextAwareEntry entry : microserviceIndex.get(microservice)) {
                // Check if the context matches
                if (entry.getSensorId().equals(sensorId) && entry.getPrIndex().equals(prIndex)) {
                    return entry.getDeviceId();
                }
            }
        }
        return null;
    }
    
    public List<SPPHeuristic.PRContextAwareEntry> getAllEntries() {
        return new ArrayList<>(entries);
    }
    
    public List<SPPHeuristic.PRContextAwareEntry> getEntriesByMicroservice(String microservice) {
        if (microserviceIndex.containsKey(microservice)) {
            return new ArrayList<>(microserviceIndex.get(microservice));
        }
        return new ArrayList<>();
    }
}







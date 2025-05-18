package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.entities.ContextPlacementRequest;
import org.fog.utils.Logger;

import org.btrplace.model.DefaultModel;
import org.btrplace.model.Mapping;
import org.btrplace.model.Model;
import org.btrplace.model.Node;
import org.btrplace.model.VM;
import org.btrplace.model.constraint.Running;
import org.btrplace.model.constraint.SatConstraint;
import org.btrplace.model.view.ShareableResource;
import org.btrplace.plan.ReconfigurationPlan;
import org.btrplace.scheduler.choco.ChocoScheduler;
import org.btrplace.scheduler.choco.DefaultChocoScheduler;

import java.util.*;

public class ILPHeuristic extends SPPHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */

    private List<DeviceState> DeviceStates = new ArrayList<>();

    @Override
    public String getName() {
        return "ILP";
    }

    public ILPHeuristic(int fonID) {
        super(fonID);
    }

    @Override
    public void postProcessing() {
    }

    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new LinkedHashMap<>();

        int placementCompleteCount = 0;
        if (toPlace.isEmpty()) {
            // Update toPlace and placementCompleteCount
            placementCompleteCount = fillToPlace(placementCompleteCount, toPlace, placementRequests);
        }

        DeviceStates = new ArrayList<>();
        for (FogDevice fogDevice : edgeFogDevices) {
            DeviceStates.add(new DeviceState(fogDevice.getId(), resourceAvailability.get(fogDevice.getId()),
                    fogDevice.getHost().getTotalMips(), fogDevice.getHost().getRam(), fogDevice.getHost().getStorage()));
        }

        Map<PlacementRequest, Integer> prStatus = new LinkedHashMap<>();
        // Process every PR individually
        for (Map.Entry<PlacementRequest, List<String>> entry : toPlace.entrySet()) {
            PlacementRequest placementRequest = entry.getKey();
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            List<String> microservices = entry.getValue();
            // -1 if success, cloudId if failure
            // Cloud will resend to itself
            // Type int for flexibility: In more complex simulations there may be more FON heads, not just the cloud.
            int status = processOnePr(microservices, app, placementRequest);
            prStatus.put(placementRequest, status);
        }
        return prStatus;
    }

    @Override
    protected List<DeviceState> getCurrentDeviceStates() {
        return DeviceStates; // Already has DeviceStates field
    }

    @Override
    protected int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {

        int containers = microservices.size();

        List<VM> vms = new ArrayList<>();
        Model model = new DefaultModel();
        Mapping map = model.getMapping();

        ShareableResource rcCPU = new ShareableResource("cpu");
        ShareableResource rcMem = new ShareableResource("mem");

        for (int i = 0; i < DeviceStates.size(); i++) {
            Node n = model.newNode();
            int cpu = (int) DeviceStates.get(i).getCPU();
            int ram = (int) DeviceStates.get(i).getRAM();
            rcCPU.setCapacity(n, cpu <= 0 ? 1: cpu);
            rcMem.setCapacity(n, ram <= 0 ? 1: ram);
            map.addOnlineNode(n);
        }

        for (int i = 0; i < containers; i++) {
            VM v = model.newVM();
            
            AppModule service = getModule(microservices.get(i), app);

            rcCPU.setConsumption(v, (int) service.getMips());
            rcMem.setConsumption(v, (int) service.getRam());

            vms.add(v);
            map.addReadyVM(v);
        }

        //Attach the resources
        model.attach(rcCPU);
        model.attach(rcMem);

        List<SatConstraint> constraints = new ArrayList<>();

        for (int i = 0; i < containers; i++) {
            constraints.add(new Running(vms.get(i)));
        }

        ChocoScheduler ra = new DefaultChocoScheduler();
        ReconfigurationPlan plan = ra.solve(model, constraints);

        // Initialize temporary state
        int[] placed = new int[microservices.size()];
        for (int i = 0 ; i < microservices.size() ; i++) {
            placed[i] = -1;
        }

        if (plan != null) {
            for (int i = 0; i < plan.getSize(); i++) {
                int nodeID = plan.getResult().getMapping().getVMLocation(vms.get(i)).id();
                String s = microservices.get(i);
                AppModule service = getModule(s, app);
                DeviceState node = DeviceStates.get(nodeID);
                if(DeviceStates.get(nodeID).canFit(service.getMips(), service.getRam(), service.getSize())) {
                    DeviceStates.get(nodeID).allocate(service.getMips(), service.getRam(), service.getSize());
                    placed[i] = node.getId();
                }
                if (placed[i] < 0) {
                    System.out.println("Failed to place module " + s + " on PR " + placementRequest.getSensorId());
                    System.out.println("Failed placement " + placementRequest.getSensorId());

                    // Undo every "placement" recorded in placed. Only deviceStates was changed, so we change it back
                    // This part should never be reached because btrplace already deals with each PR as a whole
                    // Meaning if there is one module that cannot be placed, the whole PR is not placed.
                    Logger.error("Control Flow Error", "This code should never be reached.");
                    for (int j = 0 ; j < placed.length ; j++) {
                        int placedDeviceId = placed[j];
                        String microservice = microservices.get(j);
                        if (placedDeviceId != -1) {
                            AppModule placedService = getModule(microservice, app);
                            int placedDeviceIndex = -1;
                            for (int k = 0 ; k < DeviceStates.size() ; k++) {
                                if (DeviceStates.get(k).getId() == placedDeviceId) {
                                    placedDeviceIndex = k;
                                }
                            }
                            assert (placedDeviceIndex >= 0);
                            DeviceStates.get(placedDeviceIndex).deallocate(placedService.getMips(), placedService.getRam(), placedService.getSize());
                        }
                    }
                    break;
                }
            }
            // System.out.println("Time-based plan:");
            // System.out.println(new TimeBasedPlanApplier().toString(plan));
            // System.out.println("\nDependency based plan:");
            // System.out.println(new
            // DependencyBasedPlanApplier().toString(plan));
        }

        boolean allPlaced = true;
        for (int p : placed) {
            if (p == -1) allPlaced = false;
        }

        if (allPlaced) {
            // Create a key for this placement request
            PlacementRequestKey prKey = new PlacementRequestKey(
                placementRequest.getSensorId(), 
                ((ContextPlacementRequest)placementRequest).getPrIndex()
            );
            
            // Ensure the key exists in mappedMicroservices
            if (!mappedMicroservices.containsKey(prKey)) {
                mappedMicroservices.put(prKey, new LinkedHashMap<>());
            }
            
            for (int i = 0 ; i < microservices.size(); i++) {
                String s = microservices.get(i);
                AppModule service = getModule(s, app);
                int deviceId = placed[i];

                System.out.printf("Placement of operator %s on device %s successful. Device id: %d, sensorId: %d, prIndex: %d%n",
                        s,
                        CloudSim.getEntityName(deviceId),
                        deviceId,
                        placementRequest.getSensorId(),
                        ((ContextPlacementRequest) placementRequest).getPrIndex());

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(prKey).put(s, deviceId);

                //currentModuleLoad
                if (!currentModuleLoadMap.get(deviceId).containsKey(s))
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips());
                else
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips() + currentModuleLoadMap.get(deviceId).get(s));

                //currentModuleInstance
                if (!currentModuleInstanceNum.get(deviceId).containsKey(s))
                    currentModuleInstanceNum.get(deviceId).put(s, 1);
                else
                    currentModuleInstanceNum.get(deviceId).put(s, currentModuleInstanceNum.get(deviceId).get(s) + 1);
            }
        }
        else {
            System.out.println("Failed placement " + placementRequest.getSensorId());
        }

        if (allPlaced) return -1;
        else return getFonID();
    }
    
}




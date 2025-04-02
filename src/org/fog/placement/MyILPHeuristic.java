package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.PlacementRequest;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;

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

public class MyILPHeuristic extends MyHeuristic implements MicroservicePlacementLogic {
    /**
     * Fog network related details
     */

    private List<DeviceState> DeviceStates = new ArrayList<>();

    @Override
    public String getName() {
        return "ILP";
    }

    public MyILPHeuristic(int fonID) {
        super(fonID);
    }

    @Override
    public void postProcessing() {
    }

    /**
     * Determines all the modules to place in this cycle.
     * This implementation traces the AppLoop and compiles ALL modules from the AppLoop.
     *
     * @param toPlace           An empty/incomplete map of PlacementRequest to the list of Microservices (String) that require placement.
     *                          CPU and RAM requirements of each Microservice can be obtained with getModule() method.
     * @param placementRequests this.placementRequests, ie the list of all PlacementRequest objects
     * @return A map reflecting the updated entries after cleaning.
     * @see #getModule
     */
    @Override
    protected int fillToPlace(int placementCompleteCount, Map<PlacementRequest, List<String>> toPlace, List<PlacementRequest> placementRequests) {
        int f = placementCompleteCount;
        for (PlacementRequest placementRequest : placementRequests) {
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            Set<String> alreadyPlaced = mappedMicroservices.get(placementRequest.getSensorId()).keySet();
            List<String> completeModuleList = getAllModulesToPlace(new HashSet<>(alreadyPlaced), app);

            if (completeModuleList.isEmpty()) {
                Logger.error("Flow Control Error", "fillToPlace is called on a completed PR");
                f++;  // Increment only if no more modules can be placed
            } else {
                toPlace.put(placementRequest, completeModuleList);
            }
        }
        return f;
    }

    @Override
    protected Map<PlacementRequest, Integer> mapModules() {
        Map<PlacementRequest, List<String>> toPlace = new HashMap<>();

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

        Map<PlacementRequest, Integer> prStatus = new HashMap<>();
        // Process every PR individually
        for (Map.Entry<PlacementRequest, List<String>> entry : toPlace.entrySet()) {
            PlacementRequest placementRequest = entry.getKey();
            Application app = applicationInfo.get(placementRequest.getApplicationId());
            List<String> microservices = entry.getValue();
            // -1 if success, cloudId if failure
            // Cloud will resend to itself
            // Type int for flexibility: In more complex simulations there may be more FON heads, not just the cloud.
            int status = tryPlacingOnePr(microservices, app, placementRequest);
            prStatus.put(placementRequest, status);
        }
        return prStatus;
    }


    @Override
    protected int tryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {

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
                    // todo Simon says what do we do when failure?
                    //  (160125) Nothing. Because (aggregated) failure will be determined outside the for loop
                    System.out.println("Failed to place module " + s + " on PR " + placementRequest.getSensorId());
                    System.out.println("Failed placement " + placementRequest.getSensorId());

                    // Undo every "placement" recorded in placed. Only deviceStates was changed, so we change it back
                    // Simon (310125) says this part should never be reached because btrplace already deals with each PR as a whole
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
            for (int i = 0 ; i < microservices.size(); i++) {
                String s = microservices.get(i);
                AppModule service = getModule(s, app);
                int deviceId = placed[i];

                Logger.debug("ModulePlacementEdgeward", "Placement of operator " + s + " on device " + CloudSim.getEntityName(deviceId) + " successful.");
                System.out.println("Placement of operator " + s + " on device " + CloudSim.getEntityName(deviceId) + " successful.");

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(placementRequest.getSensorId()).put(s, deviceId);

                //currentModuleLoad
                if (!currentModuleLoadMap.get(deviceId).containsKey(s))
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips());
                else
                    currentModuleLoadMap.get(deviceId).put(s, service.getMips() + currentModuleLoadMap.get(deviceId).get(s)); // todo Simon says isn't this already vertical scaling? But is on PR side not FogDevice side

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


    /**
     * Queries FogBroker to obtain the name(s) of second Microservice(s) in the AppLoop
     * Iterates through all Placement Requests, using them to extract target for the second Microservice(s)
     * State that can be used:
     *   - List<PlacementRequest> placementRequests:    This has the completed placement target IDs.
     *   - Map<PlacementRequest, Integer> closestNodes
     *  - Map<Integer, Application> applicationInfo
     * @param perDevice     Actually not very needed. Contains details of exactly how many module instance requests
     *                      were sent to each device. Includes the module instances themselves.
     * @return Map of each PR to the deviceId that the FogBroker will inform to begin execution
     * */
    @Override
    protected Map<PlacementRequest, Integer> determineTargets(Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice) {
        Map<PlacementRequest, Integer> targets = new HashMap<>();
        for (PlacementRequest pr : placementRequests) {
            Application app = applicationInfo.get(pr.getApplicationId());
            // Simon says we want one target per second microservice in the PR's application
            // If there are no second microservices, targeted is true
            boolean targeted = true;
            for (String secondMicroservice : FogBroker.getApplicationToSecondMicroservicesMap().get(app)) {
                for (Map.Entry<String, Integer> entry : pr.getPlacedMicroservices().entrySet()) {
                    if (Objects.equals(entry.getKey(), secondMicroservice)) {
                        targets.put(pr, entry.getValue());
                        targeted = true;
                        break;
                    }
                    targeted = false;
                }
            }

            if (!targeted) {
                Logger.error("ILP Deployment Error", "Cannot find target device for " + pr.getSensorId() + ". Check the placement of its first microservice.");
            }
        }
        return targets;
    }
}




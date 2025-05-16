package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.utils.Logger;
import org.fog.utils.MicroservicePlacementConfig;

import java.util.*;

public class SPPACO extends SPPHeuristic implements MicroservicePlacementLogic {
    @Override
    public String getName() {
        return "ACO";
    }

    /**
     * Fog network related details
     */
    // Latency calculation variables
//    private String topology = MicroservicePlacementConfig.NETWORK_TOPOLOGY;
//    private Map<Long, Integer> serversIds;
//    private Map<Integer, MECSystemEntity> mecSystems;
//    private Long requestReceiver;

    public SPPACO(int fonID) {
        super(fonID);
    }

//    private double tau0 = 1.0; TODO change back
    private double tau0 = 4.0;
//    private int antsNumber = 10; TODO 1000 for performance evaluation
    private int antsNumber = 200;
    private List<DeviceState> DeviceStates = new ArrayList<>();

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

        // Simon says in the ACO algorithm itself, copies of these DeviceStates will be made.
        // This initialisation occurs only once,
        // capturing the state of resourceAvailability (and fogDevices) at this point in time
        DeviceStates = new ArrayList<>();
        for (FogDevice fogDevice : edgeFogDevices) {
            Map<String, Double> dev = resourceAvailability.get(fogDevice.getId());
            PowerHost host = fogDevice.getHost();
            DeviceStates.add(
                    new DeviceState(
                        fogDevice.getId(),
                        host.getTotalMips(),
                        host.getRam(),
                        host.getStorage(),
                        dev.get(ControllerComponent.CPU),
                        dev.get(ControllerComponent.RAM),
                        dev.get(ControllerComponent.STORAGE)));
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
        return DeviceStates;
    }

    @Override
    protected int doTryPlacingOnePr(List<String> microservices, Application app, PlacementRequest placementRequest) {
        // Length of microservices should be equal to length of placement
        int requestReceiver = closestNodes.get(placementRequest);
        MyACOHelper acoHelper = new MyACOHelper(microservices, DeviceStates, app, globalLatencies, indices, antsNumber, tau0, requestReceiver);
        int[] placement = acoHelper.acoSchedule();

        // Initialize temporary state
//        Map<String, Integer> placed = new LinkedHashMap<>();
//        for (String microservice : microservices) {
//            placed.put(microservice, -1);
//        }

        boolean allPlaced = true;
        for (int p : placement) {
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
                int deviceId = placement[i];

                Logger.debug("Placement Success", String.format("Operator %s on device %s, Device id: %d, sensorId: %d, prIndex: %d%n",
                        s,
                        CloudSim.getEntityName(deviceId),
                        deviceId,
                        placementRequest.getSensorId(),
                        ((ContextPlacementRequest) placementRequest).getPrIndex()));

                // DeviceStates will go into future ACOHelper objects
                // Then all the "copy" DeviceStates will contain the updated resource information
                for (DeviceState d : DeviceStates) {
                    if (d.getId() == deviceId) {
                        d.allocate(service.getMips(), service.getRam(), service.getSize());
                    }
                }

                moduleToApp.put(s, app.getAppId());

                if (!currentModuleMap.get(deviceId).contains(s))
                    currentModuleMap.get(deviceId).add(s);

                mappedMicroservices.get(prKey).put(s, deviceId);

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
            Logger.debug("Placement Failed", "But temporary state not affected");

        }
//        placements.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>());
//        placements.get(entry.getKey()).put(service, deviceState.deviceId);
//        getCurrentCpuLoad().put(deviceState.deviceId,
//                getCurrentCpuLoad().get(deviceState.deviceId) + service.getMips());
//        getCurrentRamLoad().put(deviceState.deviceId,
//                getCurrentRamLoad().get(deviceState.deviceId) + service.getRam());

        if (allPlaced) return -1;
        else return getFonID();
    }

    class MyACOHelper {

        private List<String> microservices;

        private List<DeviceState> edgeServers;

        private double[][] latencies;

        // Total ants number
        private int antsNumber;

        // Ants that will explore
        private ANT[] ants;

        // Total number of iterations to be performed
        private int iterations = 10;

        // If alfa is 0, then the closest nodes are more likely
        // to be selected this corresponds to a classical
        // stochastic greedy algorithm (with multiple starting
        // points since ants are initially randomly distributed
        // on the cities).
         private double alfa = 0.002;
//        private double alfa;

        // If beta is 0, then only pheromone amplification is at
        // work this method will lead to a rapid stagnation situation
        // with the corresponding generation of tours which, in general,
        // are strongly suboptimal
        private double beta = 0.3;
//         private double beta = 0.0001; // TODO change back
//        private double beta;

        // Global evaporation rate
//         private double ro = 0.005; // TODO change back
         private double ro = 0.05;

        // Initial pheromone level
        // private double tau0 = 1.0;
        private double tau0;

        // This is the amount of pheromone deposit used for each branch
//        private double deltaTau = 0.001; // TODO change back
        private double deltaTau = 0.4;

        // Stores the pheromone levels
        private double[][] pheromone;

        // Stores the heuristic values
        private double[][] heuristic;

        // Latency calculation variables
//        private String topology = MicroservicePlacementConfig.NETWORK_TOPOLOGY;
        private Map<Integer, Integer> serversIds;
        //    private Map<Integer, MECSystemEntity> mecSystems;
        private int requestReceiver;
        private final Application app;
        private final Random random;

        MyACOHelper(List<String> microservices, List<DeviceState> edgeServers, Application app, double[][] latencies, Map<Integer, Integer> serversIds, int antsNumber, double tau0, int requestReceiver) {
            this.microservices = microservices;
            this.edgeServers = edgeServers;

            this.app = app;

            this.latencies = latencies;
            this.serversIds = serversIds;
            this.antsNumber = antsNumber;
            this.ants = new ANT[antsNumber];
            this.pheromone = new double[edgeServers.size()][microservices.size()];
            this.heuristic = new double[edgeServers.size()][microservices.size()];
            this.tau0 = tau0;
            this.requestReceiver = requestReceiver;
            this.random = SPPACO.this.getRandom();
            initAnts();
            initPheromones();
        }

        public int[] acoSchedule() {

            boolean done = true;
            int[] placement = new int[this.getMicroservices().size()];
            for (int i = 0; i < placement.length; i++)
                placement[i] = -1;

            for (int iter = 0; iter < this.iterations; iter++) {
                for (int k = 0; k < this.antsNumber; k++) {
                    // TODO These are "copies" of the FogDevices, only containing state that we need.
                    //  Verify that it is indeed ok to have them disappear after each iteration of this FOR loop.
                    List<DeviceState> serversIteration = new ArrayList<>();
                    for (DeviceState server : edgeServers) {
                        serversIteration.add(new DeviceState(server));
                    }
//                    for (FogDevice fogDevice : edgeServers) {
//                        serversIteration.add(new DeviceState(fogDevice.getId(), resourceAvailability.get(fogDevice.getId()),
//                                fogDevice.getHost().getTotalMips(), fogDevice.getHost().getRam(), fogDevice.getHost().getStorage()));
//                    }
                    for (int j = 0; j < this.getMicroservices().size(); j++) {
                        // computeHeuristic();
                        String microservice = this.getMicroservices().get(j);
                        AppModule service = getModule(microservice, app);
                        done = this.forwardMovement(j, k, service.getMips(), service.getRam(), service.getSize(), serversIteration);
                        if (!done)
                            break;
                    }
                    if (!done)
                        break;
                }

                if (!done)
                    break;
                int optimalSolutionIndex = this.processSolutions();
                ants[optimalSolutionIndex].setSuperior(true);

                for (int m = 0; m < this.ants[optimalSolutionIndex].getMemories().length; m++) {
                    placement[m] = (int) this.ants[optimalSolutionIndex].getMemories()[m].getTargetFogDeviceId();
                }

                this.globalEvaporation();

                // Build the solutions
                for (int j = 0; j < this.getMicroservices().size(); j++) {
                    for (int k = 0; k < this.antsNumber; k++) {
                        this.backwardMovement(j, k);
                    }
                }

                for (int k = 0; k < this.antsNumber; k++) {
                    this.ants[k].reset();
                }
            }
            return placement;
        }

        public void initPheromones() {
            for (int i = 0; i < this.getEdgeServers().size(); i++) {
                for (int j = 0; j < this.getMicroservices().size(); j++) {
                    this.pheromone[i][j] = tau0;
                }
            }
        }

        public void initAnts() {
            for (int i = 0; i < this.antsNumber; i++) {
                this.ants[i] = new ANT(this.getMicroservices().size());
            }
        }

        protected AppModule getModule(String moduleName, Application app) {
            for (AppModule appModule : app.getModules()) {
                if (appModule.getName().equals(moduleName))
                    return appModule;
            }
            return null;
        }

        public boolean forwardMovement(int cIndex, int aIndex, double cpuRequirement, double ramRequirement, double storageRequirement, List<DeviceState> serversIteration) {

            boolean done = false;
            double sumProb = 0.0;
            double[] selectionProb = new double[serversIteration.size()];

            for (int i = 0; i < serversIteration.size(); i++) {
                DeviceState edge = serversIteration.get(i);
                if (edge.canFit(cpuRequirement, ramRequirement, storageRequirement)) {
                    double niu = 0.0;
                    double latency = 0.0;
                    if (cIndex == 0) {
                        latency = getLatency(this.getLatencies(), this.requestReceiver, edge.getId(), this.serversIds);
                        if(latency == 0)
                            niu = 1.0 / 0.01;
                        else
                            niu = 1.0 / latency;
                    }
                    else {
                        int currentEdgeId = ants[aIndex].getFogDevice().getId();
                        latency = getLatency(this.getLatencies(), currentEdgeId, edge.getId(), this.serversIds);
                        if (latency == 0)
                            niu = 1.0 / 0.01;
                        else
                            niu = 1.0 / latency;
                    }
                    selectionProb[i] = Math.pow(this.pheromone[i][cIndex], alfa) * Math.pow(niu, beta);
                    sumProb += selectionProb[i];
                } else {
                    selectionProb[i] = 0.0;
                }
            }

            if (sumProb > 0) {
                done = true;
                double prob = random.nextDouble() * sumProb;
                int j = 0;
                double p = selectionProb[j];
                while (p < prob) {
                    j++;
                    p += selectionProb[j];
                }

                DeviceState selectedEdge = serversIteration.get(j);
                if (cIndex == 0) {
                    serversIteration.get(j).allocate(cpuRequirement, ramRequirement, storageRequirement);
                    double latency = getLatency(this.getLatencies(), this.requestReceiver, selectedEdge.getId(), this.serversIds);
                    this.ants[aIndex].addMemory(cIndex, new ANTMemory(j, latency, selectedEdge.getCPUUtil(), selectedEdge.getRAMUtil(), selectedEdge.getId()));
                } else {
                    serversIteration.get(j).allocate(cpuRequirement, ramRequirement, storageRequirement);
                    int currentEdgeId = this.ants[aIndex].getFogDevice().getId();
                    double latency = getLatency(this.getLatencies(), currentEdgeId, selectedEdge.getId(), this.serversIds);
                    this.ants[aIndex].addMemory(cIndex, new ANTMemory(j, latency, selectedEdge.getCPUUtil(), selectedEdge.getRAMUtil(), selectedEdge.getId()));
                }
                this.ants[aIndex].setCurrentNode(j);
                this.ants[aIndex].setFogDevice(selectedEdge);
            }

            return done;
        }

        public void backwardMovement(int cIndex, int aIndex) {
            depositPheronome(cIndex, aIndex);
        }

        public int processSolutions() {
            double minLatency = -1;
            int index = 0;

            for (int i = 0; i < this.antsNumber; i++) {
                double totalLatency = 0;
                for (int j = 0; j < this.ants[i].getMemories().length; j++) {
                    totalLatency += this.ants[i].getMemories()[j].getLatency();
                }
                if (minLatency == -1) {
                    minLatency = totalLatency;
                    index = i;
                } else {
                    if (minLatency > totalLatency) {
                        minLatency = totalLatency;
                        index = i;
                    }
                }
            }
            return index;
        }

        public void depositPheronome(int cIndex, int aIndex) {
            if (this.ants[aIndex].isSuperior()) {
                int nodeID = this.ants[aIndex].getMemories()[cIndex].getNodeIndex();
                this.pheromone[nodeID][cIndex] = this.pheromone[nodeID][cIndex] + this.deltaTau;
            }
        }

        public void computeHeuristic() {
            double niu;
            for (int i = 0; i < this.getEdgeServers().size(); i++) {
                for (int j = 0; j < this.getMicroservices().size(); j++) {
                    // DOUBLE CHECK THIS
                    if (this.getLatencies()[i][j] > 0) {
                        niu = 1.0 / this.getLatencies()[i][j];
                    } else {
                        niu = 1.0 / 0.0001;
                    }
                    this.heuristic[i][j] = Math.pow(this.pheromone[i][j], this.alfa) * Math.pow(niu, this.beta);
                }
            }
        }

        // Decrease the pheromone level in the pheron
        public void globalEvaporation() {
            for (int i = 0; i < this.getEdgeServers().size(); i++) {
                for (int j = 0; j < this.getMicroservices().size(); j++) {
                    this.pheromone[i][j] = (1 - this.ro) * this.pheromone[i][j];
                }
            }
        }

        public double getLatency(double[][] latencies, Integer edge1, Integer edge2, Map<Integer, Integer> serversIds) {
            double latency = 0.0;
            switch(MicroservicePlacementConfig.NETWORK_TOPOLOGY) {
                case MicroservicePlacementConfig.CENTRALISED:
                    int edge1Index = serversIds.get(edge1);
                    int edge2Index = serversIds.get(edge2);
                    if (edge1Index == edge2Index)
                        latency = 0.0;
                    else
                        latency = latencies[edge1Index][cloudIndex] + latencies[cloudIndex][edge2Index];
                    break;
                case MicroservicePlacementConfig.FEDERATED:
                    Logger.error("Control Flow Error", "Topology cannot possibly be Federated.");
                    return -1.0;
//                cloudIndex = 0;
//                edge1Index = serversIds.get(edge1);
//                Long leader1 = Util.getLeader(edge1, mecSystems);
//                int leader1Index = serversIds.get(leader1);
//                edge2Index = serversIds.get(edge2);
//                Long leader2 = Util.getLeader(edge2, mecSystems);
//                int leader2Index = serversIds.get(leader2);
//                if (edge1Index == edge2Index)
//                    latency = 0.0;
//                else {
//                    if(leader1Index == leader2Index)
//                        latency = latencies[edge1Index][leader1Index] + latencies[leader2Index][edge2Index];
//                    else
//                        latency = latencies[edge1Index][leader1Index] + latencies[leader1Index][cloudIndex]
//                                + latencies[cloudIndex][leader2Index] + latencies[leader2Index][edge2Index];
//                }
//                break;
                case MicroservicePlacementConfig.DECENTRALISED:
                    Logger.error("Control Flow Error", "Topology cannot possibly be Decentralised.");
                    return -1.0;
//                edge1Index = serversIds.get(edge1);
//                edge2Index = serversIds.get(edge2);
//                latency = latencies[edge1Index][edge2Index];
//                break;
            }
            // TODO determine whether the math is necessary. In iFogSim I think all latencies are in ms?
//        latency = (latency / Time.MILLISECOND);
            return latency;
        }

        public List<String> getMicroservices() {
            return microservices;
        }

        public void setMicroservices(List<String> microservices) {
            this.microservices = microservices;
        }

        public List<DeviceState> getEdgeServers() {
            return edgeServers;
        }

        public void setEdgeServers(List<DeviceState> edgeServers) {
            this.edgeServers = edgeServers;
        }

        public int getAntsNumber() {
            return antsNumber;
        }

        public void setAntsNumber(int antsNumber) {
            this.antsNumber = antsNumber;
        }

        public ANT[] getAnts() {
            return ants;
        }

        public void setAnts(ANT[] ants) {
            this.ants = ants;
        }

        public int getIterations() {
            return iterations;
        }

        public void setIterations(int iterations) {
            this.iterations = iterations;
        }

        public double getAlfa() {
            return alfa;
        }

        public void setAlfa(double alfa) {
            this.alfa = alfa;
        }

        public double getBeta() {
            return beta;
        }

        public void setBeta(double beta) {
            this.beta = beta;
        }

        public double getRo() {
            return ro;
        }

        public void setRo(double ro) {
            this.ro = ro;
        }

        public double getTau0() {
            return tau0;
        }

        public void setTau0(double tau0) {
            this.tau0 = tau0;
        }

        public double getDeltaTau() {
            return deltaTau;
        }

        public void setDeltaTau(double deltaTau) {
            this.deltaTau = deltaTau;
        }

        public double[][] getPheromone() {
            return pheromone;
        }

        public void setPheromone(double[][] pheromone) {
            this.pheromone = pheromone;
        }

        public double[][] getHeuristic() {
            return heuristic;
        }

        public void setHeuristic(double[][] heuristic) {
            this.heuristic = heuristic;
        }

//    public TopologyEnum getTopology() {
//        return topology;
//    }
//
//    public void setTopology(TopologyEnum topology) {
//        this.topology = topology;
//    }

        public Map<Integer, Integer> getServersIds() {
            return serversIds;
        }

        public void setServersIds(Map<Integer, Integer> serversIds) {
            this.serversIds = serversIds;
        }

//    public Map<Integer, MECSystemEntity> getMecSystems() {
//        return mecSystems;
//    }
//
//    public void setMecSystems(Map<Integer, MECSystemEntity> mecSystems) {
//        this.mecSystems = mecSystems;
//    }

        public Integer getRequestReceiver() {
            return requestReceiver;
        }

        public void setRequestReceiver(Integer requestReceiver) {
            this.requestReceiver = requestReceiver;
        }

        public double[][] getLatencies() {
            return latencies;
        }

        public void setLatencies(double[][] latencies) {
            this.latencies = latencies;
        }

    }
}




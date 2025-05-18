package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.example.policies.VmSchedulerTimeSharedEnergy;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.MyApplication;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.mobilitydata.Location;
import org.fog.mobilitydata.References;
import org.fog.placement.PlacementSimulationController;
import org.fog.placement.PlacementLogicFactory;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.*;
import org.fog.utils.MetricUtils.PerformanceMetrics;
import org.fog.utils.distribution.DeterministicDistribution;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Platform to run the OnlinePOC simulation under variable parameters:
 *      1. Number of edge servers
 *      2. Length of AppLoop
 *      3. Placement Logic
 *
 * Metrics:
 *      1. Average Latency
 *      2. CPU Utilization
 *
 * @author Joseph Poon
 */

/**
 * Config properties
 * SIMULATION_MODE -> dynamic or static
 * PR_PROCESSING_MODE -> PERIODIC
 * ENABLE_RESOURCE_DATA_SHARING -> false (not needed as FONs placed at the highest level.
 * DYNAMIC_CLUSTERING -> true (for clustered) and false (for not clustered) * (also compatible with static clustering)
 */
public class SPPExperiment {
//    private static final String outputFile = "./output/PerfEval/ACO_100_X_X_FINAL.csv";
//    private static final String CONFIG_FILE = "./dataset/PerformanceEvalConfigsUsers.yaml";
    private static final String outputFile = "./output/MiH_4_0_Melbourne.csv";
    private static final String CONFIG_FILE = "./dataset/SPPExperimentConfigs.yaml";
    // Path to location configuration file
    private static final String LOCATION_CONFIG_FILE = "./dataset/location_config.json";

    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    // Constants for CSV file paths and data configurations
    private static final String USERS_LOCATION_PATH = "./dataset/usersLocation-melbCBD_Experiments.csv";
    private static final String RESOURCES_LOCATION_PATH = "./dataset/edgeResources-melbCBD_Experiments.csv";
    static double SENSOR_TRANSMISSION_TIME = 10;

    // Add these constants after the existing constants
    // Flag to enable dynamic location generation
    private static boolean USE_DYNAMIC_LOCATIONS = false;
    // Paths for dynamically generated files (will be populated at runtime)
    private static String DYNAMIC_RESOURCES_LOCATION_PATH = "";
    private static String DYNAMIC_USERS_LOCATION_PATH = "";
    private static String DYNAMIC_LOCATION_CONFIG_FILE = "";
    // Default output directory for generated files
    private static String OUTPUT_DIRECTORY = "./dataset/simon/";

    /**
     * Enables dynamic location generation from grid and random distributions.
     * Call this method before running the simulation to use dynamically generated coordinates.
     * 
     * @param useAutoGenerate Whether to generate files automatically
     */
    public static void setUseDynamicLocations(boolean useAutoGenerate) {
        USE_DYNAMIC_LOCATIONS = useAutoGenerate;
    }

    /**
     * Sets the output directory for dynamically generated files.
     * Call this method before running the simulation if you want to change the default location.
     * The directory will be created if it doesn't exist.
     * 
     * @param directoryPath Path to the directory where files should be generated
     */
    public static void setOutputDirectory(String directoryPath) {
        if (directoryPath != null && !directoryPath.isEmpty()) {
            // Ensure the path ends with a separator
            if (!directoryPath.endsWith("/") && !directoryPath.endsWith("\\")) {
                directoryPath += "/";
            }
            OUTPUT_DIRECTORY = directoryPath;
            System.out.println("Set output directory for generated files to: " + OUTPUT_DIRECTORY);
        }
    }

    /**
     * Generates CSV files and configuration for locations.
     * 
     * @param numberOfEdge Number of edge servers to generate
     * @param numberOfUsers Number of users to generate
     * @param seed Random seed for reproducibility
     * @throws IOException If file generation fails
     */
    private static void generateLocationFiles(int numberOfEdge, int numberOfUsers, long seed) throws IOException {
        // First initialize the CoordinateConverter from the existing config file
        boolean initialized = CoordinateConverter.initializeFromConfig(LOCATION_CONFIG_FILE);
        if (!initialized) {
            System.out.println("Warning: Could not initialize from config file. Using default coordinate values.");
        }
        
        // Define output directory for generated files
        String outputDir = OUTPUT_DIRECTORY;
        
        // Create the directory if it doesn't exist
        java.io.File dir = new java.io.File(outputDir);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("Created directory: " + outputDir);
            } else {
                System.err.println("Failed to create directory: " + outputDir);
                // Fall back to temporary files if directory creation fails
                outputDir = "";
            }
        }
        
        // Generate location configuration file
        DYNAMIC_LOCATION_CONFIG_FILE = CoordinateConverter.generateLocationConfig(
            outputDir + "location_config_" + seed + ".json");
        
        // Generate resource locations in a grid pattern
        DYNAMIC_RESOURCES_LOCATION_PATH = CoordinateConverter.generateResourceLocationsCSV(
            numberOfEdge, outputDir + "resources_" + seed + ".csv", seed);
        
        // Generate user locations with random distribution
        DYNAMIC_USERS_LOCATION_PATH = CoordinateConverter.generateUserLocationsCSV(
            numberOfUsers, outputDir + "users_" + seed + ".csv", seed);
        
        System.out.println("Generated dynamic location files in " + outputDir + " with seed: " + seed);
    }

    public static void main(String[] args) {
        SPPExperiment.setUseDynamicLocations(false);
        List<SimulationConfig> configs = loadConfigurationsFromYaml();
        
        // Initialize the CSV file with headers once at the beginning
        try (FileWriter fileWriter = new FileWriter(outputFile)) {
            fileWriter.append("Simulation,edges,users,UserType,services,Placement Logic,Avg Resource,Resource stddev,Avg Latency," + 
                              "Latency stddev,Failure ratio,ExecutionTime_ms,PeakMemory_MB,BaselineMemory_MB,PostGCMemory_MB," + 
                              "MemoryGrowth_MB,MemoryRetention_MB,CloudEnergy_Ws,DeviceEnergy_Ws,DeviceEnergyStdDev_Ws\n");
        } catch (IOException e) {
            System.err.println("Error creating output file: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Clear power metrics at START of experiment
        MetricUtils.clearPowerMetrics();
        
        for (int simIndex = 0; simIndex < configs.size(); simIndex++) {
            SimulationConfig config = configs.get(simIndex);
            
            // Create metrics object for this simulation
            PerformanceMetrics metrics = new PerformanceMetrics(config);
            
            // Force garbage collection before starting to get baseline memory
            System.gc();
            System.runFinalization();
            try {
                Thread.sleep(5000); // Allow GC to complete
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // Measure baseline memory
            long baselineMemoryBytes = getCurrentMemoryUsage();
            metrics.setBaselineMemoryBytes(baselineMemoryBytes);
            System.out.println("Baseline memory before simulation: " + (baselineMemoryBytes/1024/1024) + " MB");
            
            // Setup memory monitoring
            AtomicLong peakMemoryBytes = new AtomicLong(0);
            long pid = ProcessHandle.current().pid();
            
            Thread memoryMonitor = new Thread(() -> {
                try {
                    boolean running = true;
                    while (running) {
                        try {
                            Path statusPath = Paths.get("/proc", Long.toString(pid), "status");
                            List<String> lines = Files.readAllLines(statusPath);
                            for (String line : lines) {
                                if (line.startsWith("VmRSS:")) {
                                    String[] parts = line.trim().split("\\s+");
                                    long memoryKb = Long.parseLong(parts[1]);
                                    long memoryBytes = memoryKb * 1024;
                                    peakMemoryBytes.updateAndGet(prev -> Math.max(prev, memoryBytes));
                                    break;
                                }
                            }
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            running = false;
                        } catch (IOException e) {
                            e.printStackTrace();
                            running = false;
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            
            // Initialize SPPMonitor for this simulation
            SPPMonitor.getInstance().initializeSimulation(simIndex);
            
            // Start monitoring and timing
            memoryMonitor.start();
            long startTime = System.currentTimeMillis();
            
            // Run the simulation
            run(config);
            
            // Record metrics
            long endTime = System.currentTimeMillis();
            metrics.setExecutionTimeMs(endTime - startTime);
            memoryMonitor.interrupt();
            
            try {
                memoryMonitor.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            metrics.setPeakMemoryBytes(peakMemoryBytes.get());
            
            // Process metrics from temporary CSV files
            processTempMetricsFiles(simIndex, metrics);
            
            // Immediately write this simulation's results to the CSV
            try (FileWriter fileWriter = new FileWriter(outputFile, true)) { // append mode
                // Write aggregate metrics
                writeSimulationResultRow(fileWriter, simIndex, config, metrics, "Aggregate");
                
                // Write edge server specific row
                writeSimulationResultRow(fileWriter, simIndex, config, metrics, "EDGE_SERVERS");
                
                // Write user type specific rows
                for (String userType : metrics.getUserTypeAvgUtilization().keySet()) {
                    writeSimulationResultRow(fileWriter, simIndex, config, metrics, userType);
                }
            } catch (IOException e) {
                System.err.println("Error writing to output file: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Optional: Delete temporary files
            deleteTempFiles(simIndex);
            
            System.out.println("Simulation completed in " + metrics.getExecutionTimeMs() + 
                              " ms with peak memory usage of " + (metrics.getPeakMemoryBytes()/1024/1024) + " MB");
            System.out.println("Memory after GC: " + (metrics.getPostGCMemoryBytes()/1024/1024) + " MB");
            System.out.println("Memory growth during simulation: " + (metrics.getMemoryGrowthBytes()/1024/1024) + " MB");
            System.out.println("Memory retained after GC: " + (metrics.getMemoryRetentionBytes()/1024/1024) + " MB");
        }
        
        // Print final entity ID information
        System.out.println("\n========= FINAL SUMMARY =========");
        System.out.println("Final ENTITY_ID: " + FogUtils.getCurrentEntityId());
        System.out.println("Final TUPLE_ID: " + FogUtils.getCurrentTupleId());
        System.out.println("Final ACTUAL_TUPLE_ID: " + FogUtils.getCurrentActualTupleId());
        System.out.println("=================================\n");
    }

    /**
     * Processes temporary metrics files for a simulation and updates the metrics object
     * 
     * @param simIndex The index of the simulation
     * @param metrics The metrics object to update
     */
    private static void processTempMetricsFiles(int simIndex, PerformanceMetrics metrics) {
        SPPMonitor monitor = SPPMonitor.getInstance();
        String metricsFile = monitor.getTempMetricsFile();
        String failedPRsFile = monitor.getTempFailedPRsFile();
        
        if (metricsFile == null || failedPRsFile == null) {
            System.err.println("Error: Temporary metrics files not initialized for simulation " + simIndex);
            return;
        }
        
        try {
            // Process metrics CSV file
            List<Double> utilizationValues = new ArrayList<>();
            List<Double> latencyValues = new ArrayList<>();
            Map<String, List<Double>> utilizationByUserType = new HashMap<>();
            Map<String, List<Double>> latencyByUserType = new HashMap<>();
            
            Path metricsPath = Paths.get(metricsFile);
            if (Files.exists(metricsPath)) {
                List<String> lines = Files.readAllLines(metricsPath);
                // Skip header line
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        double utilization = Double.parseDouble(parts[4]);
                        double latency = Double.parseDouble(parts[5]);
                        String userType = parts[3];
                        
                        utilizationValues.add(utilization);
                        latencyValues.add(latency);
                        
                        // Collect by user type
                        if (!utilizationByUserType.containsKey(userType)) {
                            utilizationByUserType.put(userType, new ArrayList<>());
                            latencyByUserType.put(userType, new ArrayList<>());
                        }
                        utilizationByUserType.get(userType).add(utilization);
                        latencyByUserType.get(userType).add(latency);
                    }
                }
            }
            
            // Process failed PRs CSV file
            int totalFailures = 0;
            int totalPRs = 0;
            Map<String, Integer> failuresByUserType = new HashMap<>();
            Map<String, Integer> totalPRsByUserType = new HashMap<>();
            
            Path failedPath = Paths.get(failedPRsFile);
            if (Files.exists(failedPath)) {
                List<String> lines = Files.readAllLines(failedPath);
                // Skip header line
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        String userType = parts[3];
                        int prTotal = Integer.parseInt(parts[5]);
                        
                        totalFailures++;
                        totalPRs = Math.max(totalPRs, prTotal); // Use the highest count as total
                        
                        // Collect by user type
                        if (!failuresByUserType.containsKey(userType)) {
                            failuresByUserType.put(userType, 0);
                            totalPRsByUserType.put(userType, 0);
                        }
                        failuresByUserType.put(userType, failuresByUserType.get(userType) + 1);
                        totalPRsByUserType.put(userType, Math.max(totalPRsByUserType.get(userType), prTotal));
                    }
                }
            }
            
            // Calculate statistics
            if (!utilizationValues.isEmpty()) {
                double[] utilizationStats = MetricUtils.calculateStatistics(utilizationValues);
                double[] latencyStats = MetricUtils.calculateStatistics(latencyValues);
                
                metrics.setAvgUtilization(utilizationStats[0]);
                metrics.setStdDevUtilization(utilizationStats[1]);
                metrics.setAvgLatency(latencyStats[0]);
                metrics.setStdDevLatency(latencyStats[1]);
            }
            
            // Set failure ratio
            if (totalPRs > 0) {
                metrics.setFailureRatio((double) totalFailures / totalPRs);
            }
            
            // Set user type specific metrics
            for (String userType : utilizationByUserType.keySet()) {
                List<Double> userUtilization = utilizationByUserType.get(userType);
                List<Double> userLatency = latencyByUserType.get(userType);
                
                if (!userUtilization.isEmpty()) {
                    double[] userUtilStats = MetricUtils.calculateStatistics(userUtilization);
                    double[] userLatencyStats = MetricUtils.calculateStatistics(userLatency);
                    
                    metrics.setUserTypeUtilization(userType, userUtilStats[0], userUtilStats[1]);
                    metrics.setUserTypeLatency(userType, userLatencyStats[0], userLatencyStats[1]);
                }
                
                // Set user type failure ratio
                int userFailures = failuresByUserType.getOrDefault(userType, 0);
                int userTotalPRs = totalPRsByUserType.getOrDefault(userType, 0);
                if (userTotalPRs > 0) {
                    metrics.setUserTypeFailureRatio(userType, (double) userFailures / userTotalPRs);
                }
            }
            
            // Collect power metrics (these are already being tracked in MetricUtils)
            metrics.setCloudEnergyConsumption(MetricUtils.getCloudEnergyConsumption());
            metrics.setAvgEdgeEnergyConsumption(MetricUtils.getAvgEdgeEnergyConsumption());
            metrics.setStdDevEdgeEnergyConsumption(MetricUtils.getStdDevEdgeEnergyConsumption());
            
        } catch (IOException e) {
            System.err.println("Error processing temporary metrics files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper method to get current memory usage
     */
    private static long getCurrentMemoryUsage() {
        try {
            long pid = ProcessHandle.current().pid();
            Path statusPath = Paths.get("/proc", Long.toString(pid), "status");
            List<String> lines = Files.readAllLines(statusPath);
            for (String line : lines) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    long memoryKb = Long.parseLong(parts[1]);
                    return memoryKb * 1024;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1; // Error case
    }

    /**
     * Loads simulation configurations from a YAML file
     *
     * @return List of SimulationConfig objects
     */
    private static List<SimulationConfig> loadConfigurationsFromYaml() {
        List<SimulationConfig> configs = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(SPPExperiment.CONFIG_FILE)) {
            Yaml yaml = new Yaml();
            List<Map<String, Object>> yamlConfigs = yaml.load(inputStream);

            for (Map<String, Object> configMap : yamlConfigs) {
                int numberOfEdge = ((Number) configMap.get("numberOfEdge")).intValue();
                
                int placementLogic;
                Object placementLogicObj = configMap.get("placementLogic");
                if (placementLogicObj instanceof String) {
                    placementLogic = PlacementLogicFactory.getPlacementLogicCode((String) placementLogicObj);
                    if (placementLogic == -1) {
                        System.err.println("Unknown placement logic name: " + placementLogicObj + ", skipping configuration");
                        continue;
                    }
                } else {
                    placementLogic = ((Number) configMap.get("placementLogic")).intValue();
                }

                // Parse user types map
                Map<String, Integer> usersPerType = new HashMap<>();
                Map<String, Object> userTypeMap = (Map<String, Object>) configMap.get("usersPerType");
                for (Map.Entry<String, Object> entry : userTypeMap.entrySet()) {
                    usersPerType.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                }
                
                // Parse interval values for Poisson distribution
                Map<String, Integer> intervalValues = new HashMap<>();
                if (configMap.containsKey("intervalValues")) {
                    Map<String, Object> intervalMap = (Map<String, Object>) configMap.get("intervalValues");
                    for (Map.Entry<String, Object> entry : intervalMap.entrySet()) {
                        intervalValues.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                    }
//                    System.out.println("Loaded interval values for Poisson distribution: " + intervalValues);
                }

                // Read random seed values if present
                int experimentSeed = 33; // Default value
                int locationSeed = 42; // Default value
                int mobilityStrategySeed = 123; // Default value
                int heuristicSeed = 456; // Default value for heuristic placement algorithms
                
                if (configMap.containsKey("randomSeeds")) {
                    Map<String, Object> randomSeedsMap = (Map<String, Object>) configMap.get("randomSeeds");
                    if (randomSeedsMap.containsKey("experimentSeed")) {
                        experimentSeed = ((Number) randomSeedsMap.get("experimentSeed")).intValue();
                    }
                    if (randomSeedsMap.containsKey("locationSeed")) {
                        locationSeed = ((Number) randomSeedsMap.get("locationSeed")).intValue();
                    }
                    if (randomSeedsMap.containsKey("mobilityStrategySeed")) {
                        mobilityStrategySeed = ((Number) randomSeedsMap.get("mobilityStrategySeed")).intValue();
                    }
                    if (randomSeedsMap.containsKey("heuristicSeed")) {
                        heuristicSeed = ((Number) randomSeedsMap.get("heuristicSeed")).intValue();
                    }
                }
                
                // Read placement process interval if present
                double placementProcessInterval = 60.0; // Default value matches MicroservicePlacementConfig
                if (configMap.containsKey("placementProcessInterval")) {
                    placementProcessInterval = ((Number) configMap.get("placementProcessInterval")).doubleValue();
//                    System.out.println("Using custom placement process interval: " + placementProcessInterval);
                }

                // Check for new format (numberOfApplications and appLoopLength)
                if (configMap.containsKey("numberOfApplications") && configMap.containsKey("appLoopLength")) {
                    int numberOfApplications = ((Number) configMap.get("numberOfApplications")).intValue();
                    int appLoopLength = ((Number) configMap.get("appLoopLength")).intValue();
                    
                    configs.add(new SimulationConfig(
                        numberOfEdge, 
                        placementLogic, 
                        numberOfApplications,
                        appLoopLength,
                        usersPerType,
                        intervalValues,
                        experimentSeed,
                        locationSeed,
                        mobilityStrategySeed,
                        heuristicSeed,
                        placementProcessInterval
                    ));
                    
//                    System.out.println("Loaded configuration with " + numberOfApplications +
//                                     " applications and loop length " + appLoopLength);
                } 
                // Legacy format with appLoopLengthPerType
                else if (configMap.containsKey("appLoopLengthPerType")) {
                    // Parse app loop lengths map (legacy format)
                    Map<String, Integer> appLoopLengthPerType = new HashMap<>();
                    Map<String, Object> loopLengthMap = (Map<String, Object>) configMap.get("appLoopLengthPerType");
                    for (Map.Entry<String, Object> entry : loopLengthMap.entrySet()) {
                        appLoopLengthPerType.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                    }
                    
                    configs.add(new SimulationConfig(
                        numberOfEdge, 
                        placementLogic, 
                        usersPerType, 
                        appLoopLengthPerType,
                        experimentSeed,
                        locationSeed,
                        mobilityStrategySeed
                    ));
                    
                    System.out.println("Loaded legacy configuration with app loop lengths per type");
                }
                else {
                    System.err.println("Configuration missing required fields, skipping");
                }
            }

            System.out.println("Loaded " + configs.size() + " configurations from " + SPPExperiment.CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error loading configurations from " + SPPExperiment.CONFIG_FILE);
            e.printStackTrace();
        }

        return configs;
    }

    private static void run(SimulationConfig simulationConfig) {
        System.out.println("Starting Simon's Experiment...");
        System.out.println(simulationConfig.toString());

        // Debug: Print entity and tuple IDs before reset
        System.out.println("Before reset - ENTITY_ID: " + FogUtils.getCurrentEntityId() + 
                           ", TUPLE_ID: " + FogUtils.getCurrentTupleId());

        try {
            CloudSim.stopSimulation();
        } catch (Exception e) {
            // Ignore errors if no simulation is running
        }

        // Reset ENTITY_ID and related counters FIRST
        FogUtils.clear();
        org.cloudbus.cloudsim.network.datacenter.NetworkConstants.clear();
        TimeKeeper.deleteInstance();
        FogBroker.clear();

        // Debug: Print entity and tuple IDs after reset
        System.out.println("After reset - ENTITY_ID: " + FogUtils.getCurrentEntityId() + 
                           ", TUPLE_ID: " + FogUtils.getCurrentTupleId());

        // Reset THIS class's temporary state
        fogDevices.clear();
        sensors.clear();
        actuators.clear();
        
        // Dynamic file generation if enabled
        if (USE_DYNAMIC_LOCATIONS) {
            try {
                generateLocationFiles(
                    simulationConfig.numberOfEdge,
                    simulationConfig.numberOfUser,
                    simulationConfig.getLocationSeed()
                );
                
                // Override location config file path
                System.out.println("Using dynamically generated location files");
            } catch (IOException e) {
                System.err.println("Failed to generate location files: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Load location configuration from JSON before creating any Location objects
        String locationConfigFile = USE_DYNAMIC_LOCATIONS ? DYNAMIC_LOCATION_CONFIG_FILE : LOCATION_CONFIG_FILE;
        System.out.println("Loading location configuration from " + locationConfigFile);
        boolean configLoaded = LocationConfigLoader.loadAndApplyConfig(locationConfigFile);
        if (!configLoaded) {
            System.err.println("Warning: Failed to load location configuration, using default values from Config.java");
        }
        
        // Make sure Location class is updated with the latest Config values
        Location.refreshConfigValues();

        // Get the experiment seed from configuration
        int experimentSeed = simulationConfig.getExperimentSeed();
        System.out.println("Using experiment seed: " + experimentSeed);
        
        try {
            Log.enable();
            Logger.ENABLED = true;
            Map<String, Integer> usersPerType = simulationConfig.usersPerType;
            int numberOfEdge = simulationConfig.numberOfEdge;
            int numberOfUser = simulationConfig.numberOfUser;
            int placementLogicType = simulationConfig.placementLogic;
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);
            
            // Debug: Print entity IDs after CloudSim.init
            System.out.println("After CloudSim.init - ENTITY_ID: " + FogUtils.getCurrentEntityId());


            FogBroker broker = new FogBroker("broker");
            CloudSim.setFogBrokerId(broker.getId());
            System.out.println("FogBroker ID: " + broker.getId());

            // Create applications based on configuration
            List<Application> applicationPool = new ArrayList<>();
            
            // If using the new configuration format with multiple applications
            if (simulationConfig.getNumberOfApplications() > 0) {
                int numApps = simulationConfig.getNumberOfApplications();
                int appLoopLength = simulationConfig.getAppLoopLength();
                
                System.out.println("Creating " + numApps + " applications with loop length " + appLoopLength);
                
                for (int i = 0; i < numApps; i++) {
                    String appId = "app_" + i;
                    Application application = createApplication(appId, broker.getId(), appLoopLength, experimentSeed + i);
                    application.setUserId(broker.getId());
                    applicationPool.add(application);
                    
                    // Configure broker with application's module
                    FogBroker.getApplicationInfo().put(application.getAppId(), application);

                    String clientModuleName = appId + "_clientModule";
                    FogBroker.getApplicationToFirstServiceMap().put(application, clientModuleName);
                    
                    List<String> secondMicroservices = new ArrayList<>();
                    secondMicroservices.add(appId + "_Service1");
                    FogBroker.getApplicationToSecondServicesMap().put(application, secondMicroservices);
                }
            } 
            // Legacy approach with one application per user type
            else {
                Map<String, Application> applicationsPerType = new HashMap<>();
                for (String userType : simulationConfig.usersPerType.keySet()) {
                    // Use a default app loop length of 3 for legacy configurations
                    int appLoopLength = 3;
                    Application application = createApplication(userType, broker.getId(), appLoopLength, experimentSeed);
                    application.setUserId(broker.getId());
                    applicationsPerType.put(userType, application);
                    applicationPool.add(application);
    
                    // NOTE:Hardcoded
                    String clientModuleName = userType + "_clientModule"; // todo NOTE hardcoded.
                    FogBroker.getApplicationToFirstServiceMap().put(application, clientModuleName);
    
                    List<String> secondMicroservices = new ArrayList<>();
                    secondMicroservices.add(userType + "_Service1");
                    FogBroker.getApplicationToSecondServicesMap().put(application, secondMicroservices);
                }
                
                // Create fog devices with user types
                createFogDevices(broker.getId(), applicationsPerType, numberOfEdge, usersPerType, simulationConfig);
            }
            
            // If using the new configuration, create fog devices without specifying applications per user type
            if (simulationConfig.getNumberOfApplications() > 0) {
                createFogDevices(broker.getId(), null, numberOfEdge, usersPerType, simulationConfig);
            }

            // Set the location seed for random location generation
            Location.setDefaultRandomSeed(simulationConfig.getLocationSeed());
            System.out.println("Set Location default seed to: " + simulationConfig.getLocationSeed());

            /**
             * Central controller for performing preprocessing functions
             */
            PlacementSimulationController microservicesController;
            
            // Get interval values from the configuration
            Map<String, Integer> intervalValues = simulationConfig.getIntervalValues();
            
            if (intervalValues != null && !intervalValues.isEmpty()) {
                System.out.println("Using interval values for Poisson distribution: " + intervalValues);
                System.out.println("Using placement process interval: " + simulationConfig.getPlacementProcessInterval());
                
                microservicesController = new PlacementSimulationController(
                    "controller",
                    fogDevices,
                    sensors,
                    actuators,
                    applicationPool,
                    placementLogicType,
                    intervalValues,
                    simulationConfig.getExperimentSeed(),
                    simulationConfig.getHeuristicSeed(),
                    simulationConfig.getPlacementProcessInterval()
                );
            } else {throw new NullPointerException("Need interval values in experiment config");}
            
            // Register device-sensor-actuator mappings
            microservicesController.registerDeviceMappings();
            
            try {
                System.out.println("Initializing location data from CSV files...");
                microservicesController.enableMobility();
                
                // Use dynamic paths if enabled
                String resourcesPath = USE_DYNAMIC_LOCATIONS ? DYNAMIC_RESOURCES_LOCATION_PATH : RESOURCES_LOCATION_PATH;
                String usersPath = USE_DYNAMIC_LOCATIONS ? DYNAMIC_USERS_LOCATION_PATH : USERS_LOCATION_PATH;
                
                microservicesController.initializeLocationData(
                    resourcesPath,
                    usersPath,
                    numberOfEdge + 1, // cloud
                    numberOfUser,
                    experimentSeed
                );
                System.out.println("Location data initialization complete.");

                microservicesController.setPathingSeeds(simulationConfig.getMobilityStrategySeed());
                System.out.println("Mobility enabled with seed: " + simulationConfig.getMobilityStrategySeed());
                
                microservicesController.completeInitialization();
                System.out.println("Controller initialization completed with proximity-based connections.");
            } catch (IOException e) {
                System.out.println("Failed to initialize location data: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            List<PlacementRequest> placementRequests = new ArrayList<>();
            for (SPPFogDevice device : microservicesController.getUserDevices()) {
                try {
                    PlacementRequest prototypePR = microservicesController.createNewPlacementRequestForDevice(device.getId());
                    if (prototypePR != null) {
                        placementRequests.add(prototypePR);
                    }
                } catch (NullPointerException e) {
                    System.err.println("Failed to create placement request for device " + device.getName() + ": " + e.getMessage());
                }
            }

            microservicesController.submitPlacementRequests(placementRequests, 1);

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            Log.printLine("Simon app START!");
            Log.printLine(String.format("Placement Logic: %d", placementLogicType));

            // Schedule the opera accident event
            double operaExplosionTime = 3600.0;
            CloudSim.send(microservicesController.getId(), microservicesController.getId(), operaExplosionTime, 
                          FogEvents.OPERA_ACCIDENT_EVENT, null);

            CloudSim.startSimulation();
//            CloudSim.stopSimulation();
            // TODO Possible mega cleanup/metric collection function
            SPPMonitor.getInstance().incrementSimulationRoundNumber();
            
            // Collect power metrics
            Map<String, Double> powerMetrics = collectPowerMetrics();
            MetricUtils.setCloudEnergyConsumption(powerMetrics.get("cloudEnergyConsumption"));
            MetricUtils.setAvgEdgeEnergyConsumption(powerMetrics.get("avgEdgeEnergyConsumption"));
            MetricUtils.setStdDevEdgeEnergyConsumption(powerMetrics.get("stdDevEdgeEnergyConsumption"));

            System.out.println("Cloud Energy Consumption: " + powerMetrics.get("cloudEnergyConsumption") + " Watt-seconds");
            System.out.println("Average Edge Energy Consumption: " + powerMetrics.get("avgEdgeEnergyConsumption") + " Watt-seconds");
            System.out.println("Edge Energy Consumption StdDev: " + powerMetrics.get("stdDevEdgeEnergyConsumption") + " Watt-seconds");
            
            System.out.println("Simon app finished!");
            
            // Reset controller's sequence counters after simulation finishes
            microservicesController.resetSequenceCounters();
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates fog devices for the simulation.
     * This method creates a cloud device, gateway devices, and user devices.
     * It now handles device creation without relying on the LocationHandler.
     *
     * @param brokerId User ID for the broker
     * @param applicationsPerType Map of user types to their applications (may be null for new configuration)
     * @param numberOfEdge Number of edge devices to create
     * @param usersPerType Map of user types to their counts
     * @param simulationConfig Configuration containing random seeds
     */
    private static void createFogDevices(int brokerId, Map<String, Application> applicationsPerType,
                                         int numberOfEdge, Map<String, Integer> usersPerType, 
                                         SimulationConfig simulationConfig) {
        // Create cloud device at the top of the hierarchy
        SPPFogDevice cloud = createFogDevice("cloud", 44800, -1, 40000, 100, 10000, 0.01, 16 * 103, 16 * 83.25, SPPFogDevice.CLOUD);
        cloud.setParentId(References.NOT_SET);
        cloud.setLevel(0);
        
        // Set the placement process interval from the simulation config
        cloud.setPlacementProcessInterval(simulationConfig.getPlacementProcessInterval());
        System.out.println("Cloud device using placement process interval: " + cloud.getPlacementProcessInterval());
        
        fogDevices.add(cloud);
        Random random = new Random(simulationConfig.getExperimentSeed());

        // Create gateway devices
        for (int i = 0; i < numberOfEdge; i++) {
            // todo 1000-4000 mips, 1000 - 32000 mb ram (mb because value must be int)
            SPPFogDevice gateway = createFogDevice(
                    "gateway_" + i,
                    random.nextInt(3001) + 1000,
                    -1, // Undefined. Let Location Manager determine.
                    random.nextInt(31001) + 1000,
                    10000,
                    10000,
                    0.0,
                    107.339,
                    83.4333,
                    SPPFogDevice.FCN
            );
            gateway.setParentId(cloud.getId());
            // Let latency be set by LocationManager in connectWithLatencies
            gateway.setLevel(1);
            fogDevices.add(gateway);
        }

        for (String userType : usersPerType.keySet()) {
            // todo This is a hardcoded check. Edit based on your user types.
            if (!(Objects.equals(userType, SPPFogDevice.GENERIC_USER) ||
                Objects.equals(userType, SPPFogDevice.AMBULANCE_USER) ||
                Objects.equals(userType, SPPFogDevice.OPERA_USER) ||
                Objects.equals(userType, SPPFogDevice.IMMOBILE_USER))) {
                throw new NullPointerException("Invalid Type");
            }

            int numUsersOfThisType = usersPerType.get(userType);
            
            // For new configuration (applicationPool), create users without specific applications
            if (applicationsPerType == null) {
                for (int i = 0; i < numUsersOfThisType; i++) {
                    FogDevice mobile = addUser(userType, userType + "_" + i, brokerId, null, References.NOT_SET);
                    mobile.setLevel(2);
                    fogDevices.add(mobile);
                }
            }
            // For legacy configuration with applications per user type
            else {
                Application appForThisType = applicationsPerType.get(userType);
                for (int i = 0; i < numUsersOfThisType; i++) {
                    // Use the specific application for this user type
                    FogDevice mobile = addUser(userType, userType + "_" + i, brokerId, appForThisType, References.NOT_SET);
                    mobile.setLevel(2);
                    fogDevices.add(mobile);
                }
            }
        }
    }

    /**
     * Creates a vanilla fog device
     *
     * @param nodeName    name of the device to be used in simulation
     * @param mips        MIPS
     * @param ram         RAM
     * @param upBw        uplink bandwidth
     * @param downBw      downlink bandwidth
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    private static SPPFogDevice createFogDevice(String nodeName, long mips, double upLinkLatency,
                                                int ram, long upBw, long downBw, double ratePerMips, double busyPower, double idlePower, String deviceType) {

        // Debug: Print entity ID before creating device
        System.out.println("Creating fog device '" + nodeName + "', ENTITY_ID before: " + FogUtils.getCurrentEntityId());
        
        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        System.out.println("Created host with ID: " + hostId);
        
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new VmSchedulerTimeSharedEnergy(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        SPPFogDevice fogdevice = null;
        try {
            fogdevice = new SPPFogDevice(
                    nodeName,
                    characteristics,
                    new AppModuleAllocationPolicy(hostList),
                    storageList,
                    10,
                    upBw,
                    downBw,
                    10000,
                    upLinkLatency,
                    ratePerMips,
                    deviceType
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fogdevice;
    }

    private static FogDevice addUser(String userType, String name, int brokerId, Application app, int parentId) {
        // NOTE: Assumes userType has been verified before calling addUser.
        SPPFogDevice mobile = createFogDevice(name, 200, -1, 200, 10000, 270, 0, 87.53, 82.44, userType);
        mobile.setParentId(parentId);
        
        if (app != null) {
            // Create sensor and actuator with specific application
            Sensor mobileSensor = new PassiveSensor("s-" + name, "SENSOR", brokerId, app.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
            mobileSensor.setApp(app);
            sensors.add(mobileSensor);
            
            Actuator mobileDisplay = new Actuator("a-" + name, brokerId, app.getAppId(), "DISPLAY");
            actuators.add(mobileDisplay);
    
            mobileSensor.setGatewayDeviceId(mobile.getId());
            mobileSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
    
            mobileDisplay.setGatewayDeviceId(mobile.getId());
            mobileDisplay.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
            mobileDisplay.setApp(app);
        } else {
            // If no app create sensor and actuator without specific application
            // The application will be randomly assigned during placement request generation
            Sensor mobileSensor = new PassiveSensor("s-" + name, "SENSOR", brokerId, null, new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
            sensors.add(mobileSensor);
            
            Actuator mobileDisplay = new Actuator("a-" + name, brokerId, null, "DISPLAY");
            actuators.add(mobileDisplay);
    
            mobileSensor.setGatewayDeviceId(mobile.getId());
            mobileSensor.setLatency(6.0);
    
            mobileDisplay.setGatewayDeviceId(mobile.getId());
            mobileDisplay.setLatency(1.0);
        }

        return mobile;
    }

    private static MyApplication createApplication(String userType, int brokerId, int numServices) {
        return createApplication(userType, brokerId, numServices, 33); // Use default seed value
    }

    private static MyApplication createApplication(String appId, int brokerId, int numServices, int randomSeed) {
        MyApplication application = MyApplication.createMyApplication(appId, brokerId);
        Random random = new Random(randomSeed);

        // Prefix all module names with the user type FOR UNIQUENESS OF MODULE NAMES
        String clientModuleName = appId + "_clientModule";
        application.addAppModule(clientModuleName, 4, 4, 50);

        for (int i = 1; i <= numServices; i++) {
            String serviceName = appId + "_Service" + i;
            application.addAppModule(serviceName, random.nextInt(501) + 250, random.nextInt(501) + 250, 500);
        }

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("SENSOR", clientModuleName, 14, 50, "SENSOR", Tuple.UP, AppEdge.SENSOR);

        String firstServiceName = appId + "_Service1";
        application.addAppEdge(clientModuleName, firstServiceName, 5, 500, // TODO change back to 10000 and 500
                appId + "_RAW_DATA", Tuple.UP, AppEdge.MODULE);
        for (int i = 1; i < numServices; i++) {
            String sourceModule = appId + "_Service" + i;
            String destModule = appId + "_Service" + (i+1);
            String tupleType = appId + "_FILTERED_DATA" + i;
            application.addAppEdge(sourceModule, destModule, 5, 500, tupleType, Tuple.UP, AppEdge.MODULE); // TODO change back to 10000 and 500
        }

        String lastServiceName = appId + "_Service" + numServices;
        application.addAppEdge(lastServiceName, clientModuleName, 4, 500,
                appId + "_RESULT", Tuple.DOWN, AppEdge.MODULE);

        // Connect to actuator
        application.addAppEdge(clientModuleName, "DISPLAY", 4, 500,
                appId + "_RESULT_DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping(clientModuleName, "SENSOR", appId + "_RAW_DATA",
                new FractionalSelectivity(1.0));

        for (int i = 1; i < numServices; i++) {
            String sourceModule = appId + "_Service" + i;
            String inputTupleType = (i == 1) ? appId + "_RAW_DATA" : appId + "_FILTERED_DATA" + (i-1);
            String outputTupleType = appId + "_FILTERED_DATA" + i;

            application.addTupleMapping(sourceModule, inputTupleType, outputTupleType,
                    new FractionalSelectivity(1.0));
        }

        String lastInputTupleType = (numServices == 1) ? appId + "_RAW_DATA" : appId + "_FILTERED_DATA" + (numServices-1);
        application.addTupleMapping(appId + "_Service" + numServices, lastInputTupleType,
                appId + "_RESULT", new FractionalSelectivity(1.0));

        application.addTupleMapping(clientModuleName, appId + "_RESULT",
                appId + "_RESULT_DISPLAY", new FractionalSelectivity(1.0));

        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add(clientModuleName);

            for (int i = 1; i <= numServices; i++) {
                add(appId + "_Service" + i);
            }

            add(clientModuleName);
            add("DISPLAY");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop);
        }};
        application.setLoops(loops);

        return application;
    }

    /**
     * Collects power consumption metrics from all fog devices
     * @return Map containing power metrics: cloud energy consumption, average edge energy consumption, etc.
     */
    private static Map<String, Double> collectPowerMetrics() {
        Map<String, Double> powerMetrics = new HashMap<>();
        
        double cloudEnergyConsumption = 0.0;
        List<Double> edgeEnergyConsumptions = new ArrayList<>();
        
        // For user-type specific energy tracking
        Map<String, List<Double>> userTypeEnergyConsumptions = new HashMap<>();
        
        // Initialize maps for all known user types
        userTypeEnergyConsumptions.put(SPPFogDevice.GENERIC_USER, new ArrayList<>());
        userTypeEnergyConsumptions.put(SPPFogDevice.AMBULANCE_USER, new ArrayList<>());
        userTypeEnergyConsumptions.put(SPPFogDevice.OPERA_USER, new ArrayList<>());
        userTypeEnergyConsumptions.put(SPPFogDevice.IMMOBILE_USER, new ArrayList<>());
        
        for (FogDevice device : fogDevices) {
            SPPFogDevice sppDevice = (SPPFogDevice) device;
            double energyConsumption = device.getEnergyConsumption();
            
            if (sppDevice.getDeviceType().equals(SPPFogDevice.CLOUD)) {
                cloudEnergyConsumption = energyConsumption;
            } else if (sppDevice.getDeviceType().equals(SPPFogDevice.FCN)) {
                edgeEnergyConsumptions.add(energyConsumption);
            } else if (sppDevice.isUserDevice()) {
                // Add to the appropriate user type list
                String userType = sppDevice.getDeviceType();
                userTypeEnergyConsumptions.get(userType).add(energyConsumption);
            }
        }
        
        // Calculate average and standard deviation for edge servers
        double avgEdgeEnergy = 0.0;
        double stdDevEdgeEnergy = 0.0;
        
        if (!edgeEnergyConsumptions.isEmpty()) {
            double sum = 0;
            for (Double value : edgeEnergyConsumptions) {
                sum += value;
            }
            avgEdgeEnergy = sum / edgeEnergyConsumptions.size();
            
            double variance = 0;
            for (Double value : edgeEnergyConsumptions) {
                variance += Math.pow(value - avgEdgeEnergy, 2);
            }
            variance /= edgeEnergyConsumptions.size();
            
            stdDevEdgeEnergy = Math.sqrt(variance);
        }
        
        // Calculate user-type-specific energy metrics
        for (String userType : userTypeEnergyConsumptions.keySet()) {
            List<Double> values = userTypeEnergyConsumptions.get(userType);
            if (!values.isEmpty()) {
                double sum = 0;
                for (Double value : values) {
                    sum += value;
                }
                double avg = sum / values.size();
                
                double variance = 0;
                for (Double value : values) {
                    variance += Math.pow(value - avg, 2);
                }
                variance /= values.size();
                double stdDev = Math.sqrt(variance);
                
                // Store in MetricUtils
                MetricUtils.setUserTypeEnergyConsumption(userType, avg, stdDev);
                
                System.out.println(userType + " Energy Consumption: " + avg + " Watt-seconds (StdDev: " + stdDev + ")");
            }
        }
        
        powerMetrics.put("cloudEnergyConsumption", cloudEnergyConsumption);
        powerMetrics.put("avgEdgeEnergyConsumption", avgEdgeEnergy);
        powerMetrics.put("stdDevEdgeEnergyConsumption", stdDevEdgeEnergy);
        
        return powerMetrics;
    }

    // Helper method to write a single row to the CSV
    private static void writeSimulationResultRow(FileWriter writer, int simIndex, SimulationConfig config, 
                                                PerformanceMetrics metrics, String userType) throws IOException {
        // Get the service count based on config format
        String serviceCount = config.getNumberOfApplications() > 0 
                             ? String.valueOf(config.getAppLoopLength()) 
                             : "Legacy";
        
        // Get memory metrics
        long peakMemoryMB = metrics.getPeakMemoryBytes() / (1024 * 1024);
        long baselineMemoryMB = metrics.getBaselineMemoryBytes() / (1024 * 1024);
        long postGCMemoryMB = metrics.getPostGCMemoryBytes() / (1024 * 1024);
        long memoryGrowthMB = metrics.getMemoryGrowthBytes() / (1024 * 1024);
        long memoryRetentionMB = metrics.getMemoryRetentionBytes() / (1024 * 1024);
        
        // Get proper user type specific metrics
        double utilizationAvg, utilizationStdDev, latencyAvg, latencyStdDev, failureRatio;
        int usersOfThisType = 0;
        
        if (userType.equals("Aggregate")) {
            utilizationAvg = metrics.getAvgUtilization();
            utilizationStdDev = metrics.getStdDevUtilization();
            latencyAvg = metrics.getAvgLatency();
            latencyStdDev = metrics.getStdDevLatency();
            failureRatio = metrics.getFailureRatio();
            usersOfThisType = config.getNumberOfUser();
        } 
        else if (userType.equals("EDGE_SERVERS")) {
            utilizationAvg = 0.0;
            utilizationStdDev = 0.0;
            latencyAvg = 0.0;
            latencyStdDev = 0.0;
            failureRatio = 0.0;
            usersOfThisType = 0;
        }
        else {
            utilizationAvg = metrics.getUserTypeAvgUtilization().getOrDefault(userType, 0.0);
            utilizationStdDev = metrics.getUserTypeStdDevUtilization().getOrDefault(userType, 0.0);
            latencyAvg = metrics.getUserTypeAvgLatency().getOrDefault(userType, 0.0);
            latencyStdDev = metrics.getUserTypeStdDevLatency().getOrDefault(userType, 0.0);
            failureRatio = metrics.getUserTypeFailureRatio().getOrDefault(userType, 0.0);
            usersOfThisType = config.getUsersPerType().getOrDefault(userType, 0);
        }
        
        // Write the row
        writer.append(String.format(
            "%d,%d,%d,%s,%s,%s,%f,%f,%f,%f,%f,%d,%d,%d,%d,%d,%d,%f,%f,%f\n",
            simIndex,
            config.getNumberOfEdge(),
            usersOfThisType,
            userType,
            serviceCount,
            MetricUtils.getHeuristicName(config.getPlacementLogic()),
            utilizationAvg,
            utilizationStdDev,
            latencyAvg,
            latencyStdDev,
            failureRatio,
            metrics.getExecutionTimeMs(),
            peakMemoryMB,
            baselineMemoryMB,
            postGCMemoryMB,
            memoryGrowthMB,
            memoryRetentionMB,
            metrics.getCloudEnergyConsumption(),
            metrics.getAvgEdgeEnergyConsumption(),
            metrics.getStdDevEdgeEnergyConsumption()
        ));
    }

    // Optional method to delete temp files after processing
    private static void deleteTempFiles(int simIndex) {
        String metricsFile = SPPMonitor.getInstance().getTempMetricsFile();
        String failedPRsFile = SPPMonitor.getInstance().getTempFailedPRsFile();
        
        if (metricsFile != null) {
            try {
                Files.deleteIfExists(Paths.get(metricsFile));
                System.out.println("Deleted temporary metrics file: " + metricsFile);
            } catch (IOException e) {
                System.err.println("Could not delete temporary metrics file: " + e.getMessage());
            }
        }
        
        if (failedPRsFile != null) {
            try {
                Files.deleteIfExists(Paths.get(failedPRsFile));
                System.out.println("Deleted temporary failed PRs file: " + failedPRsFile);
            } catch (IOException e) {
                System.err.println("Could not delete temporary failed PRs file: " + e.getMessage());
            }
        }
    }
}


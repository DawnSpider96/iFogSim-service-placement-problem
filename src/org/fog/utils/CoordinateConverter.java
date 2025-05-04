package org.fog.utils;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Utility for converting arbitrary coordinates to GPS coordinates and generating CSVs.
 */
public class CoordinateConverter {
    // Default Dublin center coordinates (used only if config file doesn't exist)
    private static double CENTER_LAT = 53.356164;
    private static double CENTER_LON = -6.2536097;
    
    // Simulation bounds (2km in each direction by default)
    private static double LAT_RANGE = 0.018; // ~2km
    private static double LON_RANGE = 0.03;  // ~2km
    
    // Min/Max bounds calculation - will be updated from config
    private static double MIN_LAT = CENTER_LAT - LAT_RANGE;
    private static double MAX_LAT = CENTER_LAT + LAT_RANGE;
    private static double MIN_LON = CENTER_LON - LON_RANGE;
    private static double MAX_LON = CENTER_LON + LON_RANGE;
    
    // World dimensions in the other simulation (arbitrary units)
    private static double WORLD_X = 4000; // 4km in meters
    private static double WORLD_Y = 4000; // 4km in meters

    // Points of interest from config
    private static Map<String, double[]> pointsOfInterest = new HashMap<>();

    // Flag to check if initialized
    private static boolean initialized = false;
    
    /**
     * Initializes coordinate converter from a location config file
     * 
     * @param configFilePath Path to location_config.json
     * @return true if initialization successful, false otherwise
     */
    public static boolean initializeFromConfig(String configFilePath) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject config = (JSONObject) parser.parse(new FileReader(configFilePath));
            
            // Read min/max values
            if (config.containsKey("minLat")) {
                MIN_LAT = ((Number) config.get("minLat")).doubleValue();
            }
            if (config.containsKey("maxLat")) {
                MAX_LAT = ((Number) config.get("maxLat")).doubleValue();
            }
            if (config.containsKey("minLon")) {
                MIN_LON = ((Number) config.get("minLon")).doubleValue();
            }
            if (config.containsKey("maxLon")) {
                MAX_LON = ((Number) config.get("maxLon")).doubleValue();
            }
            
            // Calculate center coordinates and ranges
            CENTER_LAT = (MIN_LAT + MAX_LAT) / 2;
            CENTER_LON = (MIN_LON + MAX_LON) / 2;
            LAT_RANGE = (MAX_LAT - MIN_LAT) / 2;
            LON_RANGE = (MAX_LON - MIN_LON) / 2;
            
            // Calculate approximate world dimensions in meters
            // 1 degree latitude ≈ 111km
            double latDistanceKm = (MAX_LAT - MIN_LAT) * 111.0;
            // 1 degree longitude ≈ 111 * cos(lat) km
            double lonDistanceKm = (MAX_LON - MIN_LON) * 111.0 * Math.cos(Math.toRadians(CENTER_LAT));
            
            // Set world dimensions in meters (1km = 1000m)
            WORLD_X = lonDistanceKm * 1000;
            WORLD_Y = latDistanceKm * 1000;
            
            // Read points of interest
            if (config.containsKey("pointsOfInterest")) {
                JSONObject pois = (JSONObject) config.get("pointsOfInterest");
                for (Object key : pois.keySet()) {
                    String name = (String) key;
                    JSONObject poi = (JSONObject) pois.get(name);
                    double lat = ((Number) poi.get("lat")).doubleValue();
                    double lon = ((Number) poi.get("lon")).doubleValue();
                    pointsOfInterest.put(name, new double[]{lat, lon});
                }
            }
            
            initialized = true;
            
            System.out.println("Initialized CoordinateConverter with values from " + configFilePath);
            System.out.println("  Center: " + CENTER_LAT + ", " + CENTER_LON);
            System.out.println("  Bounds: [" + MIN_LAT + ", " + MIN_LON + "] to [" + MAX_LAT + ", " + MAX_LON + "]");
            System.out.println("  World dimensions: " + WORLD_X + "m x " + WORLD_Y + "m");
            
            return true;
        } catch (IOException | ParseException e) {
            System.err.println("Error initializing CoordinateConverter from config: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Helper method to ensure the parent directory exists for a file
     *
     * @param filePath The file path to check
     * @return true if directory exists or was created, false otherwise
     */
    private static boolean ensureDirectoryExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return true; // If using temp file, no need to create directories
        }
        
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        
        if (parentDir != null && !parentDir.exists()) {
            return parentDir.mkdirs();
        }
        
        return true;
    }

    /**
     * Generates resource locations (edge servers) in a grid pattern.
     * 
     * @param numResources Number of edge server resources
     * @param filePath Path to save the CSV file
     * @param seed Random seed for reproducibility
     * @throws IOException If file writing fails
     */
    public static String generateResourceLocationsCSV(int numResources, String filePath, long seed) throws IOException {
        if (!initialized) {
            System.out.println("Warning: CoordinateConverter not initialized from config. Using default values.");
        }
        
        Random random = new Random(seed);
        
        // Create temporary file if no path provided
        if (filePath == null || filePath.isEmpty()) {
            File tempFile = File.createTempFile("resourceLocations", ".csv");
            filePath = tempFile.getAbsolutePath();
        } else {
            // Ensure the directory exists
            ensureDirectoryExists(filePath);
        }
        
        try (FileWriter writer = new FileWriter(filePath)) {
            // Generate cloud position with random offset (using same formula as edge servers)
            double cloudXPos = WORLD_X / 2;
            double cloudYPos = WORLD_Y / 2;
            cloudXPos += random.nextDouble() * WORLD_X / 2 - WORLD_X / 4;
            cloudYPos += random.nextDouble() * WORLD_Y / 2 - WORLD_Y / 4;
            
            double[] cloudGps = convertToGPS(cloudXPos, cloudYPos);
            
            // Write cloud as the first line (ID 0)
            writer.write("0," + cloudGps[0] + "," + cloudGps[1] + ",0,0,-1,VIC,DataCenter\n");
            
            // Calculate grid dimensions similar to your grid position distribution
            double ratio = WORLD_X / WORLD_Y;
            int xCount = (int) Math.ceil(Math.sqrt(ratio * numResources));
            int yCount = (int) Math.ceil(Math.sqrt((1 / ratio) * numResources));
            
            double xStep = WORLD_X / xCount;
            double yStep = WORLD_Y / yCount;
            
            int resourceId = 1; // Starting from 1 for edge servers
            
            for (int x = 0; x < xCount && resourceId <= numResources; x++) {
                for (int y = 0; y < yCount && resourceId <= numResources; y++) {
                    // Calculate position with random offset in cell
                    double xPos = x * xStep + xStep / 2;
                    double yPos = y * yStep + yStep / 2;
                    
                    // Add random offset (optional)
                    xPos += random.nextDouble() * xStep / 2 - xStep / 4;
                    yPos += random.nextDouble() * yStep / 2 - yStep / 4;
                    
                    // Convert to GPS coordinates
                    double[] gps = convertToGPS(xPos, yPos);
                    
                    // Write to CSV with IDs starting at 1 (instead of 0)
                    writer.write(
                            resourceId + "," + gps[0] + "," + gps[1]
                            + ",-1,1,0,VIC"
                            + "\n"
                    );
                    resourceId++;
                }
            }
        }
        
        System.out.println("Generated resource locations CSV at: " + filePath);
        return filePath;
    }
    
    /**
     * Generates user locations in a random distribution.
     * 
     * @param numUsers Number of user devices
     * @param filePath Path to save the CSV file 
     * @param seed Random seed for reproducibility
     * @throws IOException If file writing fails
     */
    public static String generateUserLocationsCSV(int numUsers, String filePath, long seed) throws IOException {
        if (!initialized) {
            System.out.println("Warning: CoordinateConverter not initialized from config. Using default values.");
        }
        
        Random random = new Random(seed);
        
        // Create temporary file if no path provided
        if (filePath == null || filePath.isEmpty()) {
            File tempFile = File.createTempFile("userLocations", ".csv");
            filePath = tempFile.getAbsolutePath();
        } else {
            // Ensure the directory exists
            ensureDirectoryExists(filePath);
        }
        
        try (FileWriter writer = new FileWriter(filePath)) {
            // Write header
//            writer.write("id,latitude,longitude\n");
            
            for (int userId = 1; userId <= numUsers; userId++) {
                // Generate random position within world bounds
                double xPos = random.nextDouble() * WORLD_X;
                double yPos = random.nextDouble() * WORLD_Y;
                
                // Convert to GPS coordinates
                double[] gps = convertToGPS(xPos, yPos);
                
                // Write to CSV
                writer.write(gps[0] + "," + gps[1] + "\n");
            }
        }
        
        System.out.println("Generated user locations CSV at: " + filePath);
        return filePath;
    }
    
    /**
     * Converts x,y coordinates from the arbitrary world to GPS coordinates.
     * 
     * @param x X coordinate in the arbitrary world (0 to WORLD_X)
     * @param y Y coordinate in the arbitrary world (0 to WORLD_Y)
     * @return Array with [latitude, longitude]
     */
    private static double[] convertToGPS(double x, double y) {
        // Normalize to 0-1 range
        double normalizedX = x / WORLD_X;
        double normalizedY = y / WORLD_Y;
        
        // Convert to GPS coordinates within bounds
        double lat = MIN_LAT + normalizedY * (MAX_LAT - MIN_LAT);
        double lon = MIN_LON + normalizedX * (MAX_LON - MIN_LON);
        
        return new double[] {lat, lon};
    }
    
    /**
     * Creates a configuration file for LocationConfigLoader.
     * 
     * @param filePath Path to save the JSON file
     * @throws IOException If file writing fails
     */
    public static String generateLocationConfig(String filePath) throws IOException {
        // Create temporary file if no path provided
        if (filePath == null || filePath.isEmpty()) {
            File tempFile = File.createTempFile("locationConfig", ".json");
            filePath = tempFile.getAbsolutePath();
        } else {
            // Ensure the directory exists
            ensureDirectoryExists(filePath);
        }
        
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("{\n");
            writer.write("  \"minLat\": " + MIN_LAT + ",\n");
            writer.write("  \"maxLat\": " + MAX_LAT + ",\n");
            writer.write("  \"minLon\": " + MIN_LON + ",\n");
            writer.write("  \"maxLon\": " + MAX_LON + ",\n");
            writer.write("  \"boundary\": [\n");
            writer.write("    [" + MIN_LAT + ", " + MIN_LON + "],\n");
            writer.write("    [" + MAX_LAT + ", " + MIN_LON + "],\n");
            writer.write("    [" + MAX_LAT + ", " + MAX_LON + "],\n");
            writer.write("    [" + MIN_LAT + ", " + MAX_LON + "]\n");
            writer.write("  ],\n");
            writer.write("  \"pointsOfInterest\": {\n");
            writer.write("    \"center\": {\"lat\": " + CENTER_LAT + ", \"lon\": " + CENTER_LON + "}");
            
            // Add any additional points of interest
            for (Map.Entry<String, double[]> entry : pointsOfInterest.entrySet()) {
                if (!entry.getKey().equals("center")) {
                    writer.write(",\n    \"" + entry.getKey() + "\": {\"lat\": " + 
                              entry.getValue()[0] + ", \"lon\": " + entry.getValue()[1] + "}");
                }
            }
            
            writer.write("\n  }\n");
            writer.write("}");
        }
        
        System.out.println("Generated location config at: " + filePath);
        return filePath;
    }
} 
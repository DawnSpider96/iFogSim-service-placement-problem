package org.fog.utils;

import org.fog.mobilitydata.Location;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for loading location configuration from JSON files and applying it to Config class.
 */
public class LocationConfigLoader {
    
    // Cache for original field values, so we can report if they were changed
    private static final Map<String, Object> originalValues = new HashMap<>();

    /**
     * Loads location configuration from a JSON file and applies it to the Config class.
     * Uses the setter methods to modify values.
     *
     * @param jsonFilePath path to the JSON configuration file
     * @return true if loading was successful, false otherwise
     */
    public static boolean loadAndApplyConfig(String jsonFilePath) {
        try {
            // Store original values to report changes
            storeOriginalValues();
            
            JSONParser parser = new JSONParser();
            JSONObject config = (JSONObject) parser.parse(new FileReader(jsonFilePath));
            
            // Apply geographic boundaries
            if (config.containsKey("boundary")) {
                applyBoundary((JSONArray) config.get("boundary"));
            }
            
            // Apply min/max lat/lon
            if (config.containsKey("minLat")) {
                Config.setMinLat(((Number) config.get("minLat")).doubleValue());
            }
            if (config.containsKey("maxLat")) {
                Config.setMaxLat(((Number) config.get("maxLat")).doubleValue());
            }
            if (config.containsKey("minLon")) {
                Config.setMinLon(((Number) config.get("minLon")).doubleValue());
            }
            if (config.containsKey("maxLon")) {
                Config.setMaxLon(((Number) config.get("maxLon")).doubleValue());
            }
            
            // Apply points of interest
            if (config.containsKey("pointsOfInterest")) {
                applyPointsOfInterest((JSONObject) config.get("pointsOfInterest"));
            }
            
            // Infer and set geographic area
            inferGeographicArea(config);
            
            // Verify that all fields are properly set
            verifyConfigConsistency();
            
            // Report changes
            reportChanges();
            
            System.out.println("Successfully loaded location configuration from " + jsonFilePath);
            return true;
        } catch (Exception e) {
            System.err.println("Error loading location configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Stores the original values of Config fields before modification
     */
    private static void storeOriginalValues() {
        // Store min/max values
        originalValues.put("minLat", Config.getMinLat());
        originalValues.put("maxLat", Config.getMaxLat());
        originalValues.put("minLon", Config.getMinLon());
        originalValues.put("maxLon", Config.getMaxLon());
        
        // Store geographic area
        originalValues.put("geographicArea", Config.getGeographicArea());
        
        // Store points of interest
        originalValues.put("pointsOfInterest", Config.getAllPointsOfInterest());
        
        // Store boundary (deep copy to avoid reference issues)
        double[][] original = Config.getBOUNDARY();
        double[][] copy = new double[original.length][original[0].length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        originalValues.put("BOUNDARY", copy);
    }
    
    /**
     * Reports changes to the Config fields after modification
     */
    private static void reportChanges() {
        // Report changes to min/max values
        reportChange("minLat", (Double)originalValues.get("minLat"), Config.getMinLat());
        reportChange("maxLat", (Double)originalValues.get("maxLat"), Config.getMaxLat());
        reportChange("minLon", (Double)originalValues.get("minLon"), Config.getMinLon());
        reportChange("maxLon", (Double)originalValues.get("maxLon"), Config.getMaxLon());
        
        // Report changes to geographic area
        reportChange("geographicArea", (String)originalValues.get("geographicArea"), Config.getGeographicArea());
        
        // Report changes to boundary
        reportBoundaryChanges();
        
        // Report changes to points of interest
        reportPointsOfInterestChanges();
    }
    
    /**
     * Reports changes to a specific Config field
     */
    private static void reportChange(String fieldName, Object oldValue, Object newValue) {
        if (oldValue != null && !oldValue.equals(newValue)) {
            System.out.println("Changed " + fieldName + " from " + oldValue + " to " + newValue);
        }
    }
    
    /**
     * Reports changes to the BOUNDARY field
     */
    private static void reportBoundaryChanges() {
        double[][] newBoundary = Config.getBOUNDARY();
        double[][] oldBoundary = (double[][]) originalValues.get("BOUNDARY");
        
        boolean changed = false;
        for (int i = 0; i < oldBoundary.length; i++) {
            for (int j = 0; j < oldBoundary[i].length; j++) {
                if (oldBoundary[i][j] != newBoundary[i][j]) {
                    changed = true;
                    break;
                }
            }
            if (changed) break;
        }
        
        if (changed) {
            System.out.println("BOUNDARY values were changed.");
        }
    }
    
    /**
     * Reports changes to the points of interest
     */
    private static void reportPointsOfInterestChanges() {
        Map<String, Location> originalPoints = (Map<String, Location>) originalValues.get("pointsOfInterest");
        Map<String, Location> newPoints = Config.getAllPointsOfInterest();
        
        // Check for added or modified points
        for (Map.Entry<String, Location> entry : newPoints.entrySet()) {
            String name = entry.getKey();
            Location newLocation = entry.getValue();
            
            if (!originalPoints.containsKey(name)) {
                System.out.println("Added new point of interest: " + name + " at " + newLocation);
            } else {
                Location oldLocation = originalPoints.get(name);
                if (oldLocation.latitude != newLocation.latitude || oldLocation.longitude != newLocation.longitude) {
                    System.out.println("Modified point of interest: " + name + 
                                       " from " + oldLocation + " to " + newLocation);
                }
            }
        }
        
        // Check for removed points
        for (String name : originalPoints.keySet()) {
            if (!newPoints.containsKey(name)) {
                System.out.println("Removed point of interest: " + name);
            }
        }
    }
    
    /**
     * Applies boundary configuration from JSON array to Config.BOUNDARY
     */
    private static void applyBoundary(JSONArray boundaryArray) {
        if (boundaryArray.size() != 4) {
            throw new IllegalArgumentException("Boundary must have exactly 4 points");
        }
        
        double[][] boundary = new double[4][2];
        for (int i = 0; i < 4; i++) {
            JSONArray point = (JSONArray) boundaryArray.get(i);
            boundary[i][0] = ((Number) point.get(0)).doubleValue(); // Latitude
            boundary[i][1] = ((Number) point.get(1)).doubleValue(); // Longitude
        }
        
        Config.setBOUNDARY(boundary);
    }
    
    /**
     * Applies points of interest from JSON to Config
     */
    private static void applyPointsOfInterest(JSONObject pointsOfInterestObject) {
        Map<String, Location> points = new HashMap<>();
        
        for (Object key : pointsOfInterestObject.keySet()) {
            String name = (String) key;
            JSONObject pointObject = (JSONObject) pointsOfInterestObject.get(name);
            
            double lat = ((Number) pointObject.get("lat")).doubleValue();
            double lon = ((Number) pointObject.get("lon")).doubleValue();
            
            points.put(name, new Location(lat, lon, -1));
        }
        
        // Update all points of interest at once
        Config.setAllPointsOfInterest(points);
    }
    
    /**
     * Infers the geographic area based on coordinates or explicitly defined area in JSON
     */
    private static void inferGeographicArea(JSONObject config) {
        // First check if area is explicitly defined in the JSON
        if (config.containsKey("area")) {
            String area = (String) config.get("area");
            Config.setGeographicArea(area.toUpperCase());
            System.out.println("Setting geographic area from config: " + area);
            return;
        }
        
        // If not explicitly defined, try to infer from coordinates
        double minLat = ((Number) config.get("minLat")).doubleValue();
        double minLon = ((Number) config.get("minLon")).doubleValue();
        
        // Dublin, Ireland coordinates (approximate)
        if (minLat > 53.0 && minLat < 54.0 && minLon > -7.0 && minLon < -6.0) {
            Config.setGeographicArea("DUBLIN");
            System.out.println("Inferred geographic area: DUBLIN");
        }
        // Melbourne, Australia coordinates (approximate)
        else if (minLat < -37.0 && minLat > -38.0 && minLon > 144.0 && minLon < 145.0) {
            Config.setGeographicArea("MELBOURNE");
            System.out.println("Inferred geographic area: MELBOURNE");
        }
        // If coordinates don't match known areas, keep the default
        else {
            System.out.println("Could not infer geographic area from coordinates. Using: " + Config.getGeographicArea());
        }
    }
    
    /**
     * Verifies that all required location configuration is set and consistent
     */
    private static void verifyConfigConsistency() {
        // Check for valid min/max values
        if (Config.getMinLat() >= Config.getMaxLat()) {
            System.err.println("Warning: minLat must be less than maxLat");
        }
        if (Config.getMinLon() >= Config.getMaxLon()) {
            System.err.println("Warning: minLon must be less than maxLon");
        }
        
        // Check boundary points are within min/max bounds
        for (double[] point : Config.getBOUNDARY()) {
            if (point[0] < Config.getMinLat() || point[0] > Config.getMaxLat() ||
                point[1] < Config.getMinLon() || point[1] > Config.getMaxLon()) {
                System.err.println("Warning: Boundary point [" + point[0] + ", " + point[1] + 
                                  "] is outside min/max lat/lon bounds");
            }
        }
        
        // Check points of interest are within bounds
        for (Map.Entry<String, Location> entry : Config.getAllPointsOfInterest().entrySet()) {
            String name = entry.getKey();
            Location point = entry.getValue();
            
            if (point.latitude < Config.getMinLat() || point.latitude > Config.getMaxLat() ||
                point.longitude < Config.getMinLon() || point.longitude > Config.getMaxLon()) {
                System.err.println("Warning: Point of interest '" + name + "' is outside min/max lat/lon bounds");
            }
        }
    }
} 
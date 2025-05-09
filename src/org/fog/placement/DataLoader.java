package org.fog.placement;

import org.fog.mobilitydata.Location;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * The DataLoader class is responsible for loading resource and user location data from CSV files.
 * It extracts this functionality from the original DataParser class but focuses only on the 
 * data loading aspects without maintaining state.
 */
public class DataLoader {
    // Level constants
    private final Map<String, Integer> levelID = new HashMap<>();
    private final Map<Integer, ArrayList<String>> levelwiseResources = new HashMap<>();
    
    /**
     * Initialize the DataLoader with default level configurations
     */
    public DataLoader() {
        // Initialize default level configurations
        levelID.put("LevelsNum", 3);
        levelID.put("Cloud", 0);
        levelID.put("Gateway", 1);
        levelID.put("User", 2);
    }
    
    /**
     * Loads resource data (like fog nodes, gateways, etc.) from CSV file
     * 
     * @param filename the filename for resource locations
     * @param numberOfResource the number of edge nodes to load
     * @return a map of resource IDs to their locations
     * @throws IOException if there's an error reading the file
     */
    public Map<String, Location> loadResourceLocations(String filename, int numberOfResource) throws IOException {
        Map<String, Location> resourceLocationData = new HashMap<>();
        Map<String, Integer> resourceToLevel = new HashMap<>();
        
        BufferedReader csvReader = new BufferedReader(new FileReader(filename));
        String row;
        
        // Initialize level arrays
        ArrayList<String>[] resourcesOnLevels = new ArrayList[levelID.get("LevelsNum")];
        for (int i = 0; i < levelID.get("LevelsNum"); i++) {
            resourcesOnLevels[i] = new ArrayList<>();
        }
        
        int edgesPut = 0;
        while ((row = csvReader.readLine()) != null && edgesPut < numberOfResource) {
            String[] data = row.split(",");
            if (data.length > 6 && data[6].equals("VIC")) {
                Location location = new Location(
                    Double.parseDouble(data[1]), 
                    Double.parseDouble(data[2]), 
                    Integer.parseInt(data[3])
                );
                
                String resourceId = "res_" + data[0];
                int level = Integer.parseInt(data[4]);
                
                resourcesOnLevels[level].add(resourceId);
                resourceToLevel.put(resourceId, level);
                resourceLocationData.put(resourceId, location);
                
                if (level == levelID.get("Gateway")) {
                    edgesPut++;
                }
            }
        }
        
        // Store levelwise resources
        for (int i = 0; i < levelID.get("LevelsNum"); i++) {
            levelwiseResources.put(i, resourcesOnLevels[i]);
        }
        
        csvReader.close();
        return resourceLocationData;
    }
    
    /**
     * Loads user location data from CSV file
     * 
     * @param fileName the filename for user locations
     * @param numberOfUsers the number of users to load
     * @return a map of user IDs to their locations
     * @throws IOException if there's an error reading the file
     */
    public Map<Integer, Location> loadUserLocations(String fileName, int numberOfUsers) throws IOException {
        Map<Integer, Location> userLocations = new HashMap<>();
        
        if (numberOfUsers > 200) {
            throw new NullPointerException("Number of users cannot be greater than 196");
        }
        
        BufferedReader csvReader = new BufferedReader(new FileReader(fileName));
        System.out.println("Reading user positions from: " + fileName);
        
        int userIndex = 1;
        String row;
        
        while ((row = csvReader.readLine()) != null && userIndex <= numberOfUsers) {
            String[] data = row.split(",");
            Location location = new Location(
                Double.parseDouble(data[0]), 
                Double.parseDouble(data[1]), 
                -1  // Default block value
            );
            
            userLocations.put(userIndex, location);
            userIndex++;
        }
        
        csvReader.close();
        return userLocations;
    }
    
    /**
     * Loads initial user locations from CSV file. This is an alias for loadUserLocations
     * to maintain compatibility with code that uses this method name.
     * 
     * @param fileName the filename for user locations
     * @param numberOfUsers the number of users to load
     * @return a map of user IDs to their locations
     * @throws IOException if there's an error reading the file
     */
    public Map<Integer, Location> loadInitialUserLocations(String fileName, int numberOfUsers) throws IOException {
        return loadUserLocations(fileName, numberOfUsers);
    }
    
    /**
     * Gets the level ID mapping
     * 
     * @return the level ID mapping
     */
    public Map<String, Integer> getLevelID() {
        return levelID;
    }
    
    /**
     * Gets the levelwise resources mapping
     * 
     * @return the levelwise resources mapping
     */
    public Map<Integer, ArrayList<String>> getLevelwiseResources() {
        return levelwiseResources;
    }
} 
package org.fog.mobilitydata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class OfflineDataParser extends DataParser {

//    public Map<String, Location> immobileUserLocationData = new HashMap<String, Location>();

    public OfflineDataParser() {
        levelID.put("LevelsNum", 3);
        levelID.put("Cloud", 0);
        // Todo Simon says no Proxy servers
//        levelID.put("Proxy", Integer.parseInt(props.getProperty("Proxy")));
        levelID.put("Gateway", 1);
        levelID.put("User", 2);
    }

    @Override
    public void parseUserData(Map<Integer, Integer> userMobilityPattern, String fileName) throws IOException {
        // All users are IMMOBILE. The file to parse is very much like the resources file

        BufferedReader csvReader = new BufferedReader(new FileReader(fileName));
        System.out.println("The positions of immobile users is extracted from: " + fileName);

        ArrayList<String> resourcesOnLevel2 = new ArrayList<String>();
        String row;
        int i = 1;
        while ((row = csvReader.readLine()) != null) {
            String[] data = row.split(",");
            //System.out.println(row);
            Location rl = new Location(Double.parseDouble(data[0]), Double.parseDouble(data[1]), References.NOT_SET);

            resourcesOnLevel2.add("usr_" + i);
            Map<Double, Location> singleLocationMap = new HashMap<>();
            singleLocationMap.put(References.INIT_TIME, rl);
            usersLocation.put("usr_" + i, singleLocationMap);
            resourceAndUserToLevel.put("usr_" + i, levelID.get("User"));
            i++;
        }

        csvReader.close();
        levelwiseResources.put(2, resourcesOnLevel2);

    }

    @Override
    public void parseResourceData(String filename) throws NumberFormatException, IOException {

        int numOfLevels = levelID.get("LevelsNum");
        ArrayList<String>[] resouresOnLevels = new ArrayList[numOfLevels];
        for (int i = 0; i < numOfLevels; i++)
            resouresOnLevels[i] = new ArrayList<String>();


        BufferedReader csvReader = new BufferedReader(new FileReader(filename));
        String row;
        while ((row = csvReader.readLine()) != null) {
            String[] data = row.split(",");
            //System.out.println(row);
            if (data[6].equals("VIC")) {
                //System.out.println(row);
                Location rl = new Location(Double.parseDouble(data[1]), Double.parseDouble(data[2]), Integer.parseInt(data[3]));
                resouresOnLevels[Integer.parseInt(data[4])].add("res_" + data[0]);
                resourceAndUserToLevel.put("res_" + data[0], Integer.parseInt(data[4]));
                resourceLocationData.put("res_" + data[0], rl);
            }
        }

        for (int i = 0; i < numOfLevels; i++) {
            levelwiseResources.put(i, resouresOnLevels[i]);
        }
        csvReader.close();
    }

    @Override
    public void parseResourceData() throws NumberFormatException, IOException {
        parseResourceData("./dataset/edgeResources-melbCBD_OfflinePOC.csv");
    }
}
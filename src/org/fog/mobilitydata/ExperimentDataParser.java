package org.fog.mobilitydata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ExperimentDataParser extends DataParser {

//    public Map<String, Location> immobileUserLocationData = new HashMap<String, Location>();

    public ExperimentDataParser() {
        levelID.put("LevelsNum", 3);
        levelID.put("Cloud", 0);
        // TODO Uncomment if there are Proxy servers
//        levelID.put("Proxy", Integer.parseInt(props.getProperty("Proxy")));
        levelID.put("Gateway", 1);
        levelID.put("User", 2);
    }

    public void parseUserData(Map<Integer, Integer> userMobilityPattern, String fileName, int numberOfUser) throws IOException {
        // Parses STARTING locations of users.
        // The file to parse is very much like the resources file

        int MAX_NUMBER_OF_USERS = 196;
        // Math.floor is implicit
        int stepsize = MAX_NUMBER_OF_USERS / numberOfUser;
        BufferedReader csvReader = new BufferedReader(new FileReader(fileName));
        System.out.println("The positions of immobile users is extracted from: " + fileName);

        ArrayList<String> resourcesOnLevel2 = new ArrayList<String>();
        String row;
        int i = 1;
        int rowToRead = stepsize;
        int currentRow = 1;  // Track the current row number being read

        while ((row = csvReader.readLine()) != null && i <= numberOfUser) {
            if (currentRow == rowToRead) {
                String[] data = row.split(",");
                //System.out.println(row);
                Location rl = new Location(Double.parseDouble(data[0]), Double.parseDouble(data[1]), References.NOT_SET);

                resourcesOnLevel2.add("usr_" + i);
                Map<Double, Location> startLocationMap = new HashMap<>();
                startLocationMap.put(References.INIT_TIME, rl);
                usersLocation.put("usr_" + i, startLocationMap);
                resourceAndUserToLevel.put("usr_" + i, levelID.get("User"));

                i++;
                rowToRead += stepsize;
            }
            currentRow++;  // Increment the row counter regardless of whether the row was used
        }


        csvReader.close();
        levelwiseResources.put(2, resourcesOnLevel2);

    }

    public void parseResourceData(String filename, int numberOfEdge) throws NumberFormatException, IOException {

        int numOfLevels = levelID.get("LevelsNum");
        ArrayList<String>[] resouresOnLevels = new ArrayList[numOfLevels];
        for (int i = 0; i < numOfLevels; i++)
            resouresOnLevels[i] = new ArrayList<String>();


        BufferedReader csvReader = new BufferedReader(new FileReader(filename));
        String row;

        // We read the first numberOfEdge entries from the file (that have level==levelID.get("Gateway"))
        // Ensures that we only have numberOfEdge edge servers (gateway is edge server)
        int edgesPut = 0;
        while ((row = csvReader.readLine()) != null && edgesPut < numberOfEdge) {
            String[] data = row.split(",");
            //System.out.println(row);
            if (data[6].equals("VIC")) {
                //System.out.println(row);
                Location rl = new Location(Double.parseDouble(data[1]), Double.parseDouble(data[2]), Integer.parseInt(data[3]));
                resouresOnLevels[Integer.parseInt(data[4])].add("res_" + data[0]);
                resourceAndUserToLevel.put("res_" + data[0], Integer.parseInt(data[4]));
                resourceLocationData.put("res_" + data[0], rl);

                if (Integer.parseInt(data[4]) == levelID.get("Gateway")) {
                    edgesPut++;
                }
            }
//            if (edgesPut == numberOfEdge) {
//                break;
//            }
        }

        for (int i = 0; i < numOfLevels; i++) {
            levelwiseResources.put(i, resouresOnLevels[i]);
        }
        csvReader.close();
    }

    public void parseResourceData(int numberOfEdge) throws NumberFormatException, IOException {
        parseResourceData("./dataset/edgeResources-melbCBD_Experiments.csv", numberOfEdge);
    }
}
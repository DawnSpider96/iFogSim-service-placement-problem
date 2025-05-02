package org.fog.test.unit;
import java.io.FileWriter;
import java.io.IOException;
import org.fog.mobilitydata.Location;
import org.fog.utils.Config;

public class randomLocationInArea {
    private static double minLat = Config.getMinLat();
    private static double maxLat = Config.getMaxLat();
    private static double minLon = Config.getMinLon();
    private static double maxLon = Config.getMaxLon();


    public static void main(String[] args) {
        int numPoints = 100;
        String filename = "100_random_locations.csv";
        

        try (FileWriter writer = new FileWriter(filename)) {
            writer.append("latitude,longitude\n");
            // Bounding box corners for reference
            writer.append(minLat + "," + minLon + "\n");
            writer.append(minLat + "," + maxLon + "\n");
            writer.append(maxLat + "," + maxLon + "\n");
            writer.append(maxLat + "," + minLon + "\n");

            for (int i = 0; i < numPoints; i++) {
                Location loc = Location.getRandomLocation();
                writer.append(loc.latitude + "," + loc.longitude + "\n");
            }

            System.out.println("CSV generated: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

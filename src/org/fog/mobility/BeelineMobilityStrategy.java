package org.fog.mobility;
import org.fog.mobilitydata.Location;
import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;


public class BeelineMobilityStrategy extends MobilityStrategy {

    /**
     * Creates a new path for the device to follow.
     * 
     * @param attractionPoint the point the device is moving towards
     * @param speed the speed of the device, m/s
     * @param currentLocation current location of the device
     * @return the path the device will follow. Length 1.
     */
    @Override
    public WayPointPath makePath(Attractor attractionPoint, double speed, Location currentLocation) {
        WayPointPath path = new WayPointPath();

        Random rand = new Random();
        // Random value between 5 and 10 seconds
        double time = rand.nextDouble() * 5 + 5;

        double distance = speed * time;
        Location loc = currentLocation.movedTowards(attractionPoint.getAttractionPoint(), distance);

        WayPoint w = new WayPoint(loc, CloudSim.clock() + time);
        path.addWayPoint(w);
        return path;
    }
}

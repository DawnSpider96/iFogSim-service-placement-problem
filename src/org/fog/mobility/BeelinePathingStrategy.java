package org.fog.mobility;
import org.cloudbus.cloudsim.Consts;
import org.fog.mobilitydata.Location;
import java.util.Random;
import org.cloudbus.cloudsim.core.CloudSim;

public class BeelinePathingStrategy extends AbstractPathingStrategy {

    public BeelinePathingStrategy() {
        super(); // Default seed is current time
    }
    
    public BeelinePathingStrategy(long seed) {
        super(seed);
    }

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

        double time = rand.nextDouble() * 5 + 5;

        double distance = speed * time * Consts.METERS_TO_KM;
        Location loc = currentLocation.movedTowards(attractionPoint.getAttractionPoint(), distance);

        WayPoint w = new WayPoint(loc, CloudSim.clock() + time);
        path.addWayPoint(w);
        return path;
    }
}

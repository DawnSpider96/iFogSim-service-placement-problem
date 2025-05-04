package org.fog.mobility;

import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.Location;

import java.util.Random;

/**
 * A pathing strategy that simulates realistic human walking behavior.
 * Generates a path with small random deviations from a straight line path,
 * creating a more natural walking pattern than a simple beeline.
 */
public class JitterBugPathingStrategy extends AbstractPathingStrategy {

    // Constants for tuning the behavior
    private static final double WAYPOINT_DISTANCE = 150.0; // meters between waypoints
    private static final double MAX_DEVIATION = 5.0; // maximum deviation in meters from straight line
    private static final double MAX_SPEED_VARIATION = 0.2; // maximum speed variation (20%)

    /**
     * Creates a JitterBugPathingStrategy with a default random seed.
     */
    public JitterBugPathingStrategy() {
        super();
    }

    /**
     * Creates a JitterBugPathingStrategy with a specified random seed.
     *
     * @param seed the random seed for reproducible path generation
     */
    public JitterBugPathingStrategy(long seed) {
        super(seed);
    }

    @Override
    public WayPointPath makePath(Attractor attractionPoint, double speed, Location currentLocation) {
        WayPointPath path = new WayPointPath();
        Location destination = attractionPoint.getAttractionPoint();

        double directDistanceKm = currentLocation.calculateDistance(destination);
        double directDistanceM = directDistanceKm * Consts.KM_TO_METERS;

        if (directDistanceM < WAYPOINT_DISTANCE) {
            double time = directDistanceM / speed;
            path.addWayPoint(new WayPoint(destination, CloudSim.clock() + time));
            return path;
        }

        int numWaypoints = (int) Math.ceil(directDistanceM / WAYPOINT_DISTANCE);

        Location currentPoint = currentLocation;
        double currentTime = CloudSim.clock();

        for (int i = 1; i <= numWaypoints; i++) {
            if (i == numWaypoints) {
                double segmentDistanceM = currentPoint.calculateDistance(destination) * Consts.KM_TO_METERS;
                double speedVariation = 1.0 + (rand.nextDouble() * 2 - 1) * MAX_SPEED_VARIATION;
                double adjustedSpeed = speed * speedVariation;
                double segmentTime = segmentDistanceM / adjustedSpeed;

                path.addWayPoint(new WayPoint(destination, currentTime + segmentTime));
                break;
            }

            double progress = (double) i / numWaypoints;

            double segmentDistanceKm = (directDistanceM / numWaypoints) / Consts.KM_TO_METERS;
            double distanceFromStart = progress * directDistanceKm;

            Location idealPoint = currentLocation.movedTowards(destination, distanceFromStart);

            double pathBearing = currentPoint.getBearing(destination);

            double perpBearing = (pathBearing + (rand.nextBoolean() ? 90 : -90)) % 360;

            double edgeFactor = Math.min(progress, 1.0 - progress) * 4;
            double deviationDistanceM = rand.nextDouble() * MAX_DEVIATION * edgeFactor;
            double deviationDistanceKm = deviationDistanceM / Consts.KM_TO_METERS;

            Location pointOnPath = currentLocation.movedTowards(destination, distanceFromStart);

            Location deviatedPoint = pointOnPath;
            if (deviationDistanceKm > 0.0001) {
                double tempLat = pointOnPath.getLatitude() +
                        Math.sin(Math.toRadians(perpBearing)) * 0.01;
                double tempLon = pointOnPath.getLongitude() +
                        Math.cos(Math.toRadians(perpBearing)) * 0.01;
                Location tempTarget = new Location(tempLat, tempLon, -1);

                deviatedPoint = pointOnPath.movedTowards(tempTarget, deviationDistanceKm);
            }

            double segmentDistanceM = currentPoint.calculateDistance(deviatedPoint) * Consts.KM_TO_METERS;
            double speedVariation = 1.0 + (rand.nextDouble() * 2 - 1) * MAX_SPEED_VARIATION;
            double adjustedSpeed = speed * speedVariation;
            double segmentTime = segmentDistanceM / adjustedSpeed;

            currentTime += segmentTime;
            path.addWayPoint(new WayPoint(deviatedPoint, currentTime));
            currentPoint = deviatedPoint;
        }

        return path;
    }
}
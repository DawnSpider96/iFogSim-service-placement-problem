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
        Random random = new Random(seed);

        // Get basic information about the path
        double directDistanceKm = currentLocation.calculateDistance(destination);
        double directDistanceM = directDistanceKm * Consts.KM_TO_METERS;

        // If very close, just go directly there
        if (directDistanceM < WAYPOINT_DISTANCE) {
            double time = directDistanceM / speed;
            path.addWayPoint(new WayPoint(destination, CloudSim.clock() + time));
            return path;
        }

        // Calculate number of waypoints needed
        int numWaypoints = (int) Math.ceil(directDistanceM / WAYPOINT_DISTANCE);

        // Set up waypoint generation
        Location currentPoint = currentLocation;
        double currentTime = CloudSim.clock();

        // First calculate ideal intermediate points along direct path
        for (int i = 1; i <= numWaypoints; i++) {
            // Last waypoint is always the exact destination
            if (i == numWaypoints) {
                double segmentDistanceM = currentPoint.calculateDistance(destination) * Consts.KM_TO_METERS;
                double speedVariation = 1.0 + (random.nextDouble() * 2 - 1) * MAX_SPEED_VARIATION;
                double adjustedSpeed = speed * speedVariation;
                double segmentTime = segmentDistanceM / adjustedSpeed;

                path.addWayPoint(new WayPoint(destination, currentTime + segmentTime));
                break;
            }

            // Calculate progress along path (0.0 to 1.0)
            double progress = (double) i / numWaypoints;

            // Find point along direct path
            double segmentDistanceKm = (directDistanceM / numWaypoints) / Consts.KM_TO_METERS;
            double distanceFromStart = progress * directDistanceKm;

            // Calculate ideal point along direct path
            Location idealPoint = currentLocation.movedTowards(destination, distanceFromStart);

            // Now add jitter perpendicular to the path
            // First get bearing of path
            double pathBearing = currentPoint.getBearing(destination);

            // Calculate perpendicular direction (either left or right)
            double perpBearing = (pathBearing + (random.nextBoolean() ? 90 : -90)) % 360;

            // Calculate random deviation distance (smaller near start and end)
            double edgeFactor = Math.min(progress, 1.0 - progress) * 4; // increases toward middle
            double deviationDistanceM = random.nextDouble() * MAX_DEVIATION * edgeFactor;
            double deviationDistanceKm = deviationDistanceM / Consts.KM_TO_METERS;

            // Create point with deviation by:
            // 1. Moving along direct path
            Location pointOnPath = currentLocation.movedTowards(destination, distanceFromStart);

            // 2. Then adding slight perpendicular movement for jitter
            // We'll need to approximate this using small steps of movedTowards
            // using the perpendicular bearing
            Location deviatedPoint = pointOnPath;
            if (deviationDistanceKm > 0.0001) { // Only if deviation is significant
                // Create a temporary target point in the perpendicular direction
                // far enough away to ensure we get full deviation distance
                double tempLat = pointOnPath.getLatitude() +
                        Math.sin(Math.toRadians(perpBearing)) * 0.01; // arbitrary distance
                double tempLon = pointOnPath.getLongitude() +
                        Math.cos(Math.toRadians(perpBearing)) * 0.01; // arbitrary distance
                Location tempTarget = new Location(tempLat, tempLon, -1);

                // Move towards this artificial point by our deviation distance
                deviatedPoint = pointOnPath.movedTowards(tempTarget, deviationDistanceKm);
            }

            // Calculate time to reach this waypoint with slight speed variation
            double segmentDistanceM = currentPoint.calculateDistance(deviatedPoint) * Consts.KM_TO_METERS;
            double speedVariation = 1.0 + (random.nextDouble() * 2 - 1) * MAX_SPEED_VARIATION;
            double adjustedSpeed = speed * speedVariation;
            double segmentTime = segmentDistanceM / adjustedSpeed;

            // Add the waypoint
            currentTime += segmentTime;
            path.addWayPoint(new WayPoint(deviatedPoint, currentTime));
            currentPoint = deviatedPoint;
        }

        return path;
    }
}
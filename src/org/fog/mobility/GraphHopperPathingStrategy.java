package org.fog.mobility;

import com.graphhopper.*;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.Location;
import org.cloudbus.cloudsim.Consts;
import java.util.*;

import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;

public class GraphHopperPathingStrategy implements PathingStrategy {
    private GraphHopper hopper;

    // Configuration parameters
    private String osmFileLocation = "/home/dawn/repos/iFogSim-placement/melbourne.osm.pbf";
    private String graphFolderFiles = "/home/dawn/repos/iFogSim-placement/output/graphhopper";
    private String movementType = "car";  // Default vehicle profile name
    private String navigationalType = "custom";  // Default weighting. Used to be "fastest"
    private String blockedAreas = null;
    private boolean allowAlternativeRoutes = false;
    private double probabilityForAlternativeRoute = 0.0;
    private Random random = new Random();

//    private boolean uniqueFolders;

    // Constants
    private static final double MIN_WAYPOINT_DISTANCE = 20.0;  // meters
    private static final double MAX_DISTANCE_THRESHOLD = 1200.0;  // kilometers threshold

    public GraphHopperPathingStrategy() {}

    public GraphHopperPathingStrategy(String osmFile, String graphFolder, String movement) {
        this.osmFileLocation = osmFile;
        this.graphFolderFiles = graphFolder;
        this.movementType = movement;
    }

//    private void init() {
//        File graphDir = new File(graphFolderFiles);
//        if (!graphDir.exists()) {
//            boolean dirCreated = graphDir.mkdirs();
//            System.out.println("Directory created: " + dirCreated);
//        }
//        try {
//            hopper = new GraphHopperOSM().forServer();
//            hopper.setDataReaderFile(osmFileLocation);
////            hopper.setGraphHopperLocation(graphFolderFiles);
//            hopper.setGraphHopperLocation(graphFolderFiles + "/" + osmFileLocation.hashCode() + movementType);
//            hopper.setEncodingManager(EncodingManager.create(movementType));
//
//            List<Profile> profiles = new ArrayList<>();
//            profiles.add(new Profile("car").setVehicle("car").setWeighting("fastest"));
//            hopper.setProfiles(profiles);
//
//            List<CHProfile> chProfiles = new ArrayList<>();
//            chProfiles.add(new CHProfile("car"));
//            hopper.getCHPreparationHandler().setCHProfiles(chProfiles);
//
//            hopper.importOrLoad();
//        } catch (Exception e) {
//            e.printStackTrace(); // Check for silent failures
//        }
//    }
    private void init() {
        if (hopper != null) return;

        CustomModel fastestModel = new CustomModel();
        // Distance influence 0.1 is "fastest", distance influence 100.0 is "shortest"
        fastestModel.setDistanceInfluence(0.1);

        System.out.println("CustomModel used: " + fastestModel);

        hopper = new GraphHopper();
        hopper.setGraphHopperLocation(graphFolderFiles);
        hopper.clean();
        hopper.init(
            new GraphHopperConfig().
                    putObject("datareader.file", osmFileLocation).
                    putObject("graph.location", graphFolderFiles).
                    putObject("prepare.min_network_size", 200). // skip this unless you know what you are doing, 200 is the default anyway
                    putObject("import.osm.ignored_highways", ""). // if you are only using car you can ignore paths, tracks etc. here, take a look at the documentation in `config-example.yml`
//                    putObject("graph.vehicles", "car").
                    // todo Removed, not compatible with v8.0. These give more precise points (following road structure)
//                    putObject("graph.encoded_values", "road_class, road_class_link,road_environment,max_speed,surface").
                    putObject("graph.encoded_values", "").
                    setProfiles(Collections.singletonList(
                    new Profile(movementType).setVehicle(movementType).setWeighting(navigationalType).setTurnCosts(false).setCustomModel(fastestModel)
            )));

        if (!((blockedAreas != null && !blockedAreas.isEmpty()) || allowAlternativeRoutes)) {
            List<CHProfile> l = new ArrayList<>();
            l.add(new CHProfile(movementType));
            hopper.getCHPreparationHandler().setCHProfiles(l);
        }

        try {
            hopper.importOrLoad();
        } catch (Exception e) {
            System.err.println("[GraphHopperPathingStrategy] GraphHopper graph file must be re-imported!");
            hopper.clean();
            hopper.importOrLoad();
        }
    }


    // Speed in m/s
    @Override
    public WayPointPath makePath(Attractor attractionPoint, double speed, Location currentLocation) {
        if (hopper == null) init();
        WayPointPath path;
        Location destination = attractionPoint.getAttractionPoint();
        double speedKmps = speed * Consts.METERS_TO_KM;

        try {
            GHRequest req = new GHRequest(
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    destination.getLatitude(),
                    destination.getLongitude())
                    .setProfile(movementType)
                    .setLocale(Locale.ENGLISH);

            // todo If we want GraphHopper to NOT remove points from output, uncomment.
            //  eg If we use a visual interface, we don't want to see ambulances driving through buildings.
            //  But for our current arrival time estimation purposes, simplification is fine.
            // req.getHints().put("simplify_response", false);

            // Simon says put is deprecated because PMAP should be immutable ("final" config),
            //  But this code probably won't run.
            if (blockedAreas != null && !blockedAreas.isEmpty()) {
                req.getHints().put("block_area", blockedAreas);
            }
            if (allowAlternativeRoutes) {
                req.setAlgorithm(Parameters.Algorithms.ALT_ROUTE);
                req.getHints().put(Parameters.Algorithms.AltRoute.MAX_WEIGHT, "2.0");
                req.getHints().put(Parameters.Algorithms.AltRoute.MAX_PATHS, "5");
            }

            GHResponse rsp = hopper.route(req);
            if (rsp.hasErrors()) {
                System.err.println("Routing error: " + rsp.getErrors());
                return createFallbackPath(currentLocation, destination, speedKmps);
            }

            ResponsePath route = rsp.getBest();
            if (allowAlternativeRoutes && rsp.getAll().size() > 1
                    && random.nextDouble() <= probabilityForAlternativeRoute) {
                int altIdx = random.nextInt(rsp.getAll().size() - 1) + 1;
                route = rsp.getAll().get(altIdx);
            }

            // NOTE: Both start and end point is snapped to nearest road by graphhopper.
            PointList points = route.getPoints();
            double totalDistanceKm = route.getDistance() * Consts.METERS_TO_KM;
            path = createWaypointsFromPointList(points, totalDistanceKm, speedKmps, currentLocation);
        } catch (Exception e) {
            System.err.println("Error during path creation: " + e.getMessage());
            path = createFallbackPath(currentLocation, attractionPoint.getAttractionPoint(), speedKmps);
        }
        return path;
    }

    /**
     * Converts a GraphHopper-generated {@link PointList} into a {@link WayPointPath} for simulation.
     * <p>
     * Assumes the entity moves at constant speed along straight lines between consecutive points in the {@code PointList}.
     * Linearly interpolates timestamps for each waypoint.
     * Does NOT consider turn penalties, actual road curvature, or variable speed limits.
     * <p>
     * {@code currentLocation} is used as the starting point for the first segment.
     * Estimates the time for each segment as {@code segmentDistance / speed}, where
     * {@code segmentDistance} is computed using the haversine formula between two consecutive points.
     * <p>
     * NOTE: Filters out segments shorter than a predefined threshold ({@code MIN_WAYPOINT_DISTANCE}),
     * except for the final point, which is always added to ensure the destination is reached.
     * <p>
     * This function assumes the total path length is reasonably close to the actual route length reported by GraphHopper,
     * but may slightly overshoot due to haversine approximation.
     *
     * @param pointList        the {@link PointList} from a GraphHopper route representing the geometry of the path
     * @param totalDistanceKm  the full distance of the route, as reported by {@code response.getDistance()} (in kilometers)
     * @param speedKmps        the constant speed of the entity (in kilometers per second)
     * @param currentLocation  the starting location of the entity at the beginning of the path
     * @return a {@link WayPointPath} containing timestamped waypoints based on straight-line interpolation
     */
    private WayPointPath createWaypointsFromPointList(PointList pointList, double totalDistanceKm,
                                                      double speedKmps, Location currentLocation) {
        WayPointPath path = new WayPointPath();
        if (pointList.isEmpty()) return path;

        double currentTime = CloudSim.clock();
        double distanceCoveredKm = 0;
        Location prevLoc = currentLocation;

        for (int i = 1; i < pointList.size(); i++) {
            double lat = pointList.getLat(i);
            double lon = pointList.getLon(i);
            Location wpLoc = new Location(lat, lon, -1);

            double segDistKm = prevLoc.calculateDistance(wpLoc);
            double segTime = segDistKm / speedKmps;
            distanceCoveredKm += segDistKm;
            currentTime += segTime;

            // MUST add the last point (because that is destination)
            if (i == pointList.size() - 1 || segDistKm * Consts.KM_TO_METERS >= MIN_WAYPOINT_DISTANCE) {
                path.addWayPoint(new WayPoint(wpLoc, currentTime));
                prevLoc = wpLoc;
            }
//            if (distanceCoveredKm >= totalDistanceKm) break;
        }
        System.out.printf("GraphHopper distance: %.2f km, computed distance: %.2f km\n",
                totalDistanceKm, distanceCoveredKm);
        return path;
    }

    private WayPointPath createFallbackPath(Location currentLocation, Location destination, double speedKmps) {
        WayPointPath path = new WayPointPath();
        double distKm = currentLocation.calculateDistance(destination);
        double time = distKm / speedKmps;

        if (distKm * 1000 > MAX_DISTANCE_THRESHOLD) {
            int numWaypoints = (int) (distKm / (MAX_DISTANCE_THRESHOLD * Consts.METERS_TO_KM));
            double stepTime = time / (numWaypoints + 1);
            for (int i = 1; i <= numWaypoints; i++) {
                double frac = (double) i / (numWaypoints + 1);
                Location mid = currentLocation.movedTowards(destination, distKm * frac);
                path.addWayPoint(new WayPoint(mid, CloudSim.clock() + stepTime * i));
            }
        }
        path.addWayPoint(new WayPoint(destination, CloudSim.clock() + time));
        return path;
    }

    // Getters and setters omitted for brevity
    // close() remains unchanged
}

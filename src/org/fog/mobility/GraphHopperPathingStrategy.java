package org.fog.mobility;

import com.graphhopper.*;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.Location;
import org.cloudbus.cloudsim.Consts;
import org.fog.utils.Config;
import java.io.File;
import java.util.*;

import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;

public class GraphHopperPathingStrategy extends AbstractPathingStrategy {
    private GraphHopper hopper;

    // Base path for resource files
    private static final String BASE_PATH = "/home/dawn/repos/iFogSim-placement";
    
    // Map of area-specific OSM files
    private static final Map<String, String> AREA_OSM_FILES = new HashMap<>();
    static {
        AREA_OSM_FILES.put("MELBOURNE", BASE_PATH + "/melbourne.osm.pbf");
        AREA_OSM_FILES.put("DUBLIN", BASE_PATH + "/dublin.osm.pbf");
        // Add more areas as needed
    }

    // Configuration parameters
    private String osmFileLocation = AREA_OSM_FILES.get("MELBOURNE"); // Default
    private String graphFolderFiles = BASE_PATH + "/output/graphhopper_melbourne"; // Default
    private String movementType = "car";  // Default vehicle profile name
    private String navigationalType = "custom";  // Default weighting. Used to be "fastest"
    private String blockedAreas = null;
    private boolean allowAlternativeRoutes = false;
    private double probabilityForAlternativeRoute = 0.0;

    // Constants
    private static final double MIN_WAYPOINT_DISTANCE = 5.0;  // meters, reduced from 20.0 for more detailed paths
    private static final double MAX_DISTANCE_THRESHOLD = 1200.0;  // kilometers threshold

    public GraphHopperPathingStrategy() {
        super(); // Initialize with default seed
        updateAreaSettings();
    }

    public GraphHopperPathingStrategy(String osmFile, String graphFolder, String movement) {
        super(); // Initialize with default seed
        this.osmFileLocation = osmFile;
        this.graphFolderFiles = graphFolder;
        this.movementType = movement;
    }
    
    public GraphHopperPathingStrategy(long seed) {
        super(seed);
        updateAreaSettings();
    }

    public GraphHopperPathingStrategy(long seed, String movement) {
        super(seed);
        this.movementType = movement;
        updateAreaSettings();
    }
    
    public GraphHopperPathingStrategy(String osmFile, String graphFolder, String movement, long seed) {
        super(seed);
        this.osmFileLocation = osmFile;
        this.graphFolderFiles = graphFolder;
        this.movementType = movement;
    }
    
    /**
     * Updates OSM file and graph folder based on the current geographic area in Config
     */
    private void updateAreaSettings() {
        String area = Config.getGeographicArea();
        if (area == null) {
            System.err.println("WARNING: Geographic area is null, using MELBOURNE as default");
            area = "MELBOURNE";
        }
        
        // Set OSM file based on area
        if (AREA_OSM_FILES.containsKey(area)) {
            osmFileLocation = AREA_OSM_FILES.get(area);
        } else {
            System.err.println("WARNING: No OSM file defined for area: " + area + ", using MELBOURNE");
            osmFileLocation = AREA_OSM_FILES.get("MELBOURNE");
        }
        
        // Set graph folder based on area
        graphFolderFiles = BASE_PATH + "/output/graphhopper_" + area.toLowerCase();
        
        System.out.println("GraphHopper settings updated for area: " + area);
        System.out.println("OSM file: " + osmFileLocation);
        System.out.println("Graph folder: " + graphFolderFiles);
        
        // Force reinitialization
        reset();
    }
    
    /**
     * Resets the GraphHopper instance, forcing reinitialization on next use.
     * Call this method between simulations or when changing geographic areas.
     */
    public void reset() {
        if (hopper != null) {
            try {
                hopper.close();
            } catch (Exception e) {
                System.err.println("Error closing GraphHopper: " + e.getMessage());
            }
            hopper = null;
        }
        System.out.println("GraphHopper instance reset");
    }

    private void init() {
        if (hopper != null) return;
        
        // Create graph folder if it doesn't exist
        File graphFolder = new File(graphFolderFiles);
        if (!graphFolder.exists()) {
            if (graphFolder.mkdirs()) {
                System.out.println("Created graph folder: " + graphFolderFiles);
            } else {
                System.err.println("Failed to create graph folder: " + graphFolderFiles);
            }
        }

        CustomModel fastestModel = new CustomModel();
        // Distance influence 0.1 is "fastest", distance influence 100.0 is "shortest"
        fastestModel.setDistanceInfluence(0.1);

        System.out.println("CustomModel used: " + fastestModel);
        System.out.println("Initializing GraphHopper with OSM file: " + osmFileLocation);
        System.out.println("Using graph folder: " + graphFolderFiles);

        hopper = new GraphHopper();
        hopper.setGraphHopperLocation(graphFolderFiles);
        hopper.clean();
        hopper.init(
            new GraphHopperConfig().
                    putObject("datareader.file", osmFileLocation).
                    putObject("graph.location", graphFolderFiles).
                    putObject("prepare.min_network_size", 200).
                    putObject("import.osm.ignored_highways", ""). // if you are only using car you can ignore paths, tracks etc. here, take a look at the documentation in `config-example.yml`
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
            System.err.println("ERROR [GraphHopperPathingStrategy] GraphHopper graph file must be re-imported!");
            hopper.clean();
            hopper.importOrLoad();
        }
    }


    // Speed in m/s
    @Override
    public WayPointPath makePath(Attractor attractionPoint, double speed, Location currentLocation) {
        // Check if we need to update for a new geographic area
        String currentArea = Config.getGeographicArea();
        if (!graphFolderFiles.contains(currentArea.toLowerCase())) {
            updateAreaSettings();
        }
        
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
            req.getHints().put("simplify_response", "false");

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
                    && rand.nextDouble() <= probabilityForAlternativeRoute) {
                int altIdx = rand.nextInt(rsp.getAll().size() - 1) + 1;
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
            
//            Random rand = new Random(seed);
            
            for (int i = 1; i <= numWaypoints; i++) {
                double frac = (double) i / (numWaypoints + 1);
                // Add a small random perturbation to avoid straight lines if needed
                // double perturbation = (rand.nextDouble() - 0.5) * 0.01;
                Location mid = currentLocation.movedTowards(destination, distKm * frac);
                path.addWayPoint(new WayPoint(mid, CloudSim.clock() + stepTime * i));
            }
        }
        path.addWayPoint(new WayPoint(destination, CloudSim.clock() + time));
        return path;
    }
}

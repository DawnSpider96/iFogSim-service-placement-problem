package org.fog.mobilitydata;

import org.fog.utils.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Location {

	// Simon (080425) says block is deprecated

	public double latitude;
	public double longitude;
	public int block;

	private static double[][] BOUNDARY = Config.getBOUNDARY();
	private static double minLat = Config.getMinLat();
	private static double maxLat = Config.getMaxLat();
	private static double minLon = Config.getMinLon();
	private static double maxLon = Config.getMaxLon();
	
	// Cache of points of interest for improved performance
	private static Map<String, Location> pointsOfInterestCache = new HashMap<>();
	
	// Default random seed for location generation
	private static long defaultRandomSeed = System.currentTimeMillis();
	private static Random defaultRandom = new Random(defaultRandomSeed);

	/**
	 * Sets the default random seed for all random location generation methods
	 * that don't explicitly specify a seed.
	 * 
	 * @param seed the seed to use for random location generation
	 */
	public static void setDefaultRandomSeed(long seed) {
		defaultRandomSeed = seed;
		defaultRandom = new Random(seed);
		System.out.println("Location default random seed set to: " + seed);
	}
	
	/**
	 * Gets the current default random seed
	 * 
	 * @return the default random seed
	 */
	public static long getDefaultRandomSeed() {
		return defaultRandomSeed;
	}
	
	/**
	 * Gets a point of interest by name from the Config
	 * Uses a local cache to avoid repeated calls to Config
	 * 
	 * @param name the name of the point of interest
	 * @return the Location, or null if not found
	 */
	public static Location getPointOfInterest(String name) {
		if (!pointsOfInterestCache.containsKey(name)) {
			Location loc = Config.getPointOfInterest(name);
			if (loc != null) {
				pointsOfInterestCache.put(name, loc);
			}
		}
		return pointsOfInterestCache.get(name);
	}
	
	/**
	 * Convenience method to get the HOSPITAL1 location
	 * 
	 * @return the HOSPITAL1 location
	 */
	public static Location getHospital() {
		return getPointOfInterest("HOSPITAL1");
	}
	
	/**
	 * Convenience method to get the OPERA_HOUSE location
	 * 
	 * @return the OPERA_HOUSE location
	 */
	public static Location getOperaHouse() {
		return getPointOfInterest("OPERA_HOUSE");
	}
	
	/**
	 * Updates all static location configuration fields from Config.
	 * Call this method any time Config values are changed to ensure
	 * Location class uses the latest values.
	 */
	public static void refreshConfigValues() {
		BOUNDARY = Config.getBOUNDARY();
		minLat = Config.getMinLat();
		maxLat = Config.getMaxLat();
		minLon = Config.getMinLon();
		maxLon = Config.getMaxLon();
		
		// Clear and reload points of interest cache
		pointsOfInterestCache.clear();
		pointsOfInterestCache.putAll(Config.getAllPointsOfInterest());
	}
	
	public Location(double latitude, double longitude, int block) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.block = block;
	}

	// KM
	@Override
	public String toString() {
		return "Location{Latitude=" + latitude + ", Longitude=" + longitude + "}";
	}

	/*
	 * Haversine calculation of distance.
	 * Determines DIRECT distance from one point to another.
	 * Units: Kilometers
	 * */
	public double calculateDistance(Location loc2) {

		final int R = 6371; // Radius of the earth in Kilometers

		double latDistance = Math.toRadians(this.latitude - loc2.latitude);
		double lonDistance = Math.toRadians(this.longitude - loc2.longitude);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(loc2.latitude))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));


		return R * c;
	}

	public Location movedTowards(Location endLocation, double distance) {
		// km
		double dist = this.calculateDistance(endLocation);
		double ratio = distance / dist;
		if (ratio > 1) {
			return endLocation;
		}
		double newLatitude = this.latitude + (endLocation.latitude - this.latitude) * ratio;
		double newLongitude = this.longitude + (endLocation.longitude - this.longitude) * ratio;
		return new Location(newLatitude, newLongitude, this.block);
	}

	/**
	 * Calculate bearing from one location to another in degrees.
	 * 0° is north, 90° is east, 180° is south, 270° is west.
	 *
	 * @param to destination location
	 * @return bearing in degrees
	 */
	public double getBearing(Location to) {
		double lat1 = Math.toRadians(getLatitude());
		double lon1 = Math.toRadians(getLongitude());
		double lat2 = Math.toRadians(to.getLatitude());
		double lon2 = Math.toRadians(to.getLongitude());

		double dLon = lon2 - lon1;

		double y = Math.sin(dLon) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) -
				Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

		double bearing = Math.atan2(y, x);

		bearing = Math.toDegrees(bearing);

		return (bearing + 360) % 360;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	@Deprecated
	public static Location getRandomLocationSmallbox() {
		return getRandomLocationSmallbox(defaultRandomSeed);
	}
	
	@Deprecated
	public static Location getRandomLocationSmallbox(long seed) {
		Random rand = new Random(seed);
		double horizontalRatio = rand.nextDouble();
		double verticalRatio = rand.nextDouble();
		double width = BOUNDARY[1][0] - BOUNDARY[0][0];
		double height = BOUNDARY[2][1] - BOUNDARY[0][1];
		double newLatitude = BOUNDARY[0][0] + horizontalRatio * width;
		double newLongitude = BOUNDARY[0][1] + verticalRatio * height;
		return new Location(newLatitude, newLongitude, -1);
	}

	public static Location getRandomLocation() {
		return getRandomLocation(defaultRandomSeed);
	}
	
	public static Location getRandomLocation(long seed) {
		Random rand = new Random(seed);
		while (true) {
			double randLat = minLat + rand.nextDouble() * (maxLat - minLat);
			double randLon = minLon + rand.nextDouble() * (maxLon - minLon);

			if (isPointInPolygon(randLat, randLon, BOUNDARY)) {
				return new Location(randLat, randLon, -1);
			}
		}
	}

	public static Location getRandomLocationWithinRadius(double centerLat, double centerLon, double radiusInMeters) {
		return getRandomLocationWithinRadius(centerLat, centerLon, radiusInMeters, defaultRandomSeed);
	}

	public static Location getRandomLocationWithinRadius(double centerLat, double centerLon, double radiusInMeters, long seed) {
		Random rand = new Random(seed);
		final double radiusInDegrees = radiusInMeters / 111_000.0;

		while (true) {
			double distance = radiusInDegrees * Math.sqrt(rand.nextDouble());
			double angle = rand.nextDouble() * 2 * Math.PI;

			// Offset from center point
			double offsetLat = distance * Math.cos(angle);
			double offsetLon = distance * Math.sin(angle) / Math.cos(Math.toRadians(centerLat));

			double newLat = centerLat + offsetLat;
			double newLon = centerLon + offsetLon;

			if (isPointInPolygon(newLat, newLon, BOUNDARY)) {
				return new Location(newLat, newLon, -1);
			}
		}
	}


	public static boolean isPointInPolygon(double testLat, double testLon, double[][] polygon) {
		int intersections = 0;
		for (int i = 0; i < polygon.length; i++) {
			int j = (i + 1) % polygon.length;
			double lat_i = polygon[i][0];
			double lon_i = polygon[i][1];
			double lat_j = polygon[j][0];
			double lon_j = polygon[j][1];

			// Check if one vertex is above and one below the test latitude
			if ((lat_i > testLat) != (lat_j > testLat)) {
				// Interpolate a rightward ray from the test point,
				double intersectLon = (lon_j - lon_i) * (testLat - lat_i) / (lat_j - lat_i) + lon_i;
				// Check to avoid corner case.
				if (testLon < intersectLon) {
					intersections++;
				}
			}
		}
		return (intersections % 2) == 1;
	}
}

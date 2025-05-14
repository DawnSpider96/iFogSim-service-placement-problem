package org.fog.utils;

import org.cloudbus.cloudsim.Consts;
import org.fog.mobilitydata.Location;
import java.util.HashMap;
import java.util.Map;

public class Config {

	// Determines how often energy consumption is updated
	public static final double RESOURCE_MGMT_INTERVAL = 1000;
	public static int MAX_SIMULATION_TIME = 7200; // 5 hours, previously 2000
	public static int CONTROLLER_RESOURCE_MANAGE_INTERVAL = 1000;
	public static String FOG_DEVICE_ARCH = "x86";
	public static String FOG_DEVICE_OS = "Linux";
	public static String FOG_DEVICE_VMM = "Xen";
	public static double FOG_DEVICE_TIMEZONE = 10.0;
	public static double FOG_DEVICE_COST = 3.0;
	public static double FOG_DEVICE_COST_PER_MEMORY = 0.05;
	public static double FOG_DEVICE_COST_PER_STORAGE = 0.001;
	public static double FOG_DEVICE_COST_PER_BW = 0.0;
//	public static double MAX_VALUE = 1000000.0;

	// Geographic area name
	private static String geographicArea = "MELBOURNE";
	
	// Points of interest map
	private static Map<String, Location> pointsOfInterest = new HashMap<>();
	
	// Initialize default points of interest
//	static {
//		 Add default points
//		pointsOfInterest.put("HOSPITAL1", new Location(-37.81192, 144.95807, -1)); // Top left-ish
//		pointsOfInterest.put("OPERA_HOUSE", new Location(-37.81501, 144.97388, -1)); // Around bottom right
//	}

	// Location-related fields
	private static double[][] BOUNDARY = {
			{-37.8234, 144.95441}, // Bottom-left
			{-37.81559, 144.97882}, // Bottom-right
			{-37.81192, 144.94713}, // Top-left
			{-37.80406, 144.97107}  // Top-right
	};
	private static double minLat = -37.823400;
	private static double maxLat = -37.804060;
	private static double minLon = 144.947130;
	private static double maxLon = 144.978820;

	public static final double baseServerLatency = 31 * Consts.MILLISECOND;
	public static final double baseWifiLatency = 30 * Consts.MILLISECOND;

	// TODO Maybe make this bigger. My area of interest only has length 2km
	public static final double serverLatencyPerKilometer = 10 * Consts.MICROSECOND;
	public static final double wifiLatencyPerKilometer = 10 * Consts.MICROSECOND;

	// Create cluster among devices of same level with common parent irrespective of location. Only one of the two clustering modes should be used for clustering
	public static boolean ENABLE_STATIC_CLUSTERING = false;
	//Dynamic Clustering
	public static boolean ENABLE_DYNAMIC_CLUSTERING = false;
	public static double Node_Communication_RANGE = 300.0; // In terms of meter
	public static double clusteringLatency = 2.0; // second

	public static final int TRANSMISSION_START_DELAY = 50;

	public static final int SENSOR_OUTPUT_SIZE = 3;

	// Getters and setters for location-related fields
	
	/**
	 * Gets a point of interest by name
	 * 
	 * @param name the name of the point of interest
	 * @return the Location, or null if not found
	 */
	public static Location getPointOfInterest(String name) {
		return pointsOfInterest.get(name);
	}
	
	/**
	 * Sets a point of interest
	 * 
	 * @param name the name of the point of interest
	 * @param location the location to set
	 */
	public static void setPointOfInterest(String name, Location location) {
		pointsOfInterest.put(name, location);
	}
	
	/**
	 * Gets all points of interest
	 * 
	 * @return a map of all points of interest
	 */
	public static Map<String, Location> getAllPointsOfInterest() {
		return new HashMap<>(pointsOfInterest);
	}
	
	/**
	 * Sets all points of interest, replacing the existing ones
	 * 
	 * @param points the new points of interest
	 */
	public static void setAllPointsOfInterest(Map<String, Location> points) {
		pointsOfInterest.clear();
		pointsOfInterest.putAll(points);
	}
	
	public static double[][] getBOUNDARY() {
		return BOUNDARY;
	}
	
	public static void setBOUNDARY(double[][] boundary) {
		BOUNDARY = boundary;
	}
	
	public static double getMinLat() {
		return minLat;
	}
	
	public static void setMinLat(double value) {
		minLat = value;
	}
	
	public static double getMaxLat() {
		return maxLat;
	}
	
	public static void setMaxLat(double value) {
		maxLat = value;
	}
	
	public static double getMinLon() {
		return minLon;
	}
	
	public static void setMinLon(double value) {
		minLon = value;
	}
	
	public static double getMaxLon() {
		return maxLon;
	}
	
	public static void setMaxLon(double value) {
		maxLon = value;
	}
	
	/**
	 * Gets the geographic area name
	 * 
	 * @return the geographic area name
	 */
	public static String getGeographicArea() {
		return geographicArea;
	}
	
	/**
	 * Sets the geographic area name
	 * 
	 * @param area the geographic area name
	 */
	public static void setGeographicArea(String area) {
		geographicArea = area;
	}
}

package org.fog.mobilitydata;

import org.fog.utils.Config;

import java.util.Random;

public class Location {

	// Simon (080425) says block is deprecated

	public double latitude;
	public double longitude;
	public int block;

	private static double[][] BOUNDARY = Config.BOUNDARY;
	private static double minLat = Config.minLat;
	private static double maxLat = Config.maxLat;
	private static double minLon = Config.minLon;
	private static double maxLon = Config.maxLon;

	// Landmarks
	public static final Location HOSPITAL1 = Config.HOSPITAL1;
	
	public Location(double latitude, double longitude, int block) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.block = block;
	}

	@Override
	public String toString() {
		return "Location{Latitude=" + latitude + ", Longitude=" + longitude + "}";
	}

	public double calculateDistance(Location loc2) {

		final int R = 6371; // Radius of the earth in Kilometers

		double latDistance = Math.toRadians(this.latitude - loc2.latitude);
		double lonDistance = Math.toRadians(this.longitude - loc2.longitude);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(loc2.latitude))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c; // kms


		distance = Math.pow(distance, 2);

		return Math.sqrt(distance);
	}

	public Location movedTowards(Location endLocation, double distance) {
		double dist = this.calculateDistance(endLocation);
		double ratio = distance / dist;
		if (ratio > 1) {
			return endLocation;
		}
		double newLatitude = this.latitude + (endLocation.latitude - this.latitude) * ratio;
		double newLongitude = this.longitude + (endLocation.longitude - this.longitude) * ratio;
		return new Location(newLatitude, newLongitude, this.block);
	}


	@Deprecated
	public static Location getRandomLocationSmallbox() {
		Random rand = new Random();
		double horizontalRatio = rand.nextDouble();
		double verticalRatio = rand.nextDouble();
		double width = BOUNDARY[1][0] - BOUNDARY[0][0];
		double height = BOUNDARY[2][1] - BOUNDARY[0][1];
		double newLatitude = BOUNDARY[0][0] + horizontalRatio * width;
		double newLongitude = BOUNDARY[0][1] + verticalRatio * height;
		return new Location(newLatitude, newLongitude, -1);
	}

	public static Location getRandomLocation() {
		Random rand = new Random();
		while (true) {
			double randLat = minLat + rand.nextDouble() * (maxLat - minLat);
			double randLon = minLon + rand.nextDouble() * (maxLon - minLon);

			if (isPointInPolygon(randLat, randLon, BOUNDARY)) {
				return new Location(randLat, randLon, -1);
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

package org.fog.mobilitydata;

import java.util.Random;

public class Location {

	public double latitude;
	public double longitude;
	public int block;

	public static final double[][] BOUNDARY = {
		{-37.8234, 144.95441}, // Bottom-left
		{-37.81559, 144.97882}, // Bottom-right
		{-37.81192, 144.94713}, // Top-left
		{-37.80406, 144.97107}  // Top-right
	};
	
	public Location(double latitude, double longitude, int block) {
		// TODO Auto-generated constructor stub
		this.latitude = latitude;
		this.longitude = longitude;
		this.block = block;
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


	public static Location getRandomLocation() {
		Random rand = new Random();
		double horizontalRatio = rand.nextDouble();
		double verticalRatio = rand.nextDouble();
		double width = BOUNDARY[1][0] - BOUNDARY[0][0];
		double height = BOUNDARY[2][1] - BOUNDARY[0][1];
		double newLatitude = BOUNDARY[0][0] + horizontalRatio * width;
		double newLongitude = BOUNDARY[0][1] + verticalRatio * height;
		return new Location(newLatitude, newLongitude, -1);
	}
}

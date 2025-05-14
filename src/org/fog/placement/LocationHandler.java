package org.fog.placement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.mobilitydata.ExperimentDataParser;
import org.fog.mobilitydata.Location;
import org.fog.mobilitydata.DataParser;
import org.fog.mobilitydata.References;
import org.fog.mobility.DeviceMobilityState;
import org.fog.utils.Config;

/**
 * This class handles device location tracking and parent determination.
 * 
 * NOTE: The original methodology using predefined mobility data from files is being
 * deprecated in favor of the dynamic DeviceMobilityState system. Methods related to static
 * mobility data loading and processing will be phased out in future versions.
 */
public class LocationHandler {
	
	/**
	 * @deprecated This data structure is part of the old static mobility system and will be phased out.
	 * Future implementations should use the DeviceMobilityState system instead.
	 */
	public DataParser dataObject;
	
	public Map<Integer, String> instanceToDataId;
	/** Maps deviceId to its mobility state for dynamic location tracking */
	private Map<Integer, DeviceMobilityState> deviceMobilityStates;

	/**
	 * Creates a new LocationHandler with the given DataParser.
	 * 
	 * @param dataObject the data parser
	 * @deprecated The DataParser parameter is part of the old static mobility system.
	 * Future implementations should use registerDeviceMobilityState() instead of DataParser.
	 */
	public LocationHandler(DataParser dataObject) {
		this.dataObject = dataObject;
		instanceToDataId = new HashMap<Integer, String>();
		deviceMobilityStates = new HashMap<Integer, DeviceMobilityState>();
	}

	public LocationHandler() {
		instanceToDataId = new HashMap<Integer, String>();
		deviceMobilityStates = new HashMap<Integer, DeviceMobilityState>();
	}
	
	/**
	 * Registers a device's mobility state with the location handler
	 * 
	 * @param deviceId the device ID
	 * @param mobilityState the mobility state
	 */
	public void registerDeviceMobilityState(int deviceId, DeviceMobilityState mobilityState) {
		deviceMobilityStates.put(deviceId, mobilityState);
	}
	
	/**
	 * Gets a device's mobility state
	 * 
	 * @param deviceId the device ID
	 * @return the device's mobility state, or null if not registered
	 */
	public DeviceMobilityState getDeviceMobilityState(int deviceId) {
		return deviceMobilityStates.get(deviceId);
	}
	
	/**
	 * @return the data object
	 * @deprecated This method returns the old static mobility DataParser and will be phased out.
	 * Future implementations should use the DeviceMobilityState system instead.
	 */
	public DataParser getDataObject(){
		return dataObject;
	}
	
	public static double calculateDistance(Location loc1, Location loc2) {

	    final int R = 6371; // Radius of the earth in Kilometers

	    double latDistance = Math.toRadians(loc1.latitude - loc2.latitude);
	    double lonDistance = Math.toRadians(loc1.longitude - loc2.longitude);
	    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
	            + Math.cos(Math.toRadians(loc1.latitude)) * Math.cos(Math.toRadians(loc2.latitude))
	            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
	    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	    double distance = R * c; // kms


	    distance = Math.pow(distance, 2);

	    return Math.sqrt(distance);
	}

	public double calculateDistance(int entity1, int entity2) {
		Location loc1;
		Location loc2;

		double time = CloudSim.clock();

		String dataId = getDataIdByInstanceID(entity1);
		int resourceLevel = getDataObject().resourceAndUserToLevel.get(dataId);
		if(resourceLevel != getDataObject().levelID.get("User"))
			loc1 = getResourceLocationInfo(dataId);
		else
			loc1 = getUserLocationInfo(entity1, dataId, time);

		String dataId2 = getDataIdByInstanceID(entity2);
		int resourceLevel2 = getDataObject().resourceAndUserToLevel.get(dataId2);
		if(resourceLevel2 != getDataObject().levelID.get("User"))
			loc2 = getResourceLocationInfo(dataId2);
		else
			loc2 = getUserLocationInfo(entity2, dataId2, time);

		return calculateDistance(loc1, loc2);
	}

	public double calculateLatencyUsingDistance(int entity1, int entity2) {
		double latency;
		if (getDataObject().resourceAndUserToLevel.get(getDataIdByInstanceID(entity1)) == getDataObject().levelID.get("User") ||
				getDataObject().resourceAndUserToLevel.get(getDataIdByInstanceID(entity2)) == getDataObject().levelID.get("User")) {
			latency = Config.baseWifiLatency + (calculateDistance(entity1, entity2) * Config.wifiLatencyPerKilometer);
		}
		else {
			latency = Config.baseServerLatency + (calculateDistance(entity1, entity2) * Config.serverLatencyPerKilometer);
		}
		System.out.println(entity1 + " " + entity2 + " " + latency);
		return latency;
	}
	

	public int determineParent(int resourceId, double time) {
		String dataId = getDataIdByInstanceID(resourceId);
		int resourceLevel = getDataObject().resourceAndUserToLevel.get(dataId);
		int parentLevel = resourceLevel-1;
		Location resourceLoc;
		if(resourceLevel != getDataObject().levelID.get("User"))
			resourceLoc = getResourceLocationInfo(dataId);
		else
			resourceLoc = getUserLocationInfo(resourceId, dataId, time);
		
		int parentInstanceId = References.NOT_SET;	
		String parentDataId = "";
				
	
		if(time<References.INIT_TIME){
			for(int i=0; i<getLevelWiseResources(parentLevel).size();i++){
				Location potentialParentLoc = getResourceLocationInfo(getLevelWiseResources(parentLevel).get(i));
				if(potentialParentLoc.block==resourceLoc.block) {
					parentDataId = getLevelWiseResources(parentLevel).get(i);
					for(int parentIdIterator: instanceToDataId.keySet())
					{
						if(instanceToDataId.get(parentIdIterator).equals(parentDataId))
						{
							parentInstanceId = parentIdIterator;
						}
					}
				}	
			}
		}
		else
		{
			double minmumDistance = Double.MAX_VALUE; //Used to be config.max_value
			for(int i=0; i<getLevelWiseResources(parentLevel).size();i++){
				Location potentialParentLoc = getResourceLocationInfo(getLevelWiseResources(parentLevel).get(i));
				
				double distance = calculateDistance(resourceLoc, potentialParentLoc);
					if(distance<minmumDistance){
						parentDataId = getLevelWiseResources(parentLevel).get(i);
						minmumDistance = distance;
					}
			}
			
			for(int parentIdIterator: instanceToDataId.keySet())
			{
				if(instanceToDataId.get(parentIdIterator).equals(parentDataId))
				{
					parentInstanceId = parentIdIterator;
				}
			}
			
		}
		
		return parentInstanceId;	
	}	

	/**
	 * Gets the user's location, first checking the mobility state and falling back to historical data if not found
	 * 
	 * @param deviceId the device ID
	 * @param dataId the data ID
	 * @param time the timestamp
	 * @return the user's location
	 */
	private Location getUserLocationInfo(int deviceId, String dataId, double time) {
		// First check if we have a mobility state for this device
		DeviceMobilityState mobilityState = deviceMobilityStates.get(deviceId);
		if (mobilityState != null) {
			return mobilityState.getCurrentLocation();
		}
		
		// Fall back to historical data if no mobility state exists
		return getDataObject().usersLocation.get(dataId).get(time);
	}

	private Location getResourceLocationInfo(String dataId) {
		return getDataObject().resourceLocationData.get(dataId);
	}

	/**
	 * Gets the time sheet for a device from the static mobility data.
	 * 
	 * @param instanceId the device ID
	 * @return the time sheet
	 * @deprecated This method is part of the old static mobility system and will be phased out.
	 * Future implementations should use the DeviceMobilityState system for dynamic mobility scheduling.
	 */
	public List<Double> getTimeSheet(int instanceId) {
		String dataId = getDataIdByInstanceID(instanceId);
		List<Double>timeSheet = new ArrayList<Double>(getDataObject().usersLocation.get(dataId).keySet());
		return timeSheet;
	}

	public void linkDataWithInstance(int instanceId, String dataID) {
		instanceToDataId.put(instanceId, dataID);
	}

	public int getLevelID(String resourceType) {
		return dataObject.levelID.get(resourceType);
	}
	
	public ArrayList<String> getLevelWiseResources(int levelNo) {
		return getDataObject().levelwiseResources.get(levelNo);
	}

	/**
	 * Parses user mobility data from a file.
	 * 
	 * @param userMobilityPattern the mobility pattern map
	 * @param datasetReference the path to the dataset file
	 * @throws IOException if an I/O error occurs
	 * @deprecated This method loads static mobility data from files and will be phased out.
	 * Future implementations should use registerDeviceMobilityState() with DeviceMobilityState
	 * instances for dynamic mobility control.
	 */
	public void parseUserInfo(Map<Integer, Integer> userMobilityPattern, String datasetReference) throws IOException {
		getDataObject().parseUserData(userMobilityPattern, datasetReference);
	}

	/**
	 * Parses user mobility data from a file with a specified number of users.
	 * 
	 * @param userMobilityPattern the mobility pattern map
	 * @param datasetReference the path to the dataset file
	 * @param numberOfUser the number of users to parse
	 * @throws NumberFormatException if a parsing error occurs
	 * @throws IOException if an I/O error occurs
	 * @deprecated This method loads static mobility data from files and will be phased out.
	 * Future implementations should use registerDeviceMobilityState() with DeviceMobilityState
	 * instances for dynamic mobility control.
	 */
	public void parseUserInfo(Map<Integer, Integer> userMobilityPattern, String datasetReference, int numberOfUser) throws NumberFormatException, IOException {
		((ExperimentDataParser) getDataObject()).parseUserData(userMobilityPattern, datasetReference, numberOfUser);
	}

	/**
	 * Parses resource location data from a file.
	 * 
	 * @throws NumberFormatException if a parsing error occurs
	 * @throws IOException if an I/O error occurs
	 * @deprecated This method loads static resource data from files and will be phased out
	 * in favor of a more dynamic approach in future versions.
	 */
	public void parseResourceInfo() throws NumberFormatException, IOException {
		getDataObject().parseResourceData();
	}

	/**
	 * Parses resource location data from a file with a specified number of edge nodes.
	 * 
	 * @param numberOfEdge the number of edge nodes to parse
	 * @throws NumberFormatException if a parsing error occurs
	 * @throws IOException if an I/O error occurs
	 * @deprecated This method loads static resource data from files and will be phased out
	 * in favor of a more dynamic approach in future versions.
	 */
	public void parseResourceInfo(int numberOfEdge) throws NumberFormatException, IOException {
		((ExperimentDataParser) getDataObject()).parseResourceData(numberOfEdge);
	}

	/**
	 * Gets the list of mobile user data IDs.
	 * 
	 * @return list of mobile user data IDs
	 * @deprecated This method is part of the old static mobility system and will be phased out.
	 * Future implementations should use the DeviceMobilityState system instead.
	 */
	public List<String> getMobileUserDataId() {
		List<String> userDataIds = new ArrayList<>(getDataObject().usersLocation.keySet());
		return userDataIds;
	}

//	public List<String> getImmobileUserDataId() {
//		// TODO Auto-generated method stub
//		OfflineDataParser parser = (OfflineDataParser) getDataObject();
//		List<String> userDataIds = new ArrayList<>(parser.immobileUserLocationData.keySet());
//		return userDataIds;
//	}

	public Map<String, Integer> getDataIdsLevelReferences() {
		return getDataObject().resourceAndUserToLevel;
	}
	
	public boolean isCloud(int instanceID) {
		String dataId = getDataIdByInstanceID(instanceID);
		int instenceLevel=getDataObject().resourceAndUserToLevel.get(dataId);
		if(instenceLevel==getDataObject().levelID.get("Cloud"))
			return true;
		else
			return false;
	}
	
	public String getDataIdByInstanceID(int instanceID) {
		return instanceToDataId.get(instanceID);
	}
	
	public Map<Integer, String> getInstanceDataIdReferences() {
		return instanceToDataId;
	}

	public boolean isAMobileDevice(int instanceId) {
		String dataId = getDataIdByInstanceID(instanceId);
		int instanceLevel=getDataObject().resourceAndUserToLevel.get(dataId);
		if(instanceLevel==getDataObject().levelID.get("User"))
			return true;
		else
			return false;
	}
}

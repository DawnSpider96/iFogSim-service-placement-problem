package org.fog.mobility;

import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds an ordered list (or queue) of WayPoints for a single route.
 * Provides methods to access the "next" waypoint or remove the consumed ones.
 */
public class WayPointPath {

    /**
     * The ordered list of WayPoints the device will traverse.
     * Typically sorted by ascending arrivalTime.
     */
    private Queue<WayPoint> path;
    
    /**
     * Keeps track of waypoints that have been visited
     */
    private List<WayPoint> completedWaypoints;

    /**
     * Creates a new, empty waypoint path
     */
    public WayPointPath() {
        this.path = new LinkedList<>();
        this.completedWaypoints = new ArrayList<>();
    }
    
    /**
     * Creates a waypoint path with the given queue of waypoints
     * 
     * @param path the initial waypoints
     */
    public WayPointPath(Queue<WayPoint> path) {
        this.path = path;
        this.completedWaypoints = new ArrayList<>();
    }
    
    /**
     * Adds a waypoint to the path
     * 
     * @param waypoint the waypoint to add
     */
    public void addWayPoint(WayPoint waypoint) {
        path.add(waypoint);
    }

    /**
     * Retrieves the next WayPoint (the head of the queue).
     * Returns null if none remain.
     * 
     * @return next WayPoint in the path or null if the path is empty
     */
    public WayPoint getNextWayPoint() {
        if (path.isEmpty()) {
            return null;
        }
        return path.peek();
    }

    /**
     * Removes the next WayPoint from the list (because it has been visited).
     */
    public void removeNextWayPoint() {
        if (!path.isEmpty()) {
            WayPoint used = path.poll();
            completedWaypoints.add(used);
        }
    }

    /**
     * Checks if there are no more waypoints left in the path.
     * 
     * @return true if path is empty, false otherwise
     */
    public boolean isEmpty() {
        return path.isEmpty();
    }
    
    /**
     * Gets all waypoints that have been visited
     * 
     * @return list of completed waypoints
     */
    public List<WayPoint> getCompletedWaypoints() {
        return completedWaypoints;
    }
    
    /**
     * Gets all remaining waypoints in the path
     * 
     * @return queue of remaining waypoints
     */
    public Queue<WayPoint> getRemainingWaypoints() {
        return path;
    }
} 
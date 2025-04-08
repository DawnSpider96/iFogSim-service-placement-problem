package org.fog.mobility;

import org.fog.mobilitydata.Location;

/**
 * Encapsulates a point of interest or "attraction" in the simulation (like a hospital or random location).
 * Provides a method to determine how long a device should pause once it arrives here.
 * Holds location and other metadata such as name, min/max pause times, etc.
 */
public class Attractor {

    private Location attractionPoint;
    private String name;
    private double pauseTimeMin;
    private double pauseTimeMax;
    private PauseTimeStrategy pauseTimeStrategy;

    public Attractor(Location attractionPoint, String name, double pauseTimeMin, double pauseTimeMax, PauseTimeStrategy pauseTimeStrategy) {
        this.attractionPoint = attractionPoint;
        this.name = name;
        this.pauseTimeMin = pauseTimeMin;
        this.pauseTimeMax = pauseTimeMax;
        this.pauseTimeStrategy = pauseTimeStrategy;
    }

    /**
     * Returns the geographic location of this attraction.
     * 
     * @return the location
     */
    public Location getAttractionPoint() {
        return this.attractionPoint;
    }

    public double getPauseTimeMin() {
        return this.pauseTimeMin;
    }

    public double getPauseTimeMax() {
        return this.pauseTimeMax;
    }

    public PauseTimeStrategy getPauseTimeStrategy() {
        return this.pauseTimeStrategy;
    }

    /**
     * Returns the name (optional).
     * Might be set if it's a known landmark, e.g. "General Hospital."
     * 
     * @return the name of the attraction
     */
    public String getName() {
        return this.name;
    }

    /**
     * Determines the actual pause time by calling getPauseTimeStrat().
     * 
     * @return the computed pause time
     */
    public double determinePauseTime() {
        return this.pauseTimeStrategy.determinePauseTime(this.pauseTimeMin, this.pauseTimeMax);
    }
} 
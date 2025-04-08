package org.fog.mobility;

import org.fog.mobilitydata.Location;

/**
 * Encapsulates a point of interest or "attraction" in the simulation (like a hospital or random location).
 * Provides a method to determine how long a device should pause once it arrives here.
 * Holds location and other metadata such as name, min/max pause times, etc.
 */
public interface IAttract {

    /**
     * Returns the geographic location of this attraction.
     * 
     * @return the location
     */
    Location getAttractionPoint();

    /**
     * Returns the name (optional).
     * Might be set if it's a known landmark, e.g. "General Hospital."
     * 
     * @return the name of the attraction
     */
    String getName();

    /**
     * Returns the minimum pause time possible at this attraction.
     * 
     * @return the minimum pause time
     */
    double getPauseTimeMin();

    /**
     * Returns the maximum pause time possible at this attraction.
     * 
     * @return the maximum pause time
     */
    double getPauseTimeMax();

    /**
     * Returns the PauseTimeStrategy used to compute the actual pause time.
     * 
     * @return the pause time strategy
     */
    PauseTimeStrategy getPauseTimeStrat();

    /**
     * Determines the actual pause time by calling getPauseTimeStrat().
     * 
     * @return the computed pause time
     */
    double determinePauseTime();
} 
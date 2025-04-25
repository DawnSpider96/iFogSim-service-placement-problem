package org.fog.mobility;

import java.util.Random;

/**
 * A strategy object that, given a min and max pause time, returns the actual pause time.
 * Allows for easy polymorphism if you have specialized or random-based strategies.
 */
public class PauseTimeStrategy {
    
    private Random random;
    
    /**
     * Creates a new pause time strategy with a random seed
     */
//    public PauseTimeStrategy() {
//        this.random = new Random();
//    }
    
    /**
     * Creates a new pause time strategy with a specified seed
     * 
     * @param seed the random seed to use
     */
    public PauseTimeStrategy(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Determines the actual pause time, given a min and max boundary.
     * Default implementation returns a random number in [min, max].
     * 
     * @param min lower bound of possible pause time
     * @param max upper bound of possible pause time
     * @return the computed pause time
     */
    public double determinePauseTime(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }
} 
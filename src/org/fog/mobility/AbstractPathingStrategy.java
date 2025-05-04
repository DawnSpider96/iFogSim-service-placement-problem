package org.fog.mobility;

import java.util.Random;

/**
 * Abstract base class for pathing strategies that implements the seed-related methods.
 * Provides common functionality for all pathing strategies.
 */
public abstract class AbstractPathingStrategy implements PathingStrategy {
    
    /** The random seed used by this pathing strategy. */
    protected long seed;
    
    protected Random rand;
    
    /**
     * Creates a new pathing strategy with the current system time as seed.
     */
    public AbstractPathingStrategy() {
        this(System.currentTimeMillis());
    }
    
    /**
     * Creates a new pathing strategy with the specified seed.
     * 
     * @param seed the random seed to use
     */
    public AbstractPathingStrategy(long seed) {
        this.seed = seed;
        this.rand = new Random(seed);
    }
    
    /**
     * Sets the random seed for this pathing strategy.
     * 
     * @param seed the new seed value
     */
    @Override
    public void setSeed(long seed) {
        this.seed = seed;
        this.rand = new Random(seed);
    }
    
    /**
     * Gets the current random seed for this pathing strategy.
     * 
     * @return the current seed value
     */
    @Override
    public long getSeed() {
        return seed;
    }
} 
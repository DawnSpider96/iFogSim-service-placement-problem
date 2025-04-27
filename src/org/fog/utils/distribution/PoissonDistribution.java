package org.fog.utils.distribution;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Class that implements a Poisson distribution for determining delay times
 * based on user type.
 * 
 * Lambda represents the rate parameter - for time-based events, if you want
 * approximately one event every X seconds, you should use lambda = 1/X.
 * For example, for one event every 300 seconds, use lambda = 1/300 = 0.00333
 */
public class PoissonDistribution extends Distribution {

    private double lambda; // rate parameter
    private Map<String, Double> userTypeLambdaMap; // map to store different lambda values for different user types

    /**
     * Constructor for a basic Poisson distribution with a single lambda value
     * @param lambda the rate parameter of the Poisson distribution (events per unit time)
     */
    public PoissonDistribution(double lambda) {
        super();
        this.lambda = lambda;
        this.random = new Random();
        this.userTypeLambdaMap = new HashMap<>();
    }

    /**
     * Constructor with a seed for the random number generator
     * @param lambda the rate parameter (events per unit time)
     * @param seed the seed for the random number generator
     */
    public PoissonDistribution(double lambda, long seed) {
        super();
        this.lambda = lambda;
        this.random = new Random(seed);
        this.userTypeLambdaMap = new HashMap<>();
    }

    /**
     * Add a lambda value for a specific user type
     * @param userType the type of user - must not be null
     * @param lambda the rate parameter for this user type
     * @throws NullPointerException if userType is null
     */
    public void addUserTypeLambda(String userType, double lambda) {
        if (userType == null) {
            throw new NullPointerException("User type cannot be null");
        }
        userTypeLambdaMap.put(userType, lambda);
    }

    /**
     * Get the lambda value for a specific user type
     * @param userType the type of user - must not be null
     * @return the lambda value for this user type
     * @throws NullPointerException if userType is null or not found in the mapping
     */
    public double getLambdaForUserType(String userType) {
        if (userType == null) {
            throw new NullPointerException("User type cannot be null");
        }
        if (!userTypeLambdaMap.containsKey(userType)) {
            throw new NullPointerException("No lambda value defined for user type: " + userType);
        }
        return userTypeLambdaMap.get(userType);
    }

    /**
     * Generate a Poisson-distributed value based on the default lambda
     * @return a random value from the Poisson distribution
     */
    public double getNextPoissonValue() {
        return generatePoissonValue(lambda);
    }

    /**
     * Generate a Poisson-distributed value based on the lambda for a specific user type
     * @param userType the type of user - must not be null and must exist in the mapping
     * @return a random value from the Poisson distribution with the appropriate lambda
     * @throws NullPointerException if userType is null or not found in the mapping
     */
    public double getNextPoissonValue(String userType) {
        double userLambda = getLambdaForUserType(userType); // This will throw NPE if userType is null or not found
        return generatePoissonValue(userLambda);
    }

    /**
     * Generate a random value from a Poisson distribution with the given lambda
     * For time-based events, this can be multiplied by the desired time unit
     * to get appropriate inter-arrival times.
     * 
     * @param lambda the rate parameter
     * @return a random value from the Poisson distribution
     */
    private double generatePoissonValue(double lambda) {
        // Implementation based on the inverse transform sampling method
        // This gives number of events in a unit time period
        // For inter-arrival times, use 1.0 / (generatePoissonValue(lambda) + 1)
        // to avoid division by zero
        
        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;

        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);

        return k - 1;
    }
    
    /**
     * Generate a time interval until the next event based on the exponential distribution
     * This is more appropriate for modeling time between events in a Poisson process
     * 
     * @param lambda the rate parameter
     * @return a random time interval from an exponential distribution
     */
    private double generateExponentialInterval(double lambda) {
        // For a Poisson process, inter-arrival times follow exponential distribution
        return -Math.log(random.nextDouble()) / lambda;
    }
    
    /**
     * Get a random time interval until the next event based on the default lambda
     * This is more appropriate for determining when the next event should occur
     * 
     * @return a random time interval from an exponential distribution
     */
    @Override
    public double getNextValue() {
        return generateExponentialInterval(lambda);
    }
    
    /**
     * Get a random time interval until the next event based on the lambda for a specific user type
     * 
     * @param userType the type of user - must not be null and must exist in the mapping
     * @return a random time interval from an exponential distribution
     * @throws NullPointerException if userType is null or not found in the mapping
     */
    public double getNextValue(String userType) {
        double userLambda = getLambdaForUserType(userType); // This will throw NPE if userType is null or not found
        return generateExponentialInterval(userLambda);
    }

    /**
     * Get the distribution type
     * @return the distribution type identifier
     */
    @Override
    public int getDistributionType() {
        return Distribution.POISSON;
    }

    /**
     * Get the mean inter-transmit time (1/lambda for Poisson process)
     * @return the mean value of the inter-arrival time (which is 1/lambda for Poisson)
     */
    @Override
    public double getMeanInterTransmitTime() {
        return 1.0 / lambda;
    }

    /**
     * Get the lambda rate parameter
     * @return the lambda value
     */
    public double getLambda() {
        return lambda;
    }

    /**
     * Set the lambda rate parameter
     * @param lambda the lambda value to set
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }
    
    /**
     * Check if a lambda value exists for a specific user type
     * @param userType the type of user to check
     * @return true if a lambda value exists for this user type, false otherwise
     * @throws NullPointerException if userType is null
     */
    public boolean hasLambdaForUserType(String userType) {
        if (userType == null) {
            throw new NullPointerException("User type cannot be null");
        }
        return userTypeLambdaMap.containsKey(userType);
    }
} 
package org.fog.utils.distribution;

import java.util.Random;

public abstract class Distribution {

	public static int NORMAL = 1;
	public static int DETERMINISTIC = 2;
	public static int UNIFORM = 3;
	public static int POISSON = 4;
	
	protected Random random;
	public abstract double getNextValue();
	
	public Random getRandom() {
		return random;
	}

	public void setRandom(Random random) {
		this.random = random;
	}

	public abstract int getDistributionType();
	public abstract double getMeanInterTransmitTime();
}

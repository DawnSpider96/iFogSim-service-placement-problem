package org.fog.entities;

import java.util.List;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.power.models.PowerModel;

public class MyFogDevice extends FogDevice {
	
	public MyFogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy,
			List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
			double uplinkLatency, double ratePerMips) throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, uplinkBandwidth, downlinkBandwidth,
				uplinkLatency, ratePerMips);
		// TODO Auto-generated constructor stub
	}

	private int mips;

	
	
	public int getMips() {
			return mips;
	}
	
	public void setMips(int mips) {
		this.mips = mips;
	}
	
	

}

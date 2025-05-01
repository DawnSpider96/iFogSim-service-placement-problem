package org.fog.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogUtils;
import org.fog.utils.GeoCoverage;

public class MyApplication extends Application {
	
	public MyApplication(String appId, int userId) {
		super(appId, userId);
		// TODO Auto-generated constructor stub
	}

	public static MyApplication createMyApplication(String appId, int userId){
		return new MyApplication(appId, userId);
	}
	
//	private Map<Integer, Map<String, Double>> deadlineInfo;
	private Map<Integer, Map<String, Integer>> additionalMipsInfo;
	
	public void addAppModule(String moduleName,int ram, int mips, long size, long bw){
		String vmm = "Xen";
		// Debug: Print current entity ID before VM creation
		System.out.println("Creating AppModule '" + moduleName + "', ENTITY_ID before: " + FogUtils.getCurrentEntityId());
		AppModule module = new AppModule(FogUtils.generateEntityId(), moduleName, appId, userId, mips, ram, bw, size, vmm, new TupleScheduler(mips, 1), new HashMap<Pair<String, String>, SelectivityModel>());
		System.out.println("Created AppModule with ID: " + module.getId());
		getModules().add(module); 
	}
	
	public Map<Integer, Map<String, Integer>> getAdditionalMipsInfo() {
		return additionalMipsInfo;
	}
	
	public void setAdditionalMipsInfo(Map<Integer, Map<String, Integer>> additionalMipsInfo) {
		this.additionalMipsInfo = additionalMipsInfo;
	}
	
}
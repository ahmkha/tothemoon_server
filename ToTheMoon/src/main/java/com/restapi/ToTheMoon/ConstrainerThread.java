package com.restapi.ToTheMoon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

public class ConstrainerThread extends Thread {
	private String fileName;
	private ArrayList<Districting> districtingsList;
	private int counter;
	private Job job;
	
	private float popEq;
	private String popEqScoreType;
	private String popType;
	private float compactness;
	private float mmThreshhold;
	private int numMM;
	private List<Integer> incumbents;
	private String minorityPopPercentageType;
	private List<Districting> districtings;
	
	public ConstrainerThread(String fileName, int counter, Job job, float popEq, String popEqScoreType, String popType, 
			float compactness, float mmThreshhold, int numMM, List<Integer> incumbents, String minorityPopPercentageType, List<Districting> districtings) {
		this.fileName = fileName;
		this.districtingsList = new ArrayList<>();
		this.counter = counter;
		this.job = job;
		
		this.popEq = popEq;
		this.popEqScoreType = popEqScoreType;
		this.popType = popType;
		this.compactness = compactness;
		this.mmThreshhold = mmThreshhold;
		this.numMM = numMM;
		this.incumbents = incumbents;
		this.minorityPopPercentageType = minorityPopPercentageType;
		this.districtings = districtings;
	}

	@Override
	public void run() {
	    try (
	            InputStream inputStream = Files.newInputStream(java.nio.file.Path.of(Constants.YOUR_DIRECTORY_PREFIX + this.fileName));
	            JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
	    ) {            
	        reader.beginObject();
	        reader.nextName();
	        reader.beginArray();
	        while (reader.hasNext()) {
	        	JsonObject plan = new Gson().fromJson(reader, JsonObject.class);

	        	Districting validPlan = this.job.constrain(this.popEq, this.popEqScoreType, this.popType, this.compactness, 
	        			this.mmThreshhold, this.numMM, this.minorityPopPercentageType, this.incumbents, plan, this.counter);
	        	//Districting validPlan = this.job.constrain(0.15, "totalPopulationScore", "VAP", 0.02, 0.20, 2, "HPERCENTAGE", Arrays.asList(1), plan, counter);
	        	//Districting validPlan = this.job.constrain(0.15, "totalPopulationScore", "VAP", 0.02, 0.20, 1, "BPERCENTAGE", Arrays.asList(4,6,7), plan, counter);
	        	//Districting validPlan = this.job.constrain(this.popEq, this.popEqScoreType, this.popType, this.compactness, 
	        			//this.mmThreshhold, this.numMM, this.minorityPopPercentageType, this.incumbents, plan, this.counter);
	        	if (validPlan != null) {
	        		this.districtings.add(validPlan);
	        	}
	        }
	        reader.endArray();
	        inputStream.close();
	        reader.close();
	    } catch (IOException e) {
			e.printStackTrace();
		} finally {
			System.out.println("Thread " + counter + " done.");
		}
	}
	
	public ArrayList<Districting> getDistrictingsList() {
		return districtingsList;
	}

}

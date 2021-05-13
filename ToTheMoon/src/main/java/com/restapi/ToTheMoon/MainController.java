package com.restapi.ToTheMoon;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.*;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import javafx.util.Pair;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;


/** Exact path to the MainController is given by:
 * localhost:8080/ToTheMoon/webresources/tothemoon
 */
@Path("/tothemoon")
public class MainController {
	private TempEntityManager em = new TempEntityManager();
	private static final String NEVADA_ENACTED_FILE = "D:\\\\Users\\\\Documents\\\\GitHub\\\\tothemoon_server\\\\ToTheMoon\\\\src\\\\main\\\\java\\\\DistrictingData\\\\nv_districts_with_data.json";
	private static final String NEVADA_GEO_FILE = Constants.YOUR_DIRECTORY_PREFIX + Constants.NEVADA_GEOMETRY_FILE_NAME;
	
	private TempEntityManager entityManager = new TempEntityManager();
	private State currState;
	private Job currJob;
	
	@GET
    @Path("/{state}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleSelectState(@PathParam("state") String state, 
    		@Context HttpServletRequest req) throws FileNotFoundException, IOException, ParseException {
		HttpSession session = req.getSession(true);
		ObjectMapper mapper = new ObjectMapper();
		
		USState stateEnum = UserInputToEnumTransformer.transformUserStateToEnum(state);
		State currState = em.getState(stateEnum);
		
		this.currState = currState;
		session.setAttribute("currState", this.currState);
		
		try {
			String stateJSON = mapper.writeValueAsString(currState);
	        return Response.ok(stateJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
    }
	
	@GET
    @Path("/{state}/geojson")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleSendStateGeoJson(@PathParam("state") String state) {
		String stateGeoJsonFilePath = UserInputToEnumTransformer.transformUserStateToStateGeoJsonFilePath(state);
		ObjectMapper objectMapper = new ObjectMapper();
		
		Object object;
		try {
			object = new JSONParser().parse(new FileReader(stateGeoJsonFilePath));
		} catch (Exception e1) {
			e1.printStackTrace();
			return Response.serverError().build();
		}
		
		JSONObject jsonObject = (JSONObject) object;
		try {
			String stateGeoJSON = objectMapper.writeValueAsString(jsonObject);
	        return Response.ok(stateGeoJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
    }
	
	//TODO
	@GET
    @Path("/job/{jobID}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleJob(@PathParam("jobID") String jobID, @Context HttpServletRequest req) {
		HttpSession session = req.getSession(true);
		this.currState = new State(null, null, null);
		this.currState.setCurrState(USState.NV);
		session.setAttribute("job", this.currState);
		
        String greeting = "Job selected: " + jobID;
        return Response.ok(greeting).build();
    }
	
	@GET
    @Path("/constrainJob/{minority}")
	@Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleConstrainDistrictings(@PathParam("minority") String input,
    		@Context HttpServletRequest req) throws FileNotFoundException, IOException, ParseException {
		
		HttpSession session = req.getSession();

		MinorityPopulation minority = UserInputToEnumTransformer.transformUserMinorityPopToEnum(input);
		State currState = (State) session.getAttribute("currState");
		Districting enactedDistricting = currState.getEnactedDistricting();
        Job testJob = new Job();
        testJob.setCurrMinorityPopulation(minority);
        testJob.setEnactedDistricting(enactedDistricting);
        testJob.generatePrecinctPopulationMap(NEVADA_GEO_FILE);
        testJob.fillDistrictings();
        testJob.renumberDistrictings();
        testJob.generateBoxAndWhiskerData();
        
        this.currJob = testJob;
        session.setAttribute("currJob", this.currJob);
		
        // TODO - random number now; change once constrain() is implemented
		int numberDistrictingsLeft = 900 + (int)(Math.random() * 150);
		ObjectMapper mapper = new ObjectMapper();
		try {
			String numLeft = mapper.writeValueAsString(numberDistrictingsLeft);
	        return Response.ok(numLeft).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
    }
	
	@GET
    @Path("/constrain/{minority}")
	@Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleConstrainJob(@PathParam("minority") String input,
    		@Context HttpServletRequest req) throws FileNotFoundException, IOException, ParseException {
		
        Job testJob = new Job();
		
		long startTime = System.nanoTime();	
		
		int counter = 0;
		String jobFileName = Constants.YOUR_DIRECTORY_PREFIX + Constants.SC_JOB_90000_FILE_NAME;
	    try (
	            InputStream inputStream = Files.newInputStream(java.nio.file.Path.of(jobFileName));
	            JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
	    ) {            
	        reader.beginObject();
	        reader.nextName();
	        reader.beginArray();
	        while (reader.hasNext()) {
	        	JsonObject plan = new Gson().fromJson(reader, JsonObject.class);
	        	boolean validPlan = testJob.constrain(0.10, "totalPopulationScore", 0.03, 0.20, 5, "BPERCENTAGE", Arrays.asList(4,6,7), plan);
	        	if (validPlan) {
		        	counter++;
	        	}
	        }
	        reader.endArray();
	    } finally {
		}


		long endTime = System.nanoTime();
		long duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
		System.out.println(duration / 1000);

		// Rest of constrain logic here -- i.e fillDistrictings, gill, box and whisker, etc.
		
		int numberDistrictingsLeft = counter;
		ObjectMapper mapper = new ObjectMapper();
		try {
			String numLeft = mapper.writeValueAsString(numberDistrictingsLeft);
	        return Response.ok(numLeft).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
    }
	
	// TODO - not implemented yet
	@GET
    @Path("/measures")
	//@Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response handleSetMeasures(String jsonInput, @Context HttpServletRequest req) throws JsonGenerationException, JsonMappingException, IOException {
//		HttpSession session = req.getSession(true);
//		State sesh = (State) session.getAttribute("job");	
//		ObjectMapper mapper = new ObjectMapper();
//		String stateInfo = mapper.writeValueAsString(sesh);
		
		em.createJob();
        String greeting = "Measures selected: ";
        return Response.ok(greeting).build();
	}
	
	@GET
    @Path("/districting/{districtingIndex}/boxAndWhisker")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleGetBoxAndWhisker(@PathParam("districtingIndex") String input, @Context HttpServletRequest req) {
		HttpSession session = req.getSession();
		Job job = (Job) session.getAttribute("currJob");
		BoxAndWhisker boxAndWhisker = job.getBoxAndWhisker();
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			String boxAndWhiskerJSON = mapper.writeValueAsString(boxAndWhisker);
	        return Response.ok(boxAndWhiskerJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}
	
	@GET
    @Path("/districting/{districtingIndex}/display")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleDisplayDistricting(@PathParam("districtingIndex") String index, @Context HttpServletRequest req) {
        HttpSession session = req.getSession();
		Job job = (Job) session.getAttribute("currJob");
		
		try {
			String responseJSON = job.generateDistrictingGeometry(Integer.parseInt(index));
	        return Response.ok(responseJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}
	
	@GET
    @Path("/districting/{districtingIndex}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleGetDistricting(@PathParam("districtingIndex") String index, 
    		@Context HttpServletRequest req) {
		
		HttpSession session = req.getSession();
		Job job = (Job) session.getAttribute("currJob");
		Districting districting = job.getDistrictingAtIndex(Integer.parseInt(index));
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			String districtingJSON = mapper.writeValueAsString(districting);
	        return Response.ok(districtingJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}
	
	@GET
    @Path("/gillConstruct/{districtingIndex}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleGillConstruct(@PathParam("districtingIndex") String index, 
    		@Context HttpServletRequest req) {
		
		HttpSession session = req.getSession();
		Job job = (Job) session.getAttribute("currJob");
		State currState = (State) session.getAttribute("currState");
		Districting enactedDistricting = currState.getEnactedDistricting();
		Districting districting = job.getDistrictingAtIndex(Integer.parseInt(index));
		
		districting.gillConstructRenumbering(enactedDistricting);		
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			String districtingJSON = mapper.writeValueAsString(districting);
	        return Response.ok(districtingJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}
	
	@GET
    @Path("/megaTest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response megaTest(@PathParam("districtingIndex") String index, 
    		@Context HttpServletRequest req) throws FileNotFoundException, IOException, ParseException {
		
		State stateObject = new State();
		stateObject.setCurrState(USState.NV);
		stateObject.generateEnactedDistricting(NEVADA_ENACTED_FILE, NEVADA_GEO_FILE);
		
		Districting enactedDistricting = stateObject.getEnactedDistricting();
        Job testJob = new Job();
        testJob.setCurrMinorityPopulation(MinorityPopulation.HISPANIC);
        testJob.setEnactedDistricting(enactedDistricting);
        testJob.generatePrecinctPopulationMap(NEVADA_GEO_FILE);
        testJob.fillDistrictings();
        testJob.renumberDistrictings();
        testJob.generateBoxAndWhiskerData();
        testJob.findAverageDistricting();
        testJob.generateDistrictingAnalysisSummary();
        DistrictingAnalysisSummary testSummary = testJob.getDistrictingAnalysisSummary();
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			String testJSON = mapper.writeValueAsString(testSummary);
	        return Response.ok(testJSON).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}
	
	@GET
    @Path("/deviationTest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deviationTest(@Context HttpServletRequest req) throws FileNotFoundException, IOException, ParseException {
		
		State stateObject = new State();
		stateObject.setCurrState(USState.NV);
		stateObject.generateEnactedDistricting(NEVADA_ENACTED_FILE, NEVADA_GEO_FILE);
		
		Districting enactedDistricting = stateObject.getEnactedDistricting();
        Job testJob = new Job();
        testJob.setCurrMinorityPopulation(MinorityPopulation.ASIAN);
        testJob.setEnactedDistricting(enactedDistricting);
        testJob.generatePrecinctPopulationMap(NEVADA_GEO_FILE);
        testJob.fillDistrictings();
        testJob.renumberDistrictings();
        testJob.generateBoxAndWhiskerData();
        testJob.findAverageDistricting();	
        
        System.out.println("**************************************************************");
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			testJob.getDistrictingAtIndex(0).calculateEnactedAreaScore(enactedDistricting);
			testJob.getDistrictingAtIndex(0).calculateEnactedPopScore(enactedDistricting);
			ObjectiveFunction objFunc = testJob.getDistrictingAtIndex(0).getObjectivefunction();
			String testJSON = mapper.writeValueAsString(new ObjectiveFunction(0, 0, 0, 0, 0, 0, null));
	        return Response.ok("yo").build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}
}

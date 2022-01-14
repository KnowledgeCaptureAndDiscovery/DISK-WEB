package org.diskproject.server.api.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.diskproject.server.repository.DiskRepository;
import org.diskproject.server.repository.WingsAdapter;
import org.diskproject.shared.api.DiskService;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.question.Question;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.Workflow;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.fasterxml.jackson.annotation.JsonProperty;

@Path("")
@Produces("application/json")
@Consumes("application/json")
public class DiskResource implements DiskService {

  private static String USERNAME = "admin", DOMAIN = "test";

  @Context
  HttpServletResponse response;
  @Context
  HttpServletRequest request;
  @Context
  SecurityContext securityContext;
  
  DiskRepository repo;
  
  public DiskResource() {
    this.repo = DiskRepository.get(); 
  }
  
  @GET
  @Path("server/config")
  @Override
  public Map<String, String> getConfig() {
    String username = (String) request.getAttribute("username");
    System.out.println( "user: " + username);

    try {
      PropertyListConfiguration config = this.repo.getConfig();
      Map<String, String> vals = new HashMap<String, String>();
      vals.put("username", USERNAME);
      vals.put("domain", DOMAIN);
      vals.put("wings.server", config.getProperty("wings.server").toString());
      return vals;
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RuntimeException("Exception: " + e.getMessage());
    }
  }

  @GET
  @Path("server/endpoints")
  @Override
  public Map<String, String> getEndpoints () {
      return this.repo.getEndpoints();
  }
  
  /*
   * Vocabulary
   */
  @GET
  @Path("vocabulary")
  @Override
  public Map<String, Vocabulary> getVocabularies() {
    try {
      return this.repo.getVocabularies();
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RuntimeException("Exception: " + e.getMessage());
    }
  }

  /*@GET
  @Path("vocabulary")
  @Override
  public Vocabulary getUserVocabulary(
      @PathParam("username") String username, 
      @PathParam("domain") String domain) {
    try {
      return this.repo.getUserVocabulary(username, domain);
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RuntimeException("Exception: " + e.getMessage());
    }
  }*/

  @GET
  @Path("vocabulary/reload")
  @Produces("text/html")
  @Override
  public String reloadVocabularies() {
    try {
      this.repo.reloadKBCaches();
      //this.repo.initializeKB();
      response.sendRedirect("");
      return "";
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RuntimeException("Exception: " + e.getMessage());
    }
  }
  
  @GET
  @Path("vocabulary/reload")
  public String APIReloadVocabularies() {
    try {
      this.repo.reloadKBCaches();
      //this.repo.initializeKB();
      return "OK";
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RuntimeException("Exception: " + e.getMessage());
    }
  }
    
    
  /**
   * Hypothesis
   */
  @POST
  @Path("hypotheses")
  @Override
  public void addHypothesis(
      @JsonProperty("hypothesis") Hypothesis hypothesis) {
    this.repo.addHypothesis(USERNAME, DOMAIN, hypothesis);
  }
      
  @GET
  @Path("hypotheses")
  @Override
  public List<TreeItem> listHypotheses() {
    return this.repo.listHypotheses(USERNAME, DOMAIN);
  }
  
  @GET
  @Path("hypotheses/{id}")
  @Override
  public Hypothesis getHypothesis(
      @PathParam("id") String id) {
    return this.repo.getHypothesis(USERNAME, DOMAIN, id);
  }
  
  @PUT
  @Path("hypotheses/{id}")
  @Override
  public void updateHypothesis(
      @PathParam("id") String id,
      @JsonProperty("hypothesis") Hypothesis hypothesis) {
    this.repo.updateHypothesis(USERNAME, DOMAIN, id, hypothesis);
  }
  
  @DELETE
  @Path("hypotheses/{id}")
  @Override
  public void deleteHypothesis(
      @PathParam("id") String id) {
    this.repo.deleteHypothesis(USERNAME, DOMAIN, id);
  }
  
  @GET
  @Path("hypotheses/{id}/query")
  @Override
  public List<TriggeredLOI> queryHypothesis(
      @PathParam("id") String id) {
    return this.repo.queryHypothesis(USERNAME, DOMAIN, id);
  }

  @GET
  @Path("hypotheses/{id}/tlois")
  @Override
  public Map<String, List<TriggeredLOI>> getHypothesisTLOIs(
      @PathParam("id") String id) {
    return this.repo.getHypothesisTLOIs(USERNAME, DOMAIN, id);
  }

  /**
   * Assertions
   */
  @POST
  @Path("assertions")
  @Override
  public void addAssertion(
      @JsonProperty("assertions") Graph assertions) {
    this.repo.addAssertion(USERNAME, DOMAIN, assertions);
  }
  
  @GET
  @Path("assertions")
  @Override
  public Graph listAssertions() {
    return this.repo.listAssertions(USERNAME, DOMAIN);
  }
  
  @DELETE
  @Path("assertions")
  @Override
  public void deleteAssertion(
      @JsonProperty("assertions") Graph assertions) {
    this.repo.deleteAssertion(USERNAME, DOMAIN, assertions);
  }
  
  @PUT
  @Path("assertions")
  @Override
  public void updateAssertions (
      @JsonProperty("assertions") Graph assertions) {
    this.repo.updateAssertions(USERNAME, DOMAIN, assertions);
  }
  
  /**
   * Lines of Inquiry
   */
  @POST
  @Path("lois")
  @Override
  public void addLOI(
      @JsonProperty("loi") LineOfInquiry loi) {
    this.repo.addLOI(USERNAME, DOMAIN, loi);
  }

  @GET
  @Path("lois")
  @Override
  public List<TreeItem> listLOIs() {
    return this.repo.listLOIs(USERNAME, DOMAIN);
  }

  @GET
  @Path("lois/{id}")
  @Override
  public LineOfInquiry getLOI(
      @PathParam("id") String id) {
    return this.repo.getLOI(USERNAME, DOMAIN, id);
  }

  @PUT
  @Path("lois/{id}")
  @Override
  public void updateLOI(
      @PathParam("id") String id,
      @JsonProperty("loi") LineOfInquiry loi) {
    this.repo.updateLOI(USERNAME, DOMAIN, id, loi);
  }
  
  @DELETE
  @Path("lois/{id}")
  @Override
  public void deleteLOI(
      @PathParam("id") String id) {
    this.repo.deleteLOI(USERNAME, DOMAIN, id);
  }
  
  /*
   * Triggered LOIs
   */
  @POST
  @Path("tlois")
  @Override
  public void addTriggeredLOI(
      @JsonProperty("tloi") TriggeredLOI tloi) {
    this.repo.addTriggeredLOI(USERNAME, DOMAIN, tloi);
  }
  
  @GET
  @Path("tlois")
  @Override
  public List<TriggeredLOI> listTriggeredLOIs() {
    return this.repo.listTriggeredLOIs(USERNAME, DOMAIN);
  }
  
  @GET
  @Path("tlois/{id}")
  @Override
  public TriggeredLOI getTriggeredLOI(
      @PathParam("id") String id) {
    return this.repo.getTriggeredLOI(USERNAME, DOMAIN, id);
  }
  
  @DELETE
  @Path("tlois/{id}")
  @Override
  public void deleteTriggeredLOI(
      @PathParam("id") String id) {
    this.repo.deleteTriggeredLOI(USERNAME, DOMAIN, id);
  }
 
  /*
   * Workflows
   */
  @GET
  @Override
  @Path("workflows")
  public List<Workflow> listWorkflows() {
    return WingsAdapter.get().getWorkflowList(USERNAME, DOMAIN);
  }

  @GET
  @Override
  @Path("workflows/{id}")
  public List<Variable> getWorkflowVariables( @PathParam("id") String id) {
    return WingsAdapter.get().getWorkflowVariables(USERNAME, DOMAIN, id);    
  }
  
  @GET
  @Override
  @Path("runs/{id}")
  public WorkflowRun monitorWorkflow(
      @PathParam("id") String id) {
    // Check execution status
    return WingsAdapter.get().getWorkflowRunStatus(USERNAME, DOMAIN, id);
  }  

  @GET
  @Path("externalQuery")
  @Override
  public Map<String, List<String>> queryExternalStore(
      @QueryParam("endpoint") String endpoint,
      @QueryParam("variables") String variables,
      @QueryParam("query") String query) {
	return repo.queryExternalStore(USERNAME, DOMAIN, endpoint, variables, query);
  }
  
  
  /*
   * Question
   */
  @GET
  @Path("questions")
  @Override
  public List<Question> listQuestions() {
    return this.repo.listHypothesesQuestions();
  }

  @GET
  @Path("question/{id}/options")
  @Override
  public List<List<String>> listOptions(
	  @PathParam("id") String id) {
    return this.repo.listVariableOptions(id);
  };

  /*
   * CUSTOM
   */
  @GET
  @Path("executions/{hid}/{lid}")
  @Override
  public List<TriggeredLOI> getExecutions(
      @PathParam("hid") String hid, 
      @PathParam("lid") String lid) {
	return this.repo.getTLOIsForHypothesisAndLOI(USERNAME, DOMAIN, hid, lid);
  }

  @GET
  @Path("execute/{hid}/{lid}")
  @Override
  public List<TriggeredLOI> runHypothesisAndLOI(
      @PathParam("hid") String hid, 
      @PathParam("lid") String lid) {
	return this.repo.runHypothesisAndLOI(USERNAME, DOMAIN, hid, lid);
  }
  
  
  @GET
  @Path("runhypotheses")
  @Override
  public Boolean runHypotheses() {
	return this.repo.runAllHypotheses(USERNAME, DOMAIN);
  }

  @GET
  @Path("tloi/{tloiid}/narratives")
  @Override
  public Map<String, String> getNarratives(
      @PathParam("tloiid") String tloiid) {
	return this.repo.getNarratives(USERNAME, DOMAIN, tloiid);
  }
  
  @GET
  @Path("wings-data/{dataid}")
  @Produces("application/json")
  @Override
  public String getDataFromWings(
      @PathParam("dataid") String dataid) {
	String result = this.repo.getDataFromWings(USERNAME, DOMAIN, dataid);
	if (result == null) {
		System.out.println("ERROR: " + dataid + " not available on WINGS.");
		result = "";
	} 
	return result;
  }
  
}
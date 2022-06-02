package org.diskproject.server.repository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.common.TripleDetails;
import org.diskproject.shared.classes.common.Value;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.util.GUID;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;
import org.diskproject.shared.ontologies.DISK;

import edu.isi.kcap.ontapi.KBAPI;
import edu.isi.kcap.ontapi.KBObject;
import edu.isi.kcap.ontapi.KBTriple;
import edu.isi.kcap.ontapi.OntSpec;

public class WriteKBRepository extends KBRepository {
    protected String _domain;

    private static SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    public void setDomain (String url) {
        this._domain = url;
    }

    public String DOMURI (String username) {
        return this._domain + "/" + username;
    }

    public String HYPURI (String username) {
        return this.DOMURI(username) + "/hypotheses";
    }

    public String LOIURI (String username) {
        return this.DOMURI(username) + "/lois";
    }

    public String TLOIURI (String username) {
        return this.DOMURI(username) + "/tlois";
    }

    public String ASSERTIONSURI (String username) {
        return this.DOMURI(username) + "/assertions";
    }
    
    private KBAPI getOrCreateKB (String url) {
        KBAPI kb = null;
        try {
            kb = this.fac.getKB(url, OntSpec.PLAIN, true);
        } catch (Exception e) {
            System.err.print("Could not open KB: " + url);
        }
        return kb;
    }

    private KBAPI getKB (String url) {
        KBAPI kb = null;
        try {
            kb = this.fac.getKB(url, OntSpec.PLAIN, false);
        } catch (Exception e) {
            System.err.print("Could not open KB: " + url);
        }
        return kb;
    }

    private KBObject getKBValue (Value v, KBAPI kb) {
        if (v.getType() == Value.Type.LITERAL) {
            if (v.getDatatype() != null)
                return kb.createXSDLiteral(v.getValue().toString(), v.getDatatype());
            else
                return kb.createLiteral(v.getValue());
        } else {
            return kb.getResource(v.getValue().toString());
        }
    }

    private KBTriple getKBTriple(Triple triple, KBAPI kb) {
        KBObject subj = kb.getResource(triple.getSubject());
        KBObject pred = kb.getResource(triple.getPredicate());
        KBObject obj = getKBValue(triple.getObject(), kb);

        if (subj != null && pred != null && obj != null)
            return this.fac.getTriple(subj, pred, obj);
        return null;
    }

    private void setKBStatement(Triple triple, KBAPI kb, KBObject st) {
        KBObject subj = kb.getResource(triple.getSubject());
        KBObject pred = kb.getResource(triple.getPredicate());
        KBObject obj = getKBValue(triple.getObject(), kb);
        KBObject subprop = kb.getProperty(KBConstants.RDFNS() + "subject");
        KBObject predprop = kb.getProperty(KBConstants.RDFNS() + "predicate");
        KBObject objprop = kb.getProperty(KBConstants.RDFNS() + "object");
        kb.addTriple(st, subprop, subj);
        kb.addTriple(st, predprop, pred);
        kb.addTriple(st, objprop, obj);
    }

    //Deprecate this!
    private void storeTripleDetails(Triple triple, String provid, KBAPI provkb) {
        TripleDetails details = triple.getDetails();
        if (details != null) {
            KBObject stobj = provkb.getResource(provid + "#" + GUID.randomId("Statement"));
            this.setKBStatement(triple, provkb, stobj);

            if (details.getConfidenceValue() > 0)
                provkb.setPropertyValue(stobj, DISKOnt.getProperty(DISK.HAS_CONFIDENCE_VALUE),
                        provkb.createLiteral(triple.getDetails().getConfidenceValue()));
            if (details.getTriggeredLOI() != null)
                provkb.setPropertyValue(stobj, DISKOnt.getProperty(DISK.HAS_TLOI),
                        provkb.getResource(triple.getDetails().getTriggeredLOI()));
        }
    }

    private Graph updateTripleDetails(Graph graph, KBAPI provkb) {
        HashMap<String, Triple> tripleMap = new HashMap<String, Triple>();
        for (Triple t : graph.getTriples())
            tripleMap.put(t.toString(), t);

        KBObject subprop = provkb.getProperty(KBConstants.RDFNS() + "subject");
        KBObject predprop = provkb.getProperty(KBConstants.RDFNS() + "predicate");
        KBObject objprop = provkb.getProperty(KBConstants.RDFNS() + "object");

        for (KBTriple kbt : provkb.genericTripleQuery(null, subprop, null)) {
            KBObject stobj = kbt.getSubject();
            KBObject subjobj = kbt.getObject();
            KBObject predobj = provkb.getPropertyValue(stobj, predprop);
            KBObject objobj = provkb.getPropertyValue(stobj, objprop);

            Value value = this.getObjectValue(objobj);
            Triple triple = new Triple();
            triple.setSubject(subjobj.getID());
            triple.setPredicate(predobj.getID());
            triple.setObject(value);

            String triplestr = triple.toString();
            if (tripleMap.containsKey(triplestr)) {
                Triple t = tripleMap.get(triplestr);

                KBObject conf = provkb.getPropertyValue(stobj, DISKOnt.getProperty(DISK.HAS_CONFIDENCE_VALUE));
                KBObject tloi = provkb.getPropertyValue(stobj, DISKOnt.getProperty(DISK.HAS_TLOI));

                TripleDetails details = new TripleDetails();
                if (conf != null && conf.getValue() != null)
                    details.setConfidenceValue((Double) conf.getValue());
                if (tloi != null)
                    details.setTriggeredLOI(tloi.getID());

                t.setDetails(details);
            }
        }
        return graph;
    }

    private Value getObjectValue(KBObject obj) {
        Value v = new Value();
        if (obj.isLiteral()) {
            Object valobj = obj.getValue();
            if (valobj instanceof Date) {
                valobj = dateformatter.format((Date) valobj);
            }
            if (valobj instanceof String) {
                //Fix quotes and \n
                valobj = ((String) valobj).replace("\"", "\\\"").replace("\n", "\\n");
            }
            v.setType(Value.Type.LITERAL);
            v.setValue(valobj);
            v.setDatatype(obj.getDataType());
        } else {
            v.setType(Value.Type.URI);
            v.setValue(obj.getID());
        }
        return v;
    }

    private Graph getKBGraph(String url) {
        Graph graph = new Graph();
        KBAPI kb = getKB(url);
        if (kb == null)
            return null;

        for (KBTriple t : kb.genericTripleQuery(null, null, null)) {
            Value value = this.getObjectValue(t.getObject());
            Triple triple = new Triple();
            triple.setSubject(t.getSubject().getID());
            triple.setPredicate(t.getPredicate().getID());
            triple.setObject(value);
            graph.addTriple(triple);
        }
        return graph;
    }
    
    //--- Hypothesis
    protected boolean writeHypothesis (String username, Hypothesis hypothesis) {
        Boolean newHypothesis = hypothesis.getId() == null;
        if (newHypothesis) {
            hypothesis.setId(GUID.randomId("Hypothesis"));
        }
        
        String userDomain = this.HYPURI(username);
        String hypothesisId = userDomain + "/" + hypothesis.getId();

        KBAPI userKB = getOrCreateKB(userDomain);
        
        if (userKB == null) return false;
        this.start_write();

        // Insert hypothesis info on user's graph
        KBObject hypitem = userKB.createObjectOfClass(hypothesisId, DISKOnt.getClass(DISK.HYPOTHESIS));
        
        if (hypothesis.getName() != null)
            userKB.setLabel(hypitem, hypothesis.getName());
        if (hypothesis.getDescription() != null)
            userKB.setComment(hypitem, hypothesis.getDescription());
        if (hypothesis.getDateCreated() != null)
            userKB.setPropertyValue(hypitem, DISKOnt.getProperty(DISK.DATE_CREATED), userKB.createLiteral(hypothesis.getDateCreated()));
        if (hypothesis.getDateModified() != null)
            userKB.setPropertyValue(hypitem, DISKOnt.getProperty(DISK.DATE_MODIFIED), userKB.createLiteral(hypothesis.getDateModified()));
        if (hypothesis.getAuthor() != null)
            userKB.setPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_AUTHOR), userKB.createLiteral(hypothesis.getAuthor()));
        if (hypothesis.getNotes() != null)
            userKB.setPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_USAGE_NOTES), userKB.createLiteral(hypothesis.getNotes()));
        
        // Adding parent hypothesis ID 
        if (hypothesis.getParentId() != null) {
            String fullparentid = userDomain + "/" + hypothesis.getParentId();
            userKB.setPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS), userKB.getResource(fullparentid));
        }
        
        // Adding question template details
        if (hypothesis.getQuestion() != null)
            userKB.setPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_QUESTION), userKB.createLiteral(hypothesis.getQuestion()));
        List<VariableBinding> questionBindings = hypothesis.getQuestionBindings();
        if (questionBindings != null) {
            for (VariableBinding vb: questionBindings) {
                String ID = hypothesisId + "/bindings/";
                String[] sp = vb.getVariable().split("/");
                ID += sp[sp.length-1];
                KBObject binding = userKB.createObjectOfClass(ID, DISKOnt.getClass(DISK.VARIABLE_BINDING));
                userKB.setPropertyValue(binding, DISKOnt.getProperty(DISK.HAS_VARIABLE), userKB.createLiteral(vb.getVariable()));
                userKB.setPropertyValue(binding, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE), userKB.createLiteral(vb.getBinding()));
                userKB.addPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_VARIABLE_BINDING), binding);
            }
        }

        this.save(userKB);
        this.end();
        
        // Store hypothesis graph
        KBAPI hypkb = getOrCreateKB(hypothesisId);
        if (hypkb == null) return false;

        this.start_write();
        for (Triple triple : hypothesis.getGraph().getTriples()) {
                KBTriple t = this.getKBTriple(triple, hypkb);
                if (t != null)
                    hypkb.addTriple(t);
        }
        this.save(hypkb);
        this.end();

        //FIXME: remove this way of getting the p-value
        String hypotheisProv = hypothesisId + "/provenance";
        KBAPI provkb = getOrCreateKB(hypotheisProv);
        if (provkb == null) return false;

        this.start_write();
        for (Triple triple : hypothesis.getGraph().getTriples()) {
            // Add triple details (confidence value, provenance, etc)
            this.storeTripleDetails(triple, hypotheisProv, provkb);
        }
        this.save(provkb);
        this.end();

        return true;
    }
    
    protected Hypothesis loadHypothesis (String username, String id) {
        String userDomain = this.HYPURI(username);
        String hypothesisId = userDomain + "/" + id;

        KBAPI userKB = getKB(userDomain);
        if (userKB == null) return null;

        this.start_read();

        KBObject hypitem = userKB.getIndividual(hypothesisId);
        Graph graph = this.getKBGraph(hypothesisId);
        if (hypitem == null || graph == null) {
            this.end();
            return null;
        }
        
        Hypothesis hypothesis = new Hypothesis();
        hypothesis.setId(id);
        hypothesis.setName(userKB.getLabel(hypitem));
        hypothesis.setDescription(userKB.getComment(hypitem));
        hypothesis.setGraph(graph);

        KBObject dateobj = userKB.getPropertyValue(hypitem, DISKOnt.getProperty(DISK.DATE_CREATED));
        if (dateobj != null)
            hypothesis.setDateCreated(dateobj.getValueAsString());

        KBObject dateModifiedObj = userKB.getPropertyValue(hypitem, DISKOnt.getProperty(DISK.DATE_MODIFIED));
        if (dateModifiedObj != null)
            hypothesis.setDateModified(dateModifiedObj.getValueAsString());

        KBObject authorobj = userKB.getPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_AUTHOR));
        if (authorobj != null)
            hypothesis.setAuthor(authorobj.getValueAsString());

        KBObject notesobj = userKB.getPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_USAGE_NOTES));
        if (notesobj != null)
            hypothesis.setNotes(notesobj.getValueAsString());

        // Parent hypothesis ID
        KBObject parentobj = userKB.getPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS));
        if (parentobj != null)
            hypothesis.setParentId(parentobj.getName());
        
        // Question template info
        KBObject questionobj = userKB.getPropertyValue(hypitem, DISKOnt.getProperty(DISK.HAS_QUESTION));
        if (questionobj != null)
            hypothesis.setQuestion(questionobj.getValueAsString());

        ArrayList<KBObject> questionBindings = userKB.getPropertyValues(hypitem, DISKOnt.getProperty(DISK.HAS_VARIABLE_BINDING));
        List<VariableBinding> variableBindings = new ArrayList<VariableBinding>();
        if (questionBindings != null) {
            for (KBObject binding: questionBindings) {
                KBObject kbvar = userKB.getPropertyValue(binding, DISKOnt.getProperty(DISK.HAS_VARIABLE));
                KBObject kbval = userKB.getPropertyValue(binding, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE));
                if (kbvar != null && kbval != null) {
                    String var = kbvar.getValueAsString();
                    String val = kbval.getValueAsString();
                    variableBindings.add( new VariableBinding(var, val));
                }
            }
        }
        hypothesis.setQuestionBindings(variableBindings);

        //FIXME: dont remember what this does.
        String provid = hypothesisId + "/provenance";
        KBAPI provkb = getKB(provid);
        if (provkb != null)
            this.updateTripleDetails(graph, provkb);
        
        this.end();
        return hypothesis;
    }
    
    protected boolean deleteHypothesis (String username, String id) {
        if (id == null)
            return false;

        String userDomain = this.HYPURI(username);
        String hypothesisId = userDomain + "/" + id;
        String provid = hypothesisId + "/provenance";

        KBAPI userKB = getKB(userDomain);
        KBAPI hypkb = getKB(hypothesisId);
        KBAPI provkb = getKB(provid);

        if (userKB != null && hypkb != null && provkb != null) {
            this.start_read();
            KBObject hypitem = userKB.getIndividual(hypothesisId);
            if (hypitem != null) {
                ArrayList<KBTriple> childHypotheses = userKB.genericTripleQuery(null, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS), hypitem);
                ArrayList<KBObject> questionBindings = userKB.getPropertyValues(hypitem, DISKOnt.getProperty(DISK.HAS_VARIABLE_BINDING));
                this.end();
                
                // Remove question template bindings
                this.start_write();
                if (questionBindings != null) for (KBObject binding: questionBindings) {
                    userKB.deleteObject(binding, true, true);
                }
                userKB.deleteObject(hypitem, true, true);
                this.save(userKB);
                this.end();

                // Remove all child hypotheses
                for (KBTriple t : childHypotheses) {
                    this.deleteHypothesis(username, t.getSubject().getName());
                }
            } else {
                this.end();
            }

            return this.start_write() && hypkb.delete() && this.save(hypkb) && this.end() && 
                this.start_write() && provkb.delete() && this.save(provkb) && this.end();
        }
        return false;
    }
    
    protected List<Hypothesis> listHypothesesPreviews (String username) {
        List<Hypothesis> list = new ArrayList<Hypothesis>();
        String userDomain = this.HYPURI(username);

        KBAPI userKB = getKB(userDomain);
        if (userKB != null) {
            this.start_read();
            KBObject hypcls = DISKOnt.getClass(DISK.HYPOTHESIS);
            KBObject typeprop = userKB.getProperty(KBConstants.RDFNS() + "type");
            for (KBTriple t : userKB.genericTripleQuery(null, typeprop, hypcls)) {
                KBObject hypobj = t.getSubject();
                String name = userKB.getLabel(hypobj);
                String description = userKB.getComment(hypobj);

                String parentid = null;
                KBObject parentobj = userKB.getPropertyValue(hypobj, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS));
                if (parentobj != null)
                    parentid = parentobj.getName();
                
                String dateCreated = null;
                KBObject dateobj = userKB.getPropertyValue(hypobj, DISKOnt.getProperty(DISK.DATE_CREATED));
                if (dateobj != null)
                    dateCreated = dateobj.getValueAsString();

                String dateModified = null;
                KBObject dateModifiedObj = userKB.getPropertyValue(hypobj, DISKOnt.getProperty(DISK.DATE_MODIFIED));
                if (dateModifiedObj != null)
                    dateModified = dateModifiedObj.getValueAsString();

                String author = null;
                KBObject authorobj = userKB.getPropertyValue(hypobj, DISKOnt.getProperty(DISK.HAS_AUTHOR));
                if (authorobj != null)
                    author = authorobj.getValueAsString();
                
                Hypothesis item = new Hypothesis(hypobj.getName(), name, description, parentid, null);
                if (dateCreated != null) item.setDateCreated(dateCreated);
                if (dateModified != null) item.setDateModified(dateModified);
                if (author != null) item.setAuthor(author);
        
                list.add(item);
            }
            this.end();
        }
        return list;
    }
    
    // -- Line of inquiry
    protected boolean writeLOI (String username, LineOfInquiry loi) {
        Boolean newLOI = loi.getId() == null;
        if (newLOI) {
            loi.setId(GUID.randomId("LOI"));
        }

        String userDomain = this.LOIURI(username);
        String loiId = userDomain + "/" + loi.getId();
        KBAPI userKB = getOrCreateKB(userDomain);
        
        if (userKB == null)
            return false;

        this.start_write();

        KBObject loiitem = userKB.createObjectOfClass(loiId, DISKOnt.getClass(DISK.LOI));
        if (loi.getName() != null)
            userKB.setLabel(loiitem, loi.getName());
        if (loi.getDescription() != null)
            userKB.setComment(loiitem, loi.getDescription());
        if (loi.getDateCreated() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.DATE_CREATED), userKB.createLiteral(loi.getDateCreated()));
        if (loi.getDateModified() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.DATE_MODIFIED), userKB.createLiteral(loi.getDateModified()));
        if (loi.getAuthor() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_AUTHOR), userKB.createLiteral(loi.getAuthor()));

        if (loi.getHypothesisQuery() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_HYPOTHESIS_QUERY), userKB.createLiteral(loi.getHypothesisQuery()));
        if (loi.getDataQuery() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_DATA_QUERY), userKB.createLiteral(loi.getDataQuery()));
        if (loi.getDataSource() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_DATA_SOURCE), userKB.createLiteral(loi.getDataSource()));
        if (loi.getNotes() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_USAGE_NOTES), userKB.createLiteral(loi.getNotes()));
        if (loi.getRelevantVariables() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_RELEVANT_VARIABLES), userKB.createLiteral(loi.getRelevantVariables()));
        if (loi.getQuestion() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_QUESTION), userKB.createLiteral(loi.getQuestion()));
        if (loi.getExplanation() != null)
            userKB.setPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_DATA_QUERY_DESCRIPTION), userKB.createLiteral(loi.getExplanation()));
        
        this.save(userKB);
        this.end();
        
        writeWorkflowsBindings(username, loi);
        writeMetaWorkflowsBindings(username, loi);
        
        return true;
    }
    
    protected LineOfInquiry loadLOI (String username, String id) {
        String userDomain = this.LOIURI(username);
        String loiId = userDomain + "/" + id;
        
        KBAPI userKB = getKB(userDomain);
        if (userKB == null)
            return null;
        
        this.start_read();
        KBObject loiitem = userKB.getIndividual(loiId);
        if (loiitem == null) {
            this.end();
            return null;
        }
        
        LineOfInquiry loi = new LineOfInquiry();
        loi.setId(id);
        loi.setName(userKB.getLabel(loiitem));
        loi.setDescription(userKB.getComment(loiitem));

        KBObject dateobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.DATE_CREATED));
        if (dateobj != null)
            loi.setDateCreated(dateobj.getValueAsString());

        KBObject datasourceobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_DATA_SOURCE));
        if (datasourceobj != null)
            loi.setDataSource(datasourceobj.getValueAsString());

        KBObject dateModifiedObj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.DATE_MODIFIED));
        if (dateModifiedObj != null)
            loi.setDateModified(dateModifiedObj.getValueAsString());

        KBObject authorobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_AUTHOR));
        if (authorobj != null)
            loi.setAuthor(authorobj.getValueAsString());

        KBObject notesobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_USAGE_NOTES));
        if (notesobj != null) {
            loi.setNotes(notesobj.getValueAsString());
        }

        KBObject rvarobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_RELEVANT_VARIABLES));
        if (rvarobj != null)
            loi.setRelevantVariables(rvarobj.getValueAsString());

        KBObject hqueryobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_HYPOTHESIS_QUERY));
        if (hqueryobj != null)
            loi.setHypothesisQuery(hqueryobj.getValueAsString());
        
        KBObject dqueryobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_DATA_QUERY));
        if (dqueryobj != null)
            loi.setDataQuery(dqueryobj.getValueAsString());

        KBObject questionobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_QUESTION));
        if (questionobj != null)
            loi.setQuestion(questionobj.getValueAsString());

        KBObject explobj = userKB.getPropertyValue(loiitem, DISKOnt.getProperty(DISK.HAS_DATA_QUERY_DESCRIPTION));
        if (explobj != null) {
            loi.setExplanation(explobj.getValueAsString());
        }
        
        this.end();

        loi.setWorkflows(loadWorkflowsBindings(userDomain, loi.getId()));
        loi.setMetaWorkflows(loadMetaWorkflowsBindings(userDomain, loi.getId()));

        return loi;
    }
    
    protected boolean deleteLOI (String username, String id) {
        if (id == null)
            return false;

        String userDomain = this.LOIURI(username);
        String loiId = userDomain + "/" + id;

        KBAPI userKB = getKB(userDomain);
        if (userKB == null)
            return false;
        
        this.start_write();
        KBObject hypitem = userKB.getIndividual(loiId);
        if (hypitem != null)
            userKB.deleteObject(hypitem, true, true);
        
        return this.save(userKB) && this.end();
    }
    
    protected List<LineOfInquiry> listLOIPreviews (String username) {
        List<LineOfInquiry> list = new ArrayList<LineOfInquiry>();
        String userDomain = this.LOIURI(username);
        KBAPI userKB = getKB(userDomain);
        
        if (userKB != null) {
            this.start_read();
            KBObject loicls = DISKOnt.getClass(DISK.LOI);
            KBObject typeprop = userKB.getProperty(KBConstants.RDFNS() + "type");
            for (KBTriple t : userKB.genericTripleQuery(null, typeprop, loicls)) {
                KBObject loiobj = t.getSubject();
                String name = userKB.getLabel(loiobj);
                String description = userKB.getComment(loiobj);

                KBObject dateobj = userKB.getPropertyValue(loiobj, DISKOnt.getProperty(DISK.DATE_CREATED));
                String dateCreated = (dateobj != null) ? dateobj.getValueAsString() : null;
                        
                KBObject dateModifiedObj = userKB.getPropertyValue(loiobj, DISKOnt.getProperty(DISK.DATE_MODIFIED));
                String dateModified = (dateModifiedObj != null) ? dateModifiedObj.getValueAsString() : null;

                KBObject authorobj = userKB.getPropertyValue(loiobj, DISKOnt.getProperty(DISK.HAS_AUTHOR));
                String author = (authorobj != null) ? authorobj.getValueAsString() : null;

                KBObject hqueryobj = userKB.getPropertyValue(loiobj, DISKOnt.getProperty(DISK.HAS_HYPOTHESIS_QUERY));
                String hypothesisQuery = (hqueryobj != null) ? hqueryobj.getValueAsString() : null;
            
                LineOfInquiry item = new LineOfInquiry(loiobj.getName(), name, description);
                if (dateCreated != null) item.setDateCreated(dateCreated);
                if (dateModified != null) item.setDateModified(dateModified);
                if (author != null) item.setAuthor(author);
                if (hypothesisQuery != null) item.setHypothesisQuery(hypothesisQuery);

                list.add(item);
            }
            this.end();
        }
        return list;
    }

    // -- Triggered Lines of Inquiry
    protected boolean writeTLOI (String username, TriggeredLOI tloi) {
        if (tloi.getId() == null)
            return false;

        String userDomain = this.TLOIURI(username);
        String tloiid = userDomain + "/" + tloi.getId();
        String hypns = this.HYPURI(username) + "/";
        String loins = this.LOIURI(username) + "/";

        KBAPI userKB = getOrCreateKB(userDomain);
        if (userKB == null)
            return false;
        
        this.start_write();
        KBObject tloiitem = userKB.createObjectOfClass(tloiid, DISKOnt.getClass(DISK.TLOI));

        if (tloi.getName() != null)
            userKB.setLabel(tloiitem, tloi.getName());
        if (tloi.getDescription() != null)
            userKB.setComment(tloiitem, tloi.getDescription());
        if (tloi.getDataSource() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_DATA_SOURCE), userKB.createLiteral(tloi.getDataSource()));
        if (tloi.getDateCreated() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.DATE_CREATED), userKB.createLiteral(tloi.getDateCreated()));
        if (tloi.getDateModified() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.DATE_MODIFIED), userKB.createLiteral(tloi.getDateModified()));
        if (tloi.getAuthor() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_AUTHOR), userKB.createLiteral(tloi.getAuthor()));
        if (tloi.getDataQuery() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_DATA_QUERY), userKB.createLiteral(tloi.getDataQuery()));
        if (tloi.getRelevantVariables() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_RELEVANT_VARIABLES), userKB.createLiteral(tloi.getRelevantVariables()));
        if (tloi.getExplanation() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_DATA_QUERY_DESCRIPTION), userKB.createLiteral(tloi.getExplanation()));
        if (tloi.getConfidenceValue() > 0)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_CONFIDENCE_VALUE), userKB.createLiteral(Double.toString(tloi.getConfidenceValue())));
        if (tloi.getStatus() != null)
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_TLOI_STATUS), userKB.createLiteral(tloi.getStatus().toString()));
        
        List<String> inputList = tloi.getInputFiles();
        if (inputList != null && inputList.size() > 0) {
            for (String inputurl: inputList) {
                userKB.addPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_INPUT_FILE), userKB.createLiteral(inputurl));
            }
        }

        List<String> outputList = tloi.getOutputFiles();
        if (outputList != null && outputList.size() > 0) {
            for (String outputurl: outputList) {
                userKB.addPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_OUTPUT_FILE), userKB.createLiteral(outputurl));
            }
        }
        
        if (tloi.getLoiId() != null) {
            KBObject lobj = userKB.getResource(loins + tloi.getLoiId());
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_LOI), lobj);
        }
        if (tloi.getParentHypothesisId() != null) {
            KBObject hobj = userKB.getResource(hypns + tloi.getParentHypothesisId());
            userKB.setPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS), hobj);
        }
        for (String hypid : tloi.getResultingHypothesisIds()) {
            KBObject hobj = userKB.getResource(hypns + hypid);
            userKB.addPropertyValue(tloiitem, DISKOnt.getProperty(DISK.HAS_RESULTING_HYPOTHESIS), hobj);
        }

        this.save(userKB);
        this.end();
        
        writeWorkflowsBindings(username, tloi);
        writeMetaWorkflowsBindings(username, tloi);

        return true;
    }

    protected TriggeredLOI loadTLOI (String username, String id) {
        String userDomain = this.TLOIURI(username);
        String tloiId = userDomain + "/" + id;

        KBAPI userKB = getKB(userDomain);
        if (userKB == null)
            return null;

        this.start_read();
        KBObject obj = userKB.getIndividual(tloiId);
        if (obj != null && obj.getName() != null) {
            TriggeredLOI tloi = new TriggeredLOI();
            tloi.setId(obj.getName());
            tloi.setName(userKB.getLabel(obj));
            tloi.setDescription(userKB.getComment(obj));
            KBObject hasLOI = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_LOI));
            if (hasLOI != null)
                tloi.setLoiId(hasLOI.getName());

            KBObject pobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS));
            if (pobj != null)
                tloi.setParentHypothesisId(pobj.getName());

            KBObject stobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_TLOI_STATUS));
            if (stobj != null)
                tloi.setStatus(Status.valueOf(stobj.getValue().toString()));

            KBObject dateobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.DATE_CREATED));
            if (dateobj != null)
                tloi.setDateCreated(dateobj.getValueAsString());
            
            KBObject dateModifiedObj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.DATE_MODIFIED));
            if (dateModifiedObj != null)
                tloi.setDateModified(dateModifiedObj.getValueAsString());
            
            KBObject authorobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_AUTHOR));
            if (authorobj != null)
                tloi.setAuthor(authorobj.getValueAsString());

            KBObject dqobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_DATA_QUERY));
            if (dqobj != null)
                tloi.setDataQuery(dqobj.getValueAsString());
            
            KBObject dataSourceObj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_DATA_SOURCE));
            if (dataSourceObj != null)
                tloi.setDataSource(dataSourceObj.getValueAsString());

            KBObject rvobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_RELEVANT_VARIABLES));
            if (rvobj != null)
                tloi.setRelevantVariables(rvobj.getValueAsString());

            KBObject explobj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_DATA_QUERY_DESCRIPTION));
            if (explobj != null)
                tloi.setExplanation(explobj.getValueAsString());
            
            KBObject confidenceObj = userKB.getPropertyValue(obj, DISKOnt.getProperty(DISK.HAS_CONFIDENCE_VALUE));
            if (confidenceObj != null)
                tloi.setConfidenceValue(Double.valueOf(confidenceObj.getValueAsString()));

            for (KBObject robj : userKB.getPropertyValues(obj, DISKOnt.getProperty(DISK.HAS_RESULTING_HYPOTHESIS))) {
                String resHypId = robj.getName();
                tloi.addResultingHypothesisId(resHypId);
            }

            ArrayList<KBObject> inputFilesObj = userKB.getPropertyValues(obj, DISKOnt.getProperty(DISK.HAS_INPUT_FILE));
            if (inputFilesObj != null && inputFilesObj.size() > 0) {
                for (KBObject inputf: inputFilesObj) {
                    tloi.addInputFile(inputf.getValueAsString());
                }
            }

            ArrayList<KBObject> outputFilesObj = userKB.getPropertyValues(obj, DISKOnt.getProperty(DISK.HAS_OUTPUT_FILE));
            if (outputFilesObj != null && outputFilesObj.size() > 0) {
                for (KBObject outputf: outputFilesObj) {
                    tloi.addOutputFile(outputf.getValueAsString());
                }
            }
            this.end();
            
            tloi.setWorkflows(loadWorkflowsBindings(userDomain, tloi.getId() ));
            tloi.setMetaWorkflows(loadMetaWorkflowsBindings(userDomain, tloi.getId()));
            
            return tloi;
        } else {
            this.end();
            return null;
        }
    }
    
    protected boolean deleteTLOI (String username, String id) {
        if (id == null)
            return false;

        String userDomain = this.TLOIURI(username);
        String tloiId = userDomain + "/" + id; 
        
        KBAPI userKB = getKB(userDomain);
        if (userKB == null)
            return false;

        // Remove resulting hypothesis if is not being used
        this.start_read();
        KBObject item = userKB.getIndividual(tloiId);
        KBObject hypobj = userKB.getPropertyValue(item, DISKOnt.getProperty(DISK.HAS_RESULTING_HYPOTHESIS));

        if (item != null && hypobj != null) {
            List<KBTriple> alltlois = userKB.genericTripleQuery(null, DISKOnt.getProperty(DISK.HAS_PARENT_HYPOTHESIS), hypobj);
            this.end();
            if (alltlois != null && alltlois.size() == 1) {
                this.deleteHypothesis(username, hypobj.getName());
            } else {
                System.out.println("Resulting hypotesis cannot be deleted as is being used for other tloi.");
            }
        } else {
          this.end();
        }

        // Remove this TLOI
        if (item != null) {
            this.start_write();
            userKB.deleteObject(item, true, true);
            return this.save(userKB) && this.end();
        } else {
            return false;
        }
    }
    
    protected List<TriggeredLOI> listTLOIs (String username) {
        List<TriggeredLOI> list = new ArrayList<TriggeredLOI>();
        String userDomain = this.TLOIURI(username);
        KBAPI userKB = getKB(userDomain);
        
        if (userKB != null) {
            List<String> tloiIds = new ArrayList<String>();

            this.start_read();
            KBObject cls = DISKOnt.getClass(DISK.TLOI);
            KBObject typeprop = userKB.getProperty(KBConstants.RDFNS() + "type");

            for (KBTriple t :  userKB.genericTripleQuery(null, typeprop, cls)) {
                tloiIds.add(t.getSubject().getID());
            }
            this.end();
            
            for (String tloiId: tloiIds) {
                TriggeredLOI tloi = loadTLOI(username, tloiId.replaceAll("^.*\\/", ""));
                if (tloi != null)
                    list.add(tloi);
            }
        }
        return list;
    }
    
    // -- Workflows... or methods.
    private void writeWorkflowsBindings (String username, LineOfInquiry loi) {
        writeBindings(LOIURI(username), loi.getId(), DISKOnt.getProperty(DISK.HAS_WORKFLOW_BINDING), loi.getWorkflows());
    }

    private void writeWorkflowsBindings (String username, TriggeredLOI tloi) {
        writeBindings(TLOIURI(username), tloi.getId(), DISKOnt.getProperty(DISK.HAS_WORKFLOW_BINDING), tloi.getWorkflows());
    }

    private void writeMetaWorkflowsBindings (String username, LineOfInquiry loi) {
        writeBindings(LOIURI(username), loi.getId(), DISKOnt.getProperty(DISK.HAS_METAWORKFLOW_BINDING), loi.getMetaWorkflows());
    }

    private void writeMetaWorkflowsBindings (String username, TriggeredLOI tloi) {
        writeBindings(TLOIURI(username), tloi.getId(), DISKOnt.getProperty(DISK.HAS_METAWORKFLOW_BINDING), tloi.getMetaWorkflows());
    }
    
    private void writeBindings (String userDomain, String id, KBObject bindingprop, List<WorkflowBindings> bindingsList) {
        if (bindingsList == null || bindingsList.size() == 0)
            return;
        
        String fullId = userDomain + "/" + id;
        KBAPI userKB = getOrCreateKB(userDomain);
        
        if (userKB != null) {
            this.start_write();
            KBObject item = userKB.getIndividual(fullId);
            
            //FIXME: This part refers to WINGS workflows, we need to change to methods
            KBAPI kb = userKB;
            for (WorkflowBindings bindings : bindingsList) {
                String workflowid = WingsAdapter.get().WFLOWID(bindings.getWorkflow());
                String workflowuri = WingsAdapter.get().WFLOWURI(bindings.getWorkflow());
                KBObject bindingobj = kb.createObjectOfClass(null, DISKOnt.getClass(DISK.WORKFLOW_BINDING));
                kb.addPropertyValue(item, bindingprop, bindingobj);

                kb.setPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_WORKFLOW), kb.getResource(workflowid));

                // Get Run details
                if (bindings.getRun() != null) {
                    if (bindings.getRun().getId() != null)
                        kb.setPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_ID), kb.createLiteral(bindings.getRun().getId()));
                    if (bindings.getRun().getStatus() != null)
                        kb.setPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_STATUS), kb.createLiteral(bindings.getRun().getStatus()));
                    if (bindings.getRun().getLink() != null)
                        kb.setPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_RUN_LINK), kb.createLiteral(bindings.getRun().getLink()));
                }

                // Creating workflow data bindings
                for (VariableBinding vbinding : bindings.getBindings()) {
                    String varid = vbinding.getVariable();
                    String binding = vbinding.getBinding();
                    Value bindingValue = new Value(binding, KBConstants.XSDNS() + "string");
                    KBObject varbindingobj = kb.createObjectOfClass(null, DISKOnt.getClass(DISK.VARIABLE_BINDING));
                    kb.setPropertyValue(varbindingobj, DISKOnt.getProperty(DISK.HAS_VARIABLE), kb.getResource(workflowuri + "#" + varid));
                    kb.setPropertyValue(varbindingobj, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE), this.getKBValue(bindingValue, kb));
                    kb.addPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_VARIABLE_BINDING), varbindingobj);
                }
                
                // Creating parameters
                for (VariableBinding param : bindings.getParameters()) {
                    String varid = param.getVariable();
                    String binding = param.getBinding();
                    Value bindingValue = new Value(binding, KBConstants.XSDNS() + "string");
                    KBObject paramobj = kb.createObjectOfClass(null,  DISKOnt.getClass(DISK.VARIABLE_BINDING));
                    kb.setPropertyValue(paramobj, DISKOnt.getProperty(DISK.HAS_VARIABLE), kb.getResource(workflowuri + "#" + varid));
                    kb.setPropertyValue(paramobj, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE), this.getKBValue(bindingValue, kb));
                    kb.addPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_PARAMETER), paramobj);
                }

                // Creating optional parameters
                for (VariableBinding param : bindings.getOptionalParameters()) {
                    String varid = param.getVariable();
                    String binding = param.getBinding();
                    Value bindingValue = new Value(binding, KBConstants.XSDNS() + "string");
                    KBObject paramobj = kb.createObjectOfClass(null,  DISKOnt.getClass(DISK.VARIABLE_BINDING));
                    kb.setPropertyValue(paramobj, DISKOnt.getProperty(DISK.HAS_VARIABLE), kb.getResource(workflowuri + "#" + varid));
                    kb.setPropertyValue(paramobj, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE), this.getKBValue(bindingValue, kb));
                    kb.addPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_OPTIONAL_PARAMETER), paramobj);
                }

                String hypid = bindings.getMeta().getHypothesis();
                String revhypid = bindings.getMeta().getRevisedHypothesis();
                if (hypid != null)
                    kb.setPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_HYPOTHESIS_VARIABLE),
                            kb.getResource(workflowuri + "#" + hypid));
                if (revhypid != null)
                    kb.setPropertyValue(bindingobj, DISKOnt.getProperty(DISK.HAS_REVISED_HYPOTHESIS_VARIABLE),
                            kb.getResource(workflowuri + "#" + revhypid));
            }
            
            this.save(kb);
            this.end();
        }
    }

    
    private List<WorkflowBindings> loadWorkflowsBindings (String userDomain, String id) {
        return loadBindings(userDomain, id, DISKOnt.getProperty(DISK.HAS_WORKFLOW_BINDING));
    }

    private List<WorkflowBindings> loadMetaWorkflowsBindings (String userDomain, String id) {
        return loadBindings(userDomain, id, DISKOnt.getProperty(DISK.HAS_METAWORKFLOW_BINDING));
    }
    
    private List<WorkflowBindings> loadBindings (String userDomain, String id, KBObject bindingprop) {
        List<WorkflowBindings> list = new ArrayList<WorkflowBindings>();
        String loiId = userDomain + "/" + id;
        KBAPI kb = getOrCreateKB(userDomain);

        
        if (kb != null) {
            this.start_write();
            KBObject loiitem = kb.getIndividual(loiId);

            for (KBTriple t : kb.genericTripleQuery(loiitem, bindingprop, null)) {
                KBObject wbobj = t.getObject();
                WorkflowBindings bindings = new WorkflowBindings();

                // Workflow Run details
                WorkflowRun run = new WorkflowRun();
                KBObject robj = kb.getPropertyValue(wbobj, DISKOnt.getProperty(DISK.HAS_ID));
                if (robj != null)
                    run.setId(robj.getValue().toString());
                KBObject statusobj = kb.getPropertyValue(wbobj, DISKOnt.getProperty(DISK.HAS_STATUS));
                if (statusobj != null)
                    run.setStatus(statusobj.getValue().toString());
                KBObject linkobj = kb.getPropertyValue(wbobj, DISKOnt.getProperty(DISK.HAS_RUN_LINK));
                if (linkobj != null)
                    run.setLink(linkobj.getValue().toString());
                bindings.setRun(run);

                // Workflow details
                KBObject workflowobj = kb.getPropertyValue(wbobj, DISKOnt.getProperty(DISK.HAS_WORKFLOW));
                if (workflowobj != null) {
                  bindings.setWorkflow(workflowobj.getName());
                  String link = WingsAdapter.get().getWorkflowLink(workflowobj.getName());
                  if (link != null)
                      bindings.setWorkflowLink(link);
                }

                // Variable binding details
                for (KBObject vbobj : kb.getPropertyValues(wbobj, DISKOnt.getProperty(DISK.HAS_VARIABLE_BINDING))) {
                    KBObject varobj = kb.getPropertyValue(vbobj, DISKOnt.getProperty(DISK.HAS_VARIABLE));
                    KBObject bindobj = kb.getPropertyValue(vbobj, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE));
                    VariableBinding vbinding = new VariableBinding();
                    vbinding.setVariable(varobj.getName());
                    vbinding.setBinding(bindobj.getValueAsString());
                    bindings.getBindings().add(vbinding);
                }

                // Parameters details
                for (KBObject vbobj : kb.getPropertyValues(wbobj, DISKOnt.getProperty(DISK.HAS_PARAMETER))) {
                    KBObject varobj = kb.getPropertyValue(vbobj, DISKOnt.getProperty(DISK.HAS_VARIABLE));
                    KBObject bindobj = kb.getPropertyValue(vbobj, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE));
                    VariableBinding param = new VariableBinding(varobj.getName(),bindobj.getValueAsString());
                    bindings.addParameter(param);
                }

                // Optional parameters details
                for (KBObject vbobj : kb.getPropertyValues(wbobj, DISKOnt.getProperty(DISK.HAS_OPTIONAL_PARAMETER))) {
                    KBObject varobj = kb.getPropertyValue(vbobj, DISKOnt.getProperty(DISK.HAS_VARIABLE));
                    KBObject bindobj = kb.getPropertyValue(vbobj, DISKOnt.getProperty(DISK.HAS_BINDING_VALUE));
                    VariableBinding optionalParam = new VariableBinding(varobj.getName(),bindobj.getValueAsString());
                    bindings.addOptionalParameter(optionalParam);
                }

                KBObject hypobj = kb.getPropertyValue(wbobj, DISKOnt.getProperty(DISK.HAS_HYPOTHESIS_VARIABLE));
                if (hypobj != null)
                    bindings.getMeta().setHypothesis(hypobj.getName());
                KBObject revhypobj = kb.getPropertyValue(wbobj, DISKOnt.getProperty(DISK.HAS_REVISED_HYPOTHESIS_VARIABLE));
                if (revhypobj != null)
                    bindings.getMeta().setRevisedHypothesis(revhypobj.getName());

                list.add(bindings);
            }
            this.end();
        }
        return list;
    }
    
}
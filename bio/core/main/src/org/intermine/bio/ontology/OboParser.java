package org.intermine.bio.ontology;

/*
 * Copyright (C) 2002-2013 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.log4j.Logger;
import org.obo.dataadapter.OBOAdapter;
import org.obo.dataadapter.OBOFileAdapter;
import org.obo.dataadapter.OBOSerializationEngine;
import org.obo.dataadapter.SimpleLinkFileAdapter;
import org.obo.datamodel.OBOSession;

/**
 * @author Thomas Riley
 * @author Peter Mclaren - 5/6/05 - added some functionality to allow terms to find all their parent
 * @author Xavier Watkins - 06/01/09 - refactored model
 * terms.
 */
public class OboParser
{
    private static final Logger LOG = Logger.getLogger(OboParser.class);
//    private static File temp = null;
    private final Pattern synPattern = Pattern.compile("\\s*\"(.+?[^\\\\])\".*");
    private final Matcher synMatcher = synPattern.matcher("");
    private Set<String> oboXrefs = new HashSet<String>();
    private static final String PROP_FILE = "obo_xrefs.properties";

    /**
     * All terms.
     */
    protected Map<String, OboTerm> terms = new HashMap<String, OboTerm>();

    /**
     * All relations
     */
    protected List<OboRelation> relations = new ArrayList<OboRelation>();

    /**
     * All relation types
     */
    protected Map<String, OboTypeDefinition> types = new HashMap<String, OboTypeDefinition>();

    /**
     * Default namespace.
     */
    protected String defaultNS = "";

    /**
     * Parse an OBO file to produce a set of OboTerms.
     * @param in with text in OBO format
     * @throws Exception if anything goes wrong
     */
    public void processOntology(Reader in) throws Exception {
        readConfig();
        readTerms(new BufferedReader(in));
    }

    /**
     * Parses config file for valid prefixes, eg. FBbt FMA. Only valid xrefs will be processed,
     * eg. FBbt:0000001
     */
    protected void readConfig() {
        Properties props = new Properties();
        try {
            props.load(getClass().getClassLoader().getResourceAsStream(PROP_FILE));
        } catch (IOException e) {
            throw new RuntimeException("Problem loading properties '" + PROP_FILE + "'", e);
        }
        Enumeration<?> propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String xref = (String) propNames.nextElement();
            oboXrefs.add(xref);
        }
    }

    /**
     * Parse the relations file generated by the OboEdit reasoner (calculates transitivity)
     *
     * @param dagFileName the name of the obo file to read from
     * @throws Exception if something goes wrong
     */
    @SuppressWarnings("unchecked")
    public void processRelations(String dagFileName) throws Exception {
        File temp = null;
        File f = new File("build");
        if (!f.exists()) {
            temp = File.createTempFile("obo", ".tmp");
        } else {
            temp = File.createTempFile("obo", ".tmp", f);
        }
    // Copied from OBO2Linkfile.convertFiles(OBOAdapterConfiguration, OBOAdapterConfiguration,
        // List); OBOEDIT code
        // TODO OBO will soon release the file containing all transitive closures calculated
        // by obo2linkfile so we can get rid of the code below and just use the downloaded file.
        long startTime = System.currentTimeMillis();
        OBOFileAdapter.OBOAdapterConfiguration readConfig =
            new OBOFileAdapter.OBOAdapterConfiguration();

        readConfig.setBasicSave(false);
        readConfig.getReadPaths().add(dagFileName);

        OBOFileAdapter.OBOAdapterConfiguration writeConfig =
            new OBOFileAdapter.OBOAdapterConfiguration();
        writeConfig.setBasicSave(false);

        OBOSerializationEngine.FilteredPath path = new OBOSerializationEngine.FilteredPath();
        path.setUseSessionReasoner(false);
        path.setImpliedType(OBOSerializationEngine.SAVE_ALL);
        path.setPath(temp.getCanonicalPath());
        writeConfig.getSaveRecords().add(path);

        writeConfig.setSerializer("OBO_1_2");

        OBOFileAdapter adapter = new OBOFileAdapter();
        OBOSession session = adapter.doOperation(OBOAdapter.READ_ONTOLOGY, readConfig, null);
        SimpleLinkFileAdapter writer = new SimpleLinkFileAdapter();

        writer.doOperation(OBOAdapter.WRITE_ONTOLOGY, writeConfig, session);
        LOG.info("PROGRESS:" + writer.getProgressString());
        // END OF OBO2EDIT code
        readRelations(new BufferedReader(new FileReader(temp.getCanonicalPath())));
        temp.delete();
        long timeTaken = System.currentTimeMillis() - startTime;
        LOG.info("Processed transitive closure of OBO file, took: " + timeTaken + " ms");
    }

    /**
     * Parse an OBO file to produce a map from ontology term id to name.
     *
     * @param in text in OBO format
     * @return a map from ontology term identifier to name
     * @throws IOException if anything goes wrong
     */
    public Map<String, String> getTermIdNameMap(Reader in)
        throws IOException {
        readTerms(new BufferedReader(in));
        Map<String, String> idNames = new HashMap<String, String>();
        for (OboTerm ot : terms.values()) {
            idNames.put(ot.getId(), ot.getName());
        }
        return idNames;
    }

    /**
     * @return a set of DagTerms
     */
    public Set<OboTerm> getOboTerms() {
        return new HashSet<OboTerm>(terms.values());
    }

    /**
     * @return a list of OboRelations
     */
    public List<OboRelation> getOboRelations() {
        return relations;
    }


    /**
     * Read DAG input line by line to generate hierarchy of DagTerms.
     *
     * @param in text in DAG format
     * @throws IOException if anything goes wrong
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void readTerms(BufferedReader in) throws IOException {
        String line;
        Map<String, String> tagValues = new MultiValueMap();
        List<Map> termTagValuesList = new ArrayList<Map>();
        List<Map> typeTagValuesList = new ArrayList<Map>();

        Pattern tagValuePattern = Pattern.compile("(.+?[^\\\\]):(.+)");
        Pattern stanzaHeadPattern = Pattern.compile("\\s*\\[(.+)\\]\\s*");
        Matcher tvMatcher = tagValuePattern.matcher("");
        Matcher headMatcher = stanzaHeadPattern.matcher("");

        while ((line = in.readLine()) != null) {
            // First strip off any comments
            if (line.indexOf('!') >= 0) {
                line = line.substring(0, line.indexOf('!'));
            }

            tvMatcher.reset(line);
            headMatcher.reset(line);

            if (headMatcher.matches()) {
                String stanzaType = headMatcher.group(1);
                tagValues = new MultiValueMap(); // cut loose
                if ("Term".equals(stanzaType)) {
                    termTagValuesList.add(tagValues);
                    LOG.debug("recorded term with " + tagValues.size() + " tag values");
                } else
                    if ("Typedef".equals(stanzaType)) {
                        typeTagValuesList.add(tagValues);
                        LOG.debug("recorded type with " + tagValues.size() + " tag values");
                    } else {
                        LOG.warn("Ignoring " + stanzaType + " stanza");
                    }
                LOG.debug("matched stanza " + stanzaType);
            } else if (tvMatcher.matches()) {
                String tag = tvMatcher.group(1).trim();
                String value = tvMatcher.group(2).trim();
                tagValues.put(tag, value);
                LOG.debug("matched tag \"" + tag + "\" with value \"" + value + "\"");

                if ("default-namespace".equals(tag)) {
                    defaultNS = value;
                    LOG.info("default-namespace is \"" + value + "\"");
                }
            }
        }

        in.close();


        // Build the OboTypeDefinition objects
        OboTypeDefinition oboType = new OboTypeDefinition("is_a", "is_a", true);
        types.put(oboType.getId() , oboType);
        for (Iterator<Map> iter = typeTagValuesList.iterator(); iter.hasNext();) {
            Map<?, ?> tvs = iter.next();
            String id = (String) ((List<?>) tvs.get("id")).get(0);
            String name = (String) ((List<?>) tvs.get("name")).get(0);
            boolean isTransitive = isTrue(tvs, "is_transitive");
            oboType = new OboTypeDefinition(id, name, isTransitive);
            types.put(oboType.getId() , oboType);
        }

        // Just build all the OboTerms disconnected
        for (Iterator<Map> iter = termTagValuesList.iterator(); iter.hasNext();) {
            Map<?, ?> tvs = iter.next();
            String id = (String) ((List<?>) tvs.get("id")).get(0);
            String name = (String) ((List<?>) tvs.get("name")).get(0);
            OboTerm term = new OboTerm(id, name);
            term.setObsolete(isTrue(tvs, "is_obsolete"));
            terms.put(term.getId(), term);
        }

        // Now connect them all together
        for (Iterator<Map> iter = termTagValuesList.iterator(); iter.hasNext();) {
            Map<?, ?> tvs = iter.next();
            if (!isTrue(tvs, "is_obsolete")) {
                configureDagTerm(tvs);
            }
        }
    }

    /**
     * Configure dag terms with values from one entry.
     *
     * @param tagValues term config
     */
    protected void configureDagTerm(Map<?, ?> tagValues) {
        String id = (String) ((List<?>) tagValues.get("id")).get(0);
        OboTerm term = terms.get(id);

        if (term != null) {
            term.setTagValues(tagValues);

            List<?> synonyms = (List<?>) tagValues.get("synonym");
            if (synonyms != null) {
                addSynonyms(term, synonyms, "synonym");
            }
            synonyms = (List<?>) tagValues.get("related_synonym");
            if (synonyms != null) {
                addSynonyms(term, synonyms, "related_synonym");
            }
            synonyms = (List<?>) tagValues.get("exact_synonym");
            if (synonyms != null) {
                addSynonyms(term, synonyms, "exact_synonym");
            }
            synonyms = (List<?>) tagValues.get("broad_synonym");
            if (synonyms != null) {
                addSynonyms(term, synonyms, "broad_synonym");
            }
            synonyms = (List<?>) tagValues.get("narrow_synonym");
            if (synonyms != null) {
                addSynonyms(term, synonyms, "narrow_synonym");
            }
            List<?> altIds = (List<?>) tagValues.get("alt_id");
            if (altIds != null) {
                addSynonyms(term, altIds, "alt_id");
            }

            List<?> xrefs = (List<?>) tagValues.get("xref");
            if (xrefs != null) {
                addXrefs(term, xrefs);
            }

            // Set namespace
            List<?> nsl = (List<?>) tagValues.get("namespace");
            if (nsl != null && nsl.size() > 0) {
                term.setNamespace((String) nsl.get(0));
            } else {
                term.setNamespace(defaultNS);
            }

            // Set description
            List<?> defl = (List<?>) tagValues.get("def");
            String def = null;
            if (defl != null && defl.size() > 0) {
                def = (String) defl.get(0);
                synMatcher.reset(def);
                if (synMatcher.matches()) {
                    term.setDescription(unescape(synMatcher.group(1)));
                }
            } else {
                LOG.warn("Failed to parse def of term " + id + " def: " + def);
            }

        } else {
            LOG.warn("OboParser.configureDagTerm() - no term found for id:" + id);
        }
    }

    /**
     * Given the tag+value map for a term, return whether it's true or false
     *
     * @param tagValues map of tag name to value for a single term
     * @param tagValue the term to look for in the map
     * @return true if the term is marked true, false if not
     */
    public static boolean isTrue(Map<?, ?> tagValues, String tagValue) {
        List<?> vals = (List<?>) tagValues.get(tagValue);
        if (vals != null && vals.size() > 0) {
            if (vals.size() > 1) {
                LOG.warn("Term: " + tagValues + " has more than one (" + vals.size()
                        + ") is_obsolete values - just using first");
            }
            return ((String) vals.get(0)).equalsIgnoreCase("true");
        }
        return false;
    }

    /**
     * Add synonyms to a DagTerm.
     *
     * @param term     the DagTerm
     * @param synonyms List of synonyms (Strings)
     * @param type     synonym type
     */
    protected void addSynonyms(OboTerm term, List<?> synonyms, String type) {
        for (Iterator<?> iter = synonyms.iterator(); iter.hasNext();) {
            String line = (String) iter.next();
            synMatcher.reset(line);
            if (synMatcher.matches()) {
                term.addSynonym(new OboTermSynonym(unescape(synMatcher.group(1)), type));
            } else if ("alt_id".equals(type)) {
                term.addSynonym(new OboTermSynonym(line, type));
            } else {
                LOG.warn("Could not match synonym value from: " + line);
            }
        }
    }

    /**
     * Add xrefs to a DagTerm.
     * eg.  xref: FBbt:00005137
     * xref: FMA:5884
     * xref: MA:0002406
     *
     * @param term the DagTerm
     * @param xrefs List of xrefs (Strings)
     */
    protected void addXrefs(OboTerm term, List<?> xrefs) {
        for (Iterator<?> iter = xrefs.iterator(); iter.hasNext();) {
            String identifier = (String) iter.next();
            if (identifier.contains(":")) {
                String[] bits = identifier.split(":");
                String prefix = bits[0];    // eg FBbt
                if (bits.length > 1 && prefix != null && oboXrefs.contains(prefix)) {
                    term.addXref(new OboTerm(identifier));
                }
            }
        }
    }

    /**
     * This method reads relations calculated by the GO2Link script in OBOEdit.
     *
     * @param in the reader for the Go2Link file
     * @throws IOException an exception
     */
    protected void readRelations(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            String[] bits = line.split("\t");
            OboTypeDefinition type = types.get(bits[1].replaceAll("OBO_REL:", ""));
            if (type != null) {
                String id1 = null, id2 = null;
                boolean asserted = false, redundant = false;
                for (int i = 0; i < bits.length; i++) {
                    switch (i) {
                        case 0:// id1
                        {
                            id1 = bits[i];
                            break;
                        }
                        case 1:// type
                        {
                            // already initialised
                            break;
                        }
                        case 2:// id2
                        {
                            id2 = bits[i];
                            break;
                        }
                        case 3:// asserted
                        {
                            asserted = (bits[i]).matches("asserted");
                            break;
                        }
                        case 4:// ??
                        {
                            // do nothing
                            break;
                        }
                        case 5:// redundant
                        {
                            redundant = (bits[i]).matches("redundant");
                            break;
                        }
                        default:
                            break;
                    }
                }
                OboRelation relation = new OboRelation(id1, id2, type);
                relation.setDirect(asserted);
                relation.setRedundant(redundant);
                relations.add(relation);
            } else {
                LOG.info("Unsupported type:" + bits[1]);
            }
        }
        in.close();
    }

    /**
     * Perform OBO unescaping.
     *
     * @param string the escaped string
     * @return the corresponding unescaped string
     */
    protected String unescape(String string) {
        int sz = string.length();
        StringBuffer out = new StringBuffer(sz);
        boolean hadSlash = false;

        for (int i = 0; i < sz; i++) {
            char ch = string.charAt(i);

            if (hadSlash) {
                switch (ch) {
                    case 'n':
                        out.append('\n');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'W':
                        out.append(' ');
                        break;
                    default:
                        out.append(ch);
                        break;
                }
                hadSlash = false;
            } else if (ch == '\\') {
                hadSlash = true;
            } else {
                out.append(ch);
            }
        }

        return out.toString();
    }
}

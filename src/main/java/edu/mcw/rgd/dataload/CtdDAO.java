package edu.mcw.rgd.dataload;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.StringMapQuery;
import edu.mcw.rgd.datamodel.Association;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.process.Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author mtutaj
 * @since 3/21/12
 * wrapper to handle all DAO code
 */
public class CtdDAO {

    AnnotationDAO annotationDAO = new AnnotationDAO();
    AssociationDAO associationDAO = new AssociationDAO();
    GeneDAO geneDAO = associationDAO.getGeneDAO();
    OntologyXDAO ontologyDAO = new OntologyXDAO();
    RGDManagementDAO rdao = new RGDManagementDAO();
    XdbIdDAO xdbIdDAO = new XdbIdDAO();

    Logger logStatus = LogManager.getLogger("status");
    Logger logInsertedSynonyms = LogManager.getLogger("insertedSynonyms");

    public String getConnectionInfo() {
        return rdao.getConnectionInfo();
    }

    public List<StringMapQuery.MapPair> getChebiSynonymsWithCasRN() throws Exception {

        //return ontologyDAO.getTermsWithCasRN();

        String sql = "SELECT DISTINCT term_acc,synonym_name cas FROM ont_synonyms s WHERE synonym_type='xref' AND synonym_name LIKE 'CAS:%' " +
                "AND EXISTS(SELECT 1 FROM ont_terms t WHERE t.term_acc=s.term_acc AND is_obsolete=0 AND ont_id='CHEBI')";
        return StringMapQuery.execute(ontologyDAO, sql);
    }

    public List<StringMapQuery.MapPair> getChebiSynonymsWithMesh() throws Exception {

        return ontologyDAO.getTermsWithMesh("CHEBI");
    }

    public Collection<String> getChemicalsByName(String chemicalName) throws Exception {
        if( mapNormalizedNameToChebiAccId==null ) {
           mapNormalizedNameToChebiAccId = new HashMap<>();
            for( Term term: ontologyDAO.getActiveTerms("CHEBI") ) {
                String normalizedName = normalizeTermName(term.getTerm());
                Set<String> chebiAccIds = mapNormalizedNameToChebiAccId.get(normalizedName);
                if( chebiAccIds==null ) {
                    chebiAccIds = new HashSet<>();
                    mapNormalizedNameToChebiAccId.put(normalizedName, chebiAccIds);
                }
                chebiAccIds.add(term.getAccId());
            }
        }

        String normalizedName = normalizeTermName(chemicalName);
        Set<String> chebiAccIds = mapNormalizedNameToChebiAccId.get(normalizedName);
        if( chebiAccIds==null ) {
            return null;
        }
        return chebiAccIds;
    }
    static Map<String,Set<String>> mapNormalizedNameToChebiAccId;

    String normalizeTermName(String name) {
        String[] words = name.split("\\W+");
        Set<String> uniqueWords = new TreeSet<>();
        for( String word: words ) {
            uniqueWords.add(word.toLowerCase());
        }
        return Utils.concatenate(uniqueWords, ".");
    }

    /**
     *
     * @param termAcc term accession id
     * @param meshId MESH ID
     * @return true if Chebi term already had a synonym, false, if 'xref_mesh' synonym was inserted
     * @throws Exception
     */
    public boolean ensureChebiTermHasMeshSynonym(String termAcc, String meshId) throws Exception {
        // FAST CHECK: see if the queried term is already in the hash
        Boolean termAccHasXrefMesh = termAccToHasXrefMeshMap.get(termAcc);
        if( termAccHasXrefMesh!=null ) {
            return true;
        }

        // SLOW CODE: no, the queried term was not in the map
        //  query the database to determine if the term had 'xref_mesh' synonyms
        for (TermSynonym synonym : ontologyDAO.getTermSynonyms(termAcc)) {
            if (Utils.stringsAreEqual(synonym.getType(), "xref_mesh")) {
                termAccHasXrefMesh = true;
                break;
            }
        }
        if (termAccHasXrefMesh == null) {
            termAccHasXrefMesh = false;
        }

        // SYNC: 'SLOW CODE' section could have been executed by multiple threads
        //  so we use the return value of putIfAbsent() to determine if the current thread
        //  inserted the key into the map, or not
        if( termAccToHasXrefMeshMap.putIfAbsent(termAcc, termAccHasXrefMesh)==null ) {
            // yes, this thread inserted the key into the map
            // if the term did not have 'xref_mesh' synonym, we insert one!
            if( !termAccHasXrefMesh ) {
                insertMeshIdAsChebiTermSynonym(termAcc, meshId);
                return false;
            }
        }

        return true;
    }

    public void insertMeshIdAsChebiTermSynonym( String termAcc, String meshId ) throws Exception {

        TermSynonym synonym = new TermSynonym();
        synonym.setType("xref_mesh");
        synonym.setName(meshId);
        synonym.setTermAcc(termAcc);
        synonym.setSource("CTDChemDrug");
        synonym.setCreatedDate(new Date());
        synonym.setLastModifiedDate(synonym.getCreatedDate());
        ontologyDAO.insertTermSynonym(synonym);
        logInsertedSynonyms.info(synonym.dump("|"));
    }

    final ConcurrentHashMap<String,Boolean> termAccToHasXrefMeshMap = new ConcurrentHashMap<>();

    /**
     * get an ontology term given term accession id;
     * return null if accession id is invalid
     * @param termAcc term accession id
     * @return Term object if given term found in database or null otherwise
     * @throws Exception if something wrong happens in spring framework
     */
    public Term getTermByAccId(String termAcc) throws Exception {

        return ontologyDAO.getTermByAccId(termAcc);
    }

    /**
     * get all active genes with given external id
     * @param xdbKey - external db key
     * @param accId - external id to be looked for
     * @return list of Gene objects
     */
    public List<Gene> getActiveGenesByXdbId(int xdbKey, String accId) throws Exception {

        String key = xdbKey+","+accId;
        List<Gene> genes = activeGenesByXdbIdMap.get(key);
        if( genes==null ) {
            genes = xdbIdDAO.getActiveGenesByXdbId(xdbKey, accId);
            activeGenesByXdbIdMap.put(key, genes);
        }
        return genes;
    }
    ConcurrentHashMap<String, List<Gene>> activeGenesByXdbIdMap = new ConcurrentHashMap<>();

    List<Gene> removeInactiveGenes(List<Gene> genes) throws Exception {

        Iterator<Gene> it = genes.iterator();
        while( it.hasNext() ) {
            Gene gene = it.next();
            RgdId id = rdao.getRgdId2(gene.getRgdId());
            if( id==null || !id.getObjectStatus().equals("ACTIVE") )
                it.remove();
        }
        return genes;
    }

    /**
     * get all Gene objects by gene symbol for any species; if the list is empty, try to match by gene symbol alias
     * @param geneSymbol gene symbol to be searched for
     * @param speciesTypeKey species type key
     * @return list of all genes with exact matching symbol (empty list possible)
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<Gene> getAllGenesBySymbol(String geneSymbol, int speciesTypeKey) throws Exception {

        List<Gene> genes = new ArrayList<>();
        // get matching genes by gene symbol
        genes.addAll(removeInactiveGenes(geneDAO.getAllGenesBySymbol(geneSymbol, speciesTypeKey)));

        // if nothing found, get matching genes by alias
        if( genes.isEmpty() )
            genes.addAll(removeInactiveGenes(geneDAO.getGenesByAlias(geneSymbol, speciesTypeKey)));

        return genes;
    }

    /**
     * get active gene homologs for given gene rgd id (in order to make ISO annotations);
     * note: 'other homologs' (aka 'weak orthologs', or 'ortholog associations') are NOT considered
     * to be homologs for the purpose of making ISO annotations
     * @param rgdId gene rgd id
     * @return List of Gene objects being homolog to given gene; empty list if there are no homologs available or if gene rgd id is invalid
     * @throws Exception when unexpected error in spring framework occurs
     */
    synchronized public List<Gene> getRatMouseHumanHomologs(int rgdId) throws Exception {

        // first get homologs from cache
        List<Gene> homologs = _homologCache.get(rgdId);
        if( homologs!=null ) {
            return homologs;
        }
        // not in cache -- get homologs from database
        homologs = geneDAO.getHomologs(rgdId);

        // limit homologs to rat,mouse, human
        Iterator<Gene> it = homologs.iterator();
        while( it.hasNext() ) {
            Gene gene = it.next();
            if( gene.getSpeciesTypeKey()>3 )
                it.remove();
        }

        _homologCache.put(rgdId, homologs);
        return homologs;
    }
    private Map<Integer, List<Gene>> _homologCache = new HashMap<>(40003);


    /**
     * get active gene homologs for given gene rgd id and desired species
     * @param rgdId gene rgd id
     * @return List of Gene objects being homolog to given gene; empty list if there are no homologs available or if gene rgd id is invalid
     * @throws Exception when unexpected error in spring framework occurs
     */
    synchronized public List<Gene> getHomologs(int rgdId, int speciesTypeKey) throws Exception {

        return geneDAO.getActiveOrthologs(rgdId, speciesTypeKey);
    }

    public Gene getOrtholog( int geneRgdId, int desiredSpeciesTypeKey, String geneSymbol ) throws Exception {

        // try strong orthologs
        List<Gene> homologs = desiredSpeciesTypeKey>3 ? getHomologs(geneRgdId, desiredSpeciesTypeKey) : getRatMouseHumanHomologs(geneRgdId);
        for( Gene homolog: homologs ) {
            if( homolog.getSpeciesTypeKey()==desiredSpeciesTypeKey )
                return homolog;
        }

        // try weak orthologs: since multiple orthologs could be returned,
        // pick the one with highest levenstein distance
        Gene bestMatchGene = null;
        int bestMatchScore = Integer.MAX_VALUE;
        for( Association assoc: associationDAO.getAssociationsForMasterRgdId(geneRgdId, "weak_ortholog") ) {

            Gene gene;
            try {
                gene = geneDAO.getGene(assoc.getDetailRgdId());
            } catch(GeneDAO.GeneDAOException exception) {
                // gene rgd id is invalid
                logStatus.warn("  WARNING: Gene rgd id is invalid: "+assoc.getDetailRgdId());
                continue;
            }

            if( gene.getSpeciesTypeKey()!=desiredSpeciesTypeKey )
                continue;
            int score = computeLevenshteinDistance(gene.getSymbol(), geneSymbol);
            if( score<bestMatchScore ) {
                bestMatchGene = gene;
                bestMatchScore = score;
            }
        }
        return bestMatchGene;
    }

    /**
     * get annotations by a list of values that comprise unique key *except* XREF_SOURCE:
     * TERM_ACC+ANNOTATED_OBJECT_RGD_ID+REF_RGD_ID+EVIDENCE+WITH_INFO+QUALIFIER
     * @param annot Annotation object with the following fields set: TERM_ACC+ANNOTATED_OBJECT_RGD_ID+REF_RGD_ID+EVIDENCE+WITH_INFO+QUALIFIER
     * @return list of Annotation objects, possibly empty
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotationsByAnnot(Annotation annot) throws Exception {

        String query = "SELECT * FROM full_annot WHERE "
                // fields that are never null
                +"term_acc=? AND annotated_object_rgd_id=? AND evidence=? AND "
                // fields that could be null
                +"NVL(ref_rgd_id,0) = NVL(?,0) AND "
                +"NVL(with_info,'*') = NVL(?,'*') AND "
                +"NVL(qualifier,'*') = NVL(?,'*')";

        return annotationDAO.executeAnnotationQuery(query, annot.getTermAcc(), annot.getAnnotatedObjectRgdId(),
                annot.getEvidence(), annot.getRefRgdId(), annot.getWithInfo(), annot.getQualifier());
    }

    /**
     * update last modified date for annotation list given their full annot keys
     * @param fullAnnotKeys list of FULL_ANNOT_KEYs
     * @return count of rows affected
     * @throws Exception on spring framework dao failure
     */
    public int updateLastModified(List<Integer> fullAnnotKeys) throws Exception{

        return annotationDAO.updateLastModified(fullAnnotKeys);
    }

    public int updateLastModified(int fullAnnotKey) throws Exception{

        return annotationDAO.updateLastModified(fullAnnotKey);
    }

    /**
     * Insert new annotation into FULL_ANNOT table; full_annot_key will be set
     *
     * @param annot Annotation object representing column values
     * @throws Exception
     * @return value of new full annot key
     */
    public int insertAnnotation(Annotation annot) throws Exception{

        return annotationDAO.insertAnnotation(annot);
    }

    public int updateAnnotationNotesAndXRefSource(int fullAnnotKey, String notes, String xrefSource) throws Exception {
        String sql = "UPDATE full_annot SET xref_source=?,last_modified_date=SYSDATE,notes=? WHERE full_annot_key=?";
        return annotationDAO.update(sql, xrefSource, notes, fullAnnotKey);
    }

    /**
     * get all annotations modified before given date and time
     *
     * @return list of annotations
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotationsModifiedBeforeTimestamp(int createdBy, Date dt) throws Exception{
        return annotationDAO.getAnnotationsModifiedBeforeTimestamp(createdBy, dt);
    }

    /**
     * delete given set of annotations
     *
     * @return number of rows affected
     * @throws Exception on spring framework dao failure
     */
    public int deleteAnnotations(List<Annotation> annots) throws Exception{
        List<Integer> annotKeys = new ArrayList<>(annots.size());
        for( Annotation ann: annots ) {
            annotKeys.add(ann.getKey());
        }
        return annotationDAO.deleteAnnotations(annotKeys);
    }

    public long getTotalNotesLengthForChebiAnnotations() throws Exception {
        String sql = "SELECT SUM(dbms_lob.getlength(notes)) FROM full_annot WHERE aspect='E'";
        return annotationDAO.getLongCount(sql);
    }

    public long getTotalXRefSourceLengthForChebiAnnotations() throws Exception {
        String sql = "SELECT SUM(LENGTH(xref_source)) FROM full_annot WHERE aspect='E'";
        return annotationDAO.getLongCount(sql);
    }

    private static int minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    public static int computeLevenshteinDistance(CharSequence str1, CharSequence str2) {
        int[][] distance = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++)
            distance[i][0] = i;
        for (int j = 1; j <= str2.length(); j++)
            distance[0][j] = j;

        for (int i = 1; i <= str1.length(); i++)
            for (int j = 1; j <= str2.length(); j++)
                distance[i][j] = minimum(distance[i - 1][j] + 1,
                                         distance[i][j - 1] + 1,
                                         distance[i - 1][j - 1] + ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1));

        return distance[str1.length()][str2.length()];
    }
}

package edu.mcw.rgd.dataload;

import edu.mcw.rgd.dao.spring.StringMapQuery;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author mtutaj
 * @since 3/21/12
 * Run entire import machinery.
 * <p>
 * Gene-chemical interactions file CTD_chem_gene_ixns.tsv must have the following format (valid as of June 2012):
 * <pre>
 * Fields:
 * [0] ChemicalName
 * [1] ChemicalID (MeSH identifier)
 * [2] CasRN (CAS Registry Number)
 * [3] GeneSymbol
 * [4] GeneID (NCBI Gene identifier)
 * [5] GeneForms ('|'-delimited list) New!
 * [6] Organism (scientific name)
 * [7] OrganismID (NCBI Taxonomy identifier)
 * [8] Interaction
 * [9] InteractionActions ('|'-delimited list)
 * [10]PubmedIDs ('|'-delimited list)
 * </pre>
 */
public class CtdImporter {

    private final Logger logStatus = Logger.getLogger("status");
    private final Logger logDeletedAnnots = Logger.getLogger("deletedAnnots");
    private final Logger logInsertedAnnots = Logger.getLogger("insertedAnnots");
    private final Logger logUpdatedAnnots = Logger.getLogger("updatedAnnots");
    private final Logger logMultiMatch = Logger.getLogger("multiMatch");
    private final Logger logNoMatch = Logger.getLogger("noMatch");
    private final Logger logUpdatedAnnotNotes = Logger.getLogger("updatedAnnotNotes");

    private String aspect;
    private int obsoleteAnnotLimit;
    private int owner;
    private String dataSource;
    private int refRgdId;

    private CtdDAO dao = new CtdDAO();
    private CtdParser parser;
    private int maxXrefSourceLength;

    private Date startTimeStamp; // timestamp when the pipeline was started

    // CHEBI terms keyed by CasRN -- note: there could be multiple terms for one CasRN
    final private MultiValueMap mapCasRNToChebiTerm = new MultiValueMap();

    // map of MESH ids to CHEBI terms
    final private MultiValueMap mapMeshToChebi = new MultiValueMap();

    final private ConcurrentHashMap<String, List<Annotation>> incomingAnnots = new ConcurrentHashMap<>();
    final private ConcurrentHashMap<String, List<Annotation>> inRgdAnnots = new ConcurrentHashMap<>();
    private String version;

    private CounterPool counters = new CounterPool();

    /**
     * run entire import
     * <ol>
     *     <li>load chemical gene interaction types from CTD_chem_gene_ixn_types.tsv file</li>
     *     <li>load chemical gene interactions from CTD_chem_gene_ixns.tsv file</li>
     *     <li>load chemicals from CTD_chemicals.tsv.gz file</li>
     *     <li>match every chemical by CasRN against CHEBI ontology</li>
     * </ol>
     * @throws Exception exception
     */
    public void run() throws Exception {

        logStatus.info(getVersion());

        startTimeStamp = new Date();

        logStatus.info("   " + dao.getConnectionInfo());

        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logStatus.info("   started at " + sdt.format(startTimeStamp));

        parser.init();

        dumpTotalNotesLength("AT_BEGIN");

        loadCasRNsMappedToChebiAccIds();

        loadMeshMappedToChebiAccIds();

        // download file with gene - chemical interactions
        parser.downloadChemicals(mapCasRNToChebiTerm, mapMeshToChebi, dao, counters);

        // parse all chemicals
        List<CtdRecord> ctdRecords = parser.process(counters);

        // map of NCBI gene ids to Gene objects for speedup
        final Map<String, Gene> mapGenes = Collections.synchronizedMap(new HashMap<String, Gene>());

        ctdRecords.parallelStream().forEach(rec -> {

            try {
                rec.initQC();

                Gene oneGene;

                synchronized (mapGenes) {
                    // match chemical by NCBI GeneId with a gene
                    rec.gene = null;
                    oneGene = null;

                    // consult gene cache first
                    if (mapGenes.containsKey(rec.interaction.getGeneID())) {
                        Gene gene = mapGenes.get(rec.interaction.getGeneID());
                        if (gene.getSpeciesTypeKey() != rec.interaction.getSpeciesTypeKey()) {
                            counters.increment("SPECIES TYPE MIXUP");
                        } else
                            rec.gene = gene;
                    }

                    if (rec.gene == null) {
                        List<Gene> genes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_NCBI_GENE, rec.interaction.getGeneID());
                        if (genes.isEmpty()) {
                            // no match by NCBI geneid -- try to match by gene symbol
                            genes = dao.getAllGenesBySymbol(rec.interaction.getGeneSymbol(), rec.interaction.getSpeciesTypeKey());
                            if (genes.isEmpty()) {
                                counters.increment("NO MATCH BY NCBI GENEID AND BY GENE SYMBOL");
                                logNoMatch.info("GENEID=" + rec.interaction.getGeneID() + " SYMBOL=" + rec.interaction.getGeneSymbol() + " species=" + rec.interaction.getSpeciesTypeKey());
                            }
                        } else if (genes.size() == 1) {
                            mapGenes.put(rec.interaction.getGeneID(), genes.get(0));
                        }

                        if (genes.size() > 1) {
                            // multiple genes in RGD -- try to match by symbol
                            String msg = "GENEID=" + rec.interaction.getGeneID() + " SYMBOL=" + rec.interaction.getGeneSymbol() + " species=" + rec.interaction.getSpeciesTypeKey();
                            for (Gene gene : genes) {
                                msg += "\n   RGDID=" + gene.getRgdId() + " SYMBOL=" + gene.getSymbol();
                            }

                            for (Gene gene : genes) {
                                if (Utils.stringsAreEqualIgnoreCase(gene.getSymbol(), rec.interaction.getGeneSymbol())) {
                                    rec.gene = gene;
                                    counters.increment("MULTIMATCH BY NCBI GENEID; SINGLE MATCH BY GENE SYMBOL");
                                    break;
                                }
                            }
                            if (rec.gene == null) {
                                counters.increment("MULTIMATCH");
                            } else {
                                msg += "\n   MULTIMATCH BY NCBI GENEID; SINGLE MATCH BY GENE SYMBOL";
                            }
                            logMultiMatch.info(msg);
                        } else if (genes.size() == 1) {
                            oneGene = genes.get(0);
                        }
                    }
                }

                if (oneGene != null) {
                    if (oneGene.getSpeciesTypeKey() != rec.interaction.getSpeciesTypeKey()) {
                        // interaction species is different than gene-from-GeneId species!
                        // find the ortholog
                        oneGene = dao.getOrtholog(oneGene.getRgdId(), rec.interaction.getSpeciesTypeKey(), rec.interaction.getGeneSymbol());
                    }
                    if (oneGene != null) {
                        rec.gene = oneGene;
                        counters.increment("SINGLE MATCH");
                    } else {
                        counters.increment("SPECIES TYPE MIXUP");
                    }
                }

                // load ortholog genes (only for rat/mouse/human)
                if (rec.gene != null) {
                    if( rec.gene.getSpeciesTypeKey()==SpeciesType.RAT
                     || rec.gene.getSpeciesTypeKey()==SpeciesType.MOUSE
                     || rec.gene.getSpeciesTypeKey()==SpeciesType.HUMAN)
                    {
                        List<Gene> homologs = new ArrayList<>(dao.getRatMouseHumanHomologs(rec.gene.getRgdId()));
                        rec.homologs = homologs;
                    } else {
                        rec.homologs = new ArrayList<Gene>(1);
                    }
                    rec.homologs.add(rec.gene);// add selfie to homologs
                }

                // annotations
                createIncomingAnnotations(rec);
                qcAnnots(rec);

            } catch (Exception e) {
                Utils.printStackTrace(e, logStatus);
                throw new RuntimeException(e);
            }
        });

        loadAnnots(counters);

        deleteObsoleteAnnotations(counters);

        dumpTotalNotesLength("AT_FINISH");

        logStatus.info(counters.dumpAlphabetically());

        logStatus.info("--CTD Chemical Drug Interactions pipeline DONE --");
        logStatus.info("--elapsed time: "+Utils.formatElapsedTime(startTimeStamp.getTime(), System.currentTimeMillis()));
    }


    void createIncomingAnnotations(CtdRecord rec) throws Exception {

        // compute qualifiers
        String qualifier;
        if( rec.interaction.getInteractionActions().indexOf('|')>0 ) {
            qualifier = "multiple interactions";
        }
        else {
            qualifier = rec.interaction.getInteractionActions().replace('^',' ');
        }

        // for every matching CasRN, create incoming annotations
        Collection<Term> terms = rec.chemical.getCasRN()==null ? null : (Collection<Term>) mapCasRNToChebiTerm.getCollection(rec.chemical.getCasRN());
        if( terms==null || terms.isEmpty() ) {
            terms = (Collection<Term>) mapMeshToChebi.getCollection(rec.chemical.getChemicalID());
        }
        if( (terms==null || terms.isEmpty()) && rec.chemical.chebiTerms!=null ) {
            terms = rec.chemical.chebiTerms;
        }

        for( Term term: terms ) {

            if( !dao.ensureChebiTermHasMeshSynonym(term.getAccId(), rec.chemical.getChemicalID()) ) {
                counters.increment("XREF_MESH_SYNONYMS_ADDED");
            }

            if( rec.homologs!=null ) {
                for( Gene gene: rec.homologs ) {
                    createAnnotations(rec, gene, term, qualifier);
                }
            }
        }
    }

    void createAnnotations(CtdRecord rec, Gene gene, Term term, String qualifier) throws Exception {

        Gene sourceGene = rec.gene;
        String evidence = sourceGene.getRgdId()==gene.getRgdId() ? "EXP" : "ISO";
        String notes = rec.interaction.getInteraction();
        String xrefSource = rec.interaction.getPubmedIds();

        Annotation annot = new Annotation();
        annot.setAnnotatedObjectRgdId(gene.getRgdId());
        annot.setAspect(getAspect());
        annot.setCreatedBy(getOwner());
        annot.setLastModifiedBy(getOwner());
        annot.setDataSrc(getDataSource());
        annot.setEvidence(evidence);
        annot.setCreatedDate(new Date());
        annot.setLastModifiedDate(annot.getCreatedDate());
        annot.setObjectName(gene.getName());
        annot.setObjectSymbol(gene.getSymbol());
        annot.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
        annot.setRefRgdId(getRefRgdId());
        annot.setTerm(term.getTerm());
        annot.setTermAcc(term.getAccId());
        annot.setXrefSource(xrefSource);
        annot.setQualifier(qualifier);
        annot.setNotes(notes);
        annot.setSpeciesTypeKey(gene.getSpeciesTypeKey());

        // sourceGene is the gene from which the annotation originally came from;
        // desired piece of info in ortholog annotations
        if( sourceGene.getRgdId()!=gene.getRgdId() ) {
            annot.setWithInfo("RGD:"+sourceGene.getRgdId());
        }

        // add this annotation to incoming annotations
        rec.incomingAnnots.add(annot);
    }

    void qcAnnots(CtdRecord rec) throws Exception {

        // are there any annotations to be processed?
        if( rec.incomingAnnots.isEmpty() )
            return;

        // process all incoming annotations
        for( int i=0; i<rec.incomingAnnots.size(); i++ ) {

            Annotation incomingAnnot = rec.incomingAnnots.get(i);
            String annotKey = CtdAnnotNotesManager.computeAnnotKey(incomingAnnot);
            List<Annotation> annots = incomingAnnots.get(annotKey);
            if( annots==null ) {
                annots = Collections.synchronizedList(new ArrayList<Annotation>());
                incomingAnnots.put(annotKey, annots);
            }
            annots.add(incomingAnnot);

            List<Annotation> annotsInRgd = inRgdAnnots.get(annotKey);
            if( annotsInRgd==null ) {
                annotsInRgd = dao.getAnnotationsByAnnot(incomingAnnot);
                inRgdAnnots.put(annotKey, annotsInRgd);
            }
        }
    }

    void loadAnnots(CounterPool counters) throws Exception {
        long time0 = System.currentTimeMillis();

        dumpTotalNotesLength("BEFORE_UPDATE_NOTES_XREFSRC");

        logStatus.debug("INCOMING ANNOT BUCKETS:"+incomingAnnots.size());
        AtomicInteger i=new AtomicInteger(0), annotCount=new AtomicInteger(0);

        incomingAnnots.entrySet().parallelStream().forEach( entry -> {
            List<Annotation> annots = entry.getValue();
            annotCount.addAndGet(annots.size());
            logStatus.debug((i.incrementAndGet()) + ". " + annotCount);
            try {
                process(counters, annots, entry.getKey());
            } catch(Exception e) {
                Utils.printStackTrace(e, logStatus);
                throw new RuntimeException(e);
            }
        });

        logStatus.debug("INCOMING ANNOT DONE");

        logStatus.info("LOAD ANNOTS OK -- elapsed "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    void process(CounterPool counters, List<Annotation> incomingAnnots, String annotKey) throws Exception {

        // double check
        if( incomingAnnots.isEmpty() ) {
            return;
        }

        List<Annotation> mergedIncomingAnnots = mergeAnnots(incomingAnnots, getMaxXrefSourceLength());


        List<Annotation> annotsInRgd = inRgdAnnots.get(annotKey);

        for( Annotation annot: mergedIncomingAnnots ) {
            // load annots in RGD
            if (annotsInRgd.isEmpty()) {
                dao.insertAnnotation(annot);
                logInsertedAnnots.info("inserted RGD:" + annot.getAnnotatedObjectRgdId() + " " + annot.getTermAcc() + " " + annot.getXrefSource() + " " + annot.getNotes()
                        +" "+SpeciesType.getCommonName(annot.getSpeciesTypeKey())+" "+annot.getEvidence());

                // annotation has been inserted
                counters.increment("ANNOTATIONS_" + annot.getEvidence() + "_INSERTED");
                counters.increment("ANNOTATIONS_" + SpeciesType.getCommonName(annot.getSpeciesTypeKey()).toUpperCase() + "_INSERTED");
                return;
            }

            logUpdatedAnnots.info("RGD:" + annot.getAnnotatedObjectRgdId() + " " + annot.getTermAcc() + " " + annot.getXrefSource());
            counters.increment("ANNOTATIONS_" + annot.getEvidence() + "_MATCHED");
            counters.increment("ANNOTATIONS_" + SpeciesType.getCommonName(annot.getSpeciesTypeKey()).toUpperCase() + "_MATCHED");

            // find the annot in RGD with same xrefSource, if possible, for update
            Annotation annotInRgd = annotsInRgd.get(0);
            for (Annotation ann : annotsInRgd) {
                if (Utils.stringsAreEqual(ann.getXrefSource(), annot.getXrefSource())) {
                    annotInRgd = ann;
                    break;
                }
            }
            annotsInRgd.remove(annotInRgd);

            // check if annotation notes and/or xref_source has to be updated
            if (Utils.stringsAreEqualIgnoreCase(annot.getNotes(), annotInRgd.getNotes())
                    && Utils.stringsAreEqualIgnoreCase(annot.getXrefSource(), annotInRgd.getXrefSource())) {

                // the annotation is up-to-date
                dao.updateLastModified(annotInRgd.getKey());
                counters.increment("ANNOTATIONS_UPDATED_TIME");
            } else {
                // update notes and xref_source of the annotation

                logUpdatedAnnotNotes.info("RGDID:" + annot.getAnnotatedObjectRgdId() + " " + annot.getTermAcc() + " " + annot.getXrefSource() + " NOTESLEN=" + annot.getNotes().length()
                        + "\n OLD:" + annotInRgd.getXrefSource() + " - " + annotInRgd.getNotes()
                        + "\n NEW:" + annot.getXrefSource() + " - " + annot.getNotes());

                dao.updateAnnotationNotesAndXRefSource(annotInRgd.getKey(), annot.getNotes(), annot.getXrefSource());
                counters.increment("ANNOTATIONS_UPDATED_TIME_NOTES_XREFSRC");
            }
        }
    }

    List<Annotation> mergeAnnots(List<Annotation> incomingAnnots, int maxXRefSourceLen) throws Exception {

        List<Annotation> mergedAnnots = new ArrayList<>();

        // merge notes and xref_source for incoming annots
        boolean processNextSplit = true;
        for( int splits=1; processNextSplit; splits++ ) {

            if( splits>1 ) {
                logStatus.info("  xrefSourceSplitCount="+splits+" RGD:"+incomingAnnots.get(0).getAnnotatedObjectRgdId()
                    +" "+incomingAnnots.get(0).getTermAcc()+" "+incomingAnnots.get(0).getTerm());
            }
            mergedAnnots.clear();
            processNextSplit = false; // optimistically assume we can merge annotations in the current split (99% true)

            int annotsInSplit = 1 + (incomingAnnots.size()/splits);

            for( int i=0; i<incomingAnnots.size(); i+=annotsInSplit ) {
                int toIndex = i+annotsInSplit;
                if( toIndex>incomingAnnots.size() ) {
                    toIndex = incomingAnnots.size();
                }
                Annotation annot = mergeAnnots(incomingAnnots.subList(i, toIndex));
                if( annot.getXrefSource().length()<=maxXRefSourceLen ) {
                    mergedAnnots.add(annot);
                } else {
                    processNextSplit = true;
                    break; // too many PMIDs in XREF_SOURCE
                }
            }
        }
        return mergedAnnots;
    }

    Annotation mergeAnnots(List<Annotation> incomingAnnots) throws Exception {

        Annotation result = (Annotation) incomingAnnots.get(0).clone();

        Set<String> noteSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> pmidSet = new TreeSet<>();
        for (Annotation ann : incomingAnnots) {

            // multiple notes are split by "; "
            String notes = ann.getNotes();
            if (notes != null && !notes.isEmpty()) {
                if (notes.contains("; ")) {
                    logStatus.warn("**** NOTES CONTAINS '; '");
                }
                Collections.addAll(noteSet, notes.split("; "));
            }

            // multiple PMIDs are separated by '|'
            String xrefSrc = ann.getXrefSource();
            if (xrefSrc != null && !xrefSrc.isEmpty()) {
                Collections.addAll(pmidSet, xrefSrc.split("[\\|]"));
            }
        }
        result.setNotes(Utils.concatenate(noteSet, "; "));
        result.setXrefSource(Utils.concatenate(pmidSet, "|"));
        return result;
    }

    void deleteObsoleteAnnotations(CounterPool counters) throws Exception {

        List<Annotation> obsoleteAnnotations = dao.getAnnotationsModifiedBeforeTimestamp(getOwner(), startTimeStamp);
        if( obsoleteAnnotations.size() > getObsoleteAnnotLimit() ) {

            String msg = "*******************************\n" +
                "There are more obsolete annotations ("+obsoleteAnnotations.size()+
                " than the built-in delete limit of "+getObsoleteAnnotLimit()+
                "; DELETE ABORTED!\n" +
                "******************************\n";
            logStatus.warn(msg);
            counters.add("OBSOLETE_ANNOTATIONS", obsoleteAnnotations.size());
            return;
        }

        for( Annotation obsoleteAnnot: obsoleteAnnotations ) {
            logDeletedAnnots.info("DELETE " + obsoleteAnnot.dump("|"));
            counters.increment("ANNOTATIONS_"+obsoleteAnnot.getEvidence() + "_DELETED");
        }

        dao.deleteAnnotations(obsoleteAnnotations);
    }

    void loadCasRNsMappedToChebiAccIds() throws Exception {

        for( StringMapQuery.MapPair pair: dao.getChebiSynonymsWithCasRN() ) {

            // synonym value is the cas_nr followed by source, f.e. "CAS:5142-23-4 "ChemIDplus"
            // this must be made to "5142-23-4"
            String casDecorated = pair.stringValue;
            int spacePos = casDecorated.indexOf(" ");
            String cas = spacePos>0 ? casDecorated.substring(4, spacePos) : casDecorated.substring(4).trim();

            Term term = dao.getTermByAccId(pair.keyValue);
            Collection<Term> terms = mapCasRNToChebiTerm.getCollection(cas);
            if( terms!=null ) {
                for( Term t: terms ) {
                    if( t.getAccId().equals(term.getAccId()) ) {
                        term = null;
                        break;
                    }
                }
            }
            if( term!=null ) {
                mapCasRNToChebiTerm.put(cas, term);
            }
        }

        counters.add("CHEBI_TERMS_WITH_CASRN", mapCasRNToChebiTerm.size());
        counters.add("CHEBI_TERMS_CASRN_COUNT", mapCasRNToChebiTerm.totalSize());
    }

    void loadMeshMappedToChebiAccIds() throws Exception {

        for( StringMapQuery.MapPair pair: dao.getChebiSynonymsWithMesh() ) {

            mapMeshToChebi.put(pair.stringValue, dao.getTermByAccId(pair.keyValue));
        }

        counters.add("CHEBI_TERMS_WITH_MESH", mapMeshToChebi.size());
        counters.add("CHEBI_TERMS_MESH_COUNT", mapMeshToChebi.totalSize());
    }

    void dumpTotalNotesLength(String statName) throws Exception {
        long totalLength = dao.getTotalNotesLengthForChebiAnnotations();
        logStatus.info("TOTAL_NOTES_LENGTH_"+statName+": "+Utils.formatThousands(totalLength)+" bytes");

        totalLength = dao.getTotalXRefSourceLengthForChebiAnnotations();
        logStatus.info("TOTAL_XREF_SOURCE_LENGTH_"+statName+": "+Utils.formatThousands(totalLength)+" bytes");
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }

    public String getAspect() {
        return aspect;
    }

    public void setOwner(int owner) {
        this.owner = owner;
    }

    public int getOwner() {
        return owner;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setRefRgdId(int refRgdId) {
        this.refRgdId = refRgdId;
    }

    public int getRefRgdId() {
        return refRgdId;
    }

    public void setObsoleteAnnotLimit(int obsoleteAnnotLimit) {
        this.obsoleteAnnotLimit = obsoleteAnnotLimit;
    }

    public int getObsoleteAnnotLimit() {
        return obsoleteAnnotLimit;
    }

    public void setParser(CtdParser parser) {
        this.parser = parser;
    }

    public void setMaxXrefSourceLength(int maxXrefSourceLength) {
        this.maxXrefSourceLength = maxXrefSourceLength;
    }

    public int getMaxXrefSourceLength() {
        return maxXrefSourceLength;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

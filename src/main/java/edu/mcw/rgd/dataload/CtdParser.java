package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.pipelines.PipelineSession;
import edu.mcw.rgd.pipelines.RecordPreprocessor;
import edu.mcw.rgd.process.FileDownloader;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * @author mtutaj
 * @since 3/3/2017
 */
public class CtdParser extends RecordPreprocessor {

    private final Logger logStatus = Logger.getLogger("status");
    private final Logger logRejectedAnnots = Logger.getLogger("rejectedAnnots");
    private final Logger logRejectedAnnotsSummary = Logger.getLogger("rejectedAnnotsSummary");

    private Set<String> organismNames;
    private String chemicalsFile;
    private String chemGeneInteractionsFile;

    // chemicals with MESH having matching CHEBI terms in RGD; keyed by ChemicalID (MESH)
    final private Map<String, CtdChemical> mapChemicals = new HashMap<>();

    // count of rejected annots for given chemical
    private Map<String,Integer> mapRejectedAnnotCounts = new HashMap<>();

    // download chemicals
    public void process() throws Exception {

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getChemGeneInteractionsFile());
        downloader.setLocalFile("data/chem_gene_ixns.tsv.gz");
        downloader.setPrependDateStamp(true);

        String localFile = downloader.downloadNew();

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(localFile))));
        int recno = 0;
        String line;
        while( (line=reader.readLine())!=null ) {
            // skip comment lines
            if( line.startsWith("#") )
                continue;
            // break into columns
            String cols[] = line.split("\\t", -1);
            if( cols.length==11 ) {

                String chemicalId = "MESH:"+cols[1];

                // organism must be on at least one of the targeted list of organism names
                String organism = cols[6];
                if( !getOrganismNames().contains(organism) ) {
                    getSession().incrementCounter("INTERACTION_SKIPPED_UNSUPPORTED_ORGANISM", 1);
                    continue;
                }

                CtdChemGeneInteraction interaction = new CtdChemGeneInteraction();

                interaction.setChemicalName(cols[0]);
                interaction.setChemicalID(chemicalId);
                interaction.setCasRN(cols[2]);
                interaction.setGeneSymbol(cols[3]);
                interaction.setGeneID(cols[4]);
                interaction.setGeneForms(cols[5]);
                interaction.setOrganism(organism);
                if( !interaction.setOrganismID(cols[7]) ) {
                    logStatus.warn("Unsupported taxonomic organism id " + cols[7]);
                }
                interaction.setInteraction(cols[8]);
                interaction.setInteractionActions(cols[9]);
                interaction.setPubmedIds(cols[10]);

                // see if chemical id is on the list of processed chemicals
                if( !mapChemicals.containsKey(chemicalId) ) {
                    // this chemical is not on list of chemicals processed by the pipeline
                    getSession().incrementCounter("INTERACTION_SKIPPED_CHEMICAL_NOT_MATCHING_CHEBI", 1);

                    // log it
                    logRejectedAnnots.info(interaction.dump("|"));

                    //
                    String key = interaction.dump2("|");
                    Integer count = mapRejectedAnnotCounts.get(key);
                    if( count==null ) {
                        count = 0;
                    }
                    mapRejectedAnnotCounts.put(key, count+1);
                    continue;
                }

                CtdRecord rec = new CtdRecord();
                rec.interaction = interaction;
                rec.chemical = mapChemicals.get(chemicalId);
                rec.setRecNo(++recno);

                getSession().putRecordToFirstQueue(rec);

                //if( recno>=10000 ) {
                //    System.out.println("Stop interactions loading after 10000");
                //   break;
                //}
            }
        }
        reader.close();

        dumpRejectedAnnotations();

        getSession().incrementCounter("INTERACTIONS_LOADED", getSession().getRecordsProcessed(0));
    }

    void dumpRejectedAnnotations() {
        // reverse the hash: annot-count --> list of terms
        MultiValueMap map = new MultiValueMap();
        for( Map.Entry<String,Integer> entry: mapRejectedAnnotCounts.entrySet() ) {
            map.put(entry.getValue(), entry.getKey());
        }

        // sort keys
        ArrayList<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);

        // dump annots: annots with most annotat counts are dumped first
        for( int i=keys.size()-1; i>=0; i-- ) {
            int annotCount = keys.get(i);
            Collection coll = map.getCollection(annotCount);
            for( Object obj: coll ) {
                logRejectedAnnotsSummary.info(obj.toString() + "|ANNOT_COUNT:" + annotCount);
            }
        }
    }

    public void downloadChemicals(MultiValueMap mapCasRNToChebiTerm, MultiValueMap mapMeshToChebi, CtdDAO dao) throws Exception {

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getChemicalsFile());
        downloader.setLocalFile("data/chemicals.tsv.gz");
        downloader.setPrependDateStamp(true);

        String localFile = downloader.downloadNew();

        // counters
        int chemicalsWithoutCasRN = 0;
        int chemicalsWithNonMatchingCasRN = 0;
        int chemicalsWithMatchingCasRN = 0;
        int chemicalsWithMatchingMesh = 0;
        int chemicalsWithMatchingTermName = 0;

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(localFile))));
        String line;

        while( (line=reader.readLine())!=null ) {
            // skip comment lines
            if( line.startsWith("#") )
                continue;
            // break into columns
            String cols[] = line.split("\\t", -1);
            if( cols.length==9 ) {
                CtdChemical chemical = new CtdChemical();
                chemical.setChemicalName(cols[0]);
                chemical.setChemicalID(cols[1]);
                chemical.setCasRN(cols[2]);
                chemical.setChemicalDefinition(cols[3]);
                chemical.setParentIDs(cols[4]);
                chemical.setChemicalTreeNumbers(cols[5]);
                chemical.setParentTreeNumbers(cols[6]);
                chemical.setSynonyms(cols[7]);
                //chemical.setDrugBankIDs(cols[8]);

                if( chemical.getCasRN()==null || chemical.getCasRN().length()==0 ) {
                    chemicalsWithoutCasRN ++;
                }

                if( mapCasRNToChebiTerm.containsKey(chemical.getCasRN())) {
                    chemicalsWithMatchingCasRN ++;
                    mapChemicals.put(chemical.getChemicalID(), chemical);
                }
                else if( mapMeshToChebi.containsKey(chemical.getChemicalID())) {
                    chemicalsWithMatchingMesh ++;
                    mapChemicals.put(chemical.getChemicalID(), chemical);
                }
                else {
                    Collection<String> chebiAccIds = dao.getChemicalsByName(chemical.getChemicalName());
                    if( chebiAccIds!=null && chebiAccIds.size()==1 ) {
                        chemicalsWithMatchingTermName ++;
                        List<Term> chebiTerms = new ArrayList<>(chebiAccIds.size());
                        for( String chebiAccId: chebiAccIds ) {
                            chebiTerms.add(dao.getTermByAccId(chebiAccId));
                        }
                        chemical.chebiTerms = chebiTerms;
                        mapChemicals.put(chemical.getChemicalID(), chemical);
                    } else {
                        chemicalsWithNonMatchingCasRN++;
                    }
                }
            }
        }
        reader.close();

        PipelineSession session = getSession();
        session.incrementCounter("CHEMICALS_PROCESSED", chemicalsWithMatchingCasRN+chemicalsWithNonMatchingCasRN+chemicalsWithoutCasRN);
        session.incrementCounter("CHEMICALS__LOADED_MATCH_BY_CASRN", chemicalsWithMatchingCasRN);
        session.incrementCounter("CHEMICALS__LOADED_MATCH_BY_MESH", chemicalsWithMatchingMesh);
        session.incrementCounter("CHEMICALS__LOADED_MATCH_BY_TERMNAME", chemicalsWithMatchingTermName);
        session.incrementCounter("CHEMICALS__IGNORED_NOT_MATCH", chemicalsWithNonMatchingCasRN);
        session.incrementCounter("CHEMICALS__WITHOUT_CASRN", chemicalsWithoutCasRN);
    }

    public void setOrganismNames(Set<String> organismNames) {
        this.organismNames = organismNames;
    }

    public Set<String> getOrganismNames() {
        return organismNames;
    }

    public void setChemicalsFile(String chemicalsFile) {
        this.chemicalsFile = chemicalsFile;
    }

    public String getChemicalsFile() {
        return chemicalsFile;
    }

    public String getChemGeneInteractionsFile() {
        return chemGeneInteractionsFile;
    }

    public void setChemGeneInteractionsFile(String chemGeneInteractionsFile) {
        this.chemGeneInteractionsFile = chemGeneInteractionsFile;
    }
}

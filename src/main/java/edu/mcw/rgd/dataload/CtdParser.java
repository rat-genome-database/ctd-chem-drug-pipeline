package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.util.*;

/**
 * @author mtutaj
 * @since 3/3/2017
 */
public class CtdParser {

    private final Logger logStatus = LogManager.getLogger("status");
    private final Logger logRejectedAnnots = LogManager.getLogger("rejectedAnnots");
    private final Logger logRejectedAnnotsSummary = LogManager.getLogger("rejectedAnnotsSummary");

    // taxonomic names of all public species in RGD
    private Set<String> organismNames = new HashSet<>();

    private String chemicalsFile;
    private String chemGeneInteractionsFile;

    // chemicals with MESH having matching CHEBI terms in RGD; keyed by ChemicalID (MESH)
    final private Map<String, CtdChemical> mapChemicals = new HashMap<>();

    // count of rejected annots for given chemical
    private Map<String,Integer> mapRejectedAnnotCounts = new HashMap<>();

    public void init() {
        loadOrganismNames();
    }

    public List<CtdRecord> process(CounterPool counters) throws Exception {

        List<CtdRecord> ctdRecords = new ArrayList<>();

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getChemGeneInteractionsFile());
        downloader.setLocalFile("data/chem_gene_ixns.tsv.gz");
        downloader.setPrependDateStamp(true);

        String localFile = downloader.downloadNew();

        BufferedReader reader = Utils.openReader(localFile);
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
                    counters.increment("INTERACTION_SKIPPED_UNSUPPORTED_ORGANISM");
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
                    counters.increment("INTERACTION_SKIPPED_CHEMICAL_NOT_MATCHING_CHEBI");

                    // log it
                    logRejectedAnnots.debug(interaction.dump("|"));

                    //
                    String key = interaction.dump2("|");
                    Integer count = mapRejectedAnnotCounts.get(key);
                    if( count==null ) {
                        count = 0;
                    }
                    mapRejectedAnnotCounts.put(key, count+1);
                    continue;
                }

                counters.increment("INTERACTIONS_FOR_SPECIES "+organism.toUpperCase());

                CtdRecord rec = new CtdRecord();
                rec.interaction = interaction;
                rec.chemical = mapChemicals.get(chemicalId);

                ctdRecords.add(rec);
            }
        }
        reader.close();

        dumpRejectedAnnotations();

        counters.add("INTERACTIONS__LOADED", ctdRecords.size());

        return ctdRecords;
    }

    void loadOrganismNames() {
        for( int speciesTypeKey: SpeciesType.getSpeciesTypeKeys() ) {
            if( SpeciesType.isSearchable(speciesTypeKey) ) {
                organismNames.add(SpeciesType.getTaxonomicName(speciesTypeKey));
            }
        }
    }

    void dumpRejectedAnnotations() {
        // reverse the hash: annot-count --> list of terms
        MultiValuedMap<Integer, String> map = new ArrayListValuedHashMap<>();
        for( Map.Entry<String,Integer> entry: mapRejectedAnnotCounts.entrySet() ) {
            map.put(entry.getValue(), entry.getKey());
        }

        // sort keys
        ArrayList<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);

        // dump annots: annots with most annotation counts are dumped first
        logRejectedAnnotsSummary.info("CHEMICAL|ID|CAS|ANNOT_COUNT");
        for( int i=keys.size()-1; i>=0; i-- ) {
            int annotCount = keys.get(i);
            Collection<String> coll = map.get(annotCount);
            for( String obj: coll ) {
                logRejectedAnnotsSummary.info(obj + "|" + annotCount);
            }
        }
    }

    public void downloadChemicals(MultiValuedMap mapCasRNToChebiTerm, MultiValuedMap mapMeshToChebi, CtdDAO dao, CounterPool counters) throws Exception {

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

        BufferedReader reader = Utils.openReader(localFile);
        String line;

        while( (line=reader.readLine())!=null ) {
            // skip comment lines
            if( line.startsWith("#") )
                continue;
            // break into columns
            String cols[] = line.split("\\t", -1);
            if( cols.length>=8 ) {
                CtdChemical chemical = new CtdChemical();
                chemical.setChemicalName(cols[0]);
                chemical.setChemicalID(cols[1]);
                chemical.setCasRN(cols[2]);
                chemical.setChemicalDefinition(cols[3]);
                chemical.setParentIDs(cols[4]);
                chemical.setChemicalTreeNumbers(cols[5]);
                chemical.setParentTreeNumbers(cols[6]);
                chemical.setSynonyms(cols[7]);
                // note: in some releases of the file, there is 9 column, and in some it is not
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

        int chemicalsProcessed = chemicalsWithMatchingCasRN+chemicalsWithNonMatchingCasRN+chemicalsWithoutCasRN;
        counters.add("CHEMICALS_PROCESSED", chemicalsProcessed);
        counters.add("CHEMICALS__LOADED_MATCH_BY_CASRN", chemicalsWithMatchingCasRN);
        counters.add("CHEMICALS__LOADED_MATCH_BY_MESH", chemicalsWithMatchingMesh);
        counters.add("CHEMICALS__LOADED_MATCH_BY_TERMNAME", chemicalsWithMatchingTermName);
        counters.add("CHEMICALS__IGNORED_NOT_MATCH", chemicalsWithNonMatchingCasRN);
        counters.add("CHEMICALS__WITHOUT_CASRN", chemicalsWithoutCasRN);

        if( chemicalsProcessed==0 ) {
            throw new Exception("ERROR: chemicals not loaded from file "+getChemicalsFile());
        }
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

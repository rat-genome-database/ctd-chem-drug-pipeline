package edu.mcw.rgd.dataload;


import edu.mcw.rgd.datamodel.Dumpable;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.process.Dumper;

/**
 * @author mtutaj
 * @since 3/22/12
 * <p>represents a row from http://ctdbase.org/reports/CTD_chem_gene_ixns.tsv.gz</p>
 * Fields:<ol>
 * <li>ChemicalName</li>
 * <li>ChemicalID (MeSH identifier)</li>
 * <li>CasRN (CAS Registry Number)</li>
 * <li>GeneSymbol</li>
 * <li>GeneID (NCBI Gene identifier)</li>
 * <li>GeneForms ('|'-delimited list) New!</li>
 * <li>Organism (scientific name)</li>
 * <li>OrganismID (NCBI Taxonomy identifier)</li>
 * <li>Interaction</li>
 * <li>InteractionActions ('|'-delimited list)</li>
 * <li>PubmedIDs ('|'-delimited list)</li>
 * </ol>
 */
public class CtdChemGeneInteraction implements Dumpable {

    private String chemicalName;
    private String chemicalID;
    private String CasRN;
    private String geneSymbol;
    private String geneID;
    private String organism;
    private String organismID;
    private String interaction;
    private String interactionActions;
    private String pubmedIds;
    private String geneForms;
    private int speciesTypeKey; // species type key derived from organism id

    public String getChemicalName() {
        return chemicalName;
    }

    public void setChemicalName(String chemicalName) {
        this.chemicalName = chemicalName;
    }

    public String getChemicalID() {
        return chemicalID;
    }

    public void setChemicalID(String chemicalID) {
        this.chemicalID = chemicalID;
    }

    public String getCasRN() {
        return CasRN;
    }

    public void setCasRN(String casRN) {
        CasRN = casRN;
    }

    public String getGeneSymbol() {
        return geneSymbol;
    }

    public void setGeneSymbol(String geneSymbol) {
        this.geneSymbol = geneSymbol;
    }

    public String getGeneID() {
        return geneID;
    }

    public void setGeneID(String geneID) {
        this.geneID = geneID;
    }

    public String getOrganism() {
        return organism;
    }

    public void setOrganism(String organism) {
        this.organism = organism;
    }

    public String getOrganismID() {
        return organismID;
    }

    public void setOrganismID(String organismID) {
        this.organismID = organismID;

        switch (organismID) {
            case "10116":
                setSpeciesTypeKey(SpeciesType.RAT);
                break;
            case "9606":
                setSpeciesTypeKey(SpeciesType.HUMAN);
                break;
            case "10090":
                setSpeciesTypeKey(SpeciesType.MOUSE);
                break;
            default:
                System.out.println("Unsupported taxonomic organism id " + organismID);
                break;
        }
    }

    public String getInteraction() {
        return interaction;
    }

    public void setInteraction(String interaction) {
        this.interaction = interaction;
    }

    public String getInteractionActions() {
        return interactionActions;
    }

    public void setInteractionActions(String interactionActions) {
        this.interactionActions = interactionActions;
    }

    public String getPubmedIds() {
        return pubmedIds;
    }

    /** in source file, pubmedids come as a list of pubmedids separated by |;
     * we are 'normalizing' it by preceding every pubmedid with string 'PMID:'
     * @param pubmedIds
     */
    public void setPubmedIds(String pubmedIds) {
        if( pubmedIds!=null && pubmedIds.length()>0 ) {
            pubmedIds = "PMID:"+pubmedIds.replace("|", "|PMID:");
        }
        this.pubmedIds = pubmedIds;
    }

    public String getGeneForms() {
        return geneForms;
    }

    public void setGeneForms(String geneForms) {
        this.geneForms = geneForms;
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;
    }

    @Override
    public String dump(String delimiter) {
        return new Dumper(delimiter)
                .put("CHEMICAL", getChemicalName())
                .put("ID", getChemicalID())
                .put("CAS", getCasRN())
                .put("GENE", getGeneSymbol())
                .put("GENE_ID", getGeneID())
                .put("SPECIES", SpeciesType.getCommonName(getSpeciesTypeKey()))
                .put("INTERACTION", getInteraction())
                .put("ACTIONS", getInteractionActions())
                .put("REFS", getPubmedIds())
                .put("GENE_FORMS", getGeneForms())
                .dump();
    }

    public String dump2(String delimiter) {
        return new Dumper(delimiter)
                .put("CHEMICAL", getChemicalName())
                .put("ID", getChemicalID())
                .put("CAS", getCasRN())
                .dump();
    }
}

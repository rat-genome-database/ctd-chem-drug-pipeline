package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.ontologyx.Term;

import java.util.*;

/**
 * @author mtutaj
 * @since 3/21/12
 * <p>
 * Preview of a line from chemicals.tsv file (note: in original file
 * <pre>
 * # Fields:
 * # ChemicalName $ ChemicalID $ CasRN $ ParentIDs $ ChemicalTreeNumbers $ ParentTreeNumbers $ Synonyms
 * #
 * bevonium $ MESH:C000002 $ 33371-53-8 $ MESH:D001561 $ D02.241.223.601.238.306/C000002|D02.241.511.085/C000002 $ D02.241.223.601.238.306|D02.241.511.085 $ 2-(hydroxymethyl)-N,N-dimethylpiperidinium benzilate|Acabel|bevonium methylsulfate|bevonium methyl sulfate|bevonium sulfate (1:1)|CG 201|piribenzil methyl sulfate*
 * </pre>
 * Fields:<dl>
 * <dt>ChemicalName</dt>
 * <dt>ChemicalID</dt> <dd>MeSH identifier; use to link to CTD chemical pages</dd>
 * <dt>CasRN</dt> <dd>CAS Registry Number</dd>
 * <dt>ChemicalDefinition</dt>
 * <dt>ParentIDs</dt> <dd>identifiers of the parent terms; '|'-delimited list</dd>
 * <dt>ChemicalTreeNumbers</dt> <dd>identifiers of the chemical's nodes; '|'-delimited list</dd>
 * <dt>ParentTreeNumbers</dt> <dd>identifiers of the parent nodes; '|'-delimited list</dd>
 * <dt>Synonyms</dt> <dd>'|'-delimited list</dd>
 * </dl>
 * Each chemical occurs in at least one node of the hierarchy. To navigate the hierarchy, use ParentIDs or ParentTreeNumbers.
 */
public class CtdChemical {

    private String chemicalName;
    private String chemicalID; // MESH acc id
    private String CasRN;
    private String chemicalDefinition;
    private List<String> parentIDs = new ArrayList<>();
    private List<String> chemicalTreeNumbers = new ArrayList<>();
    private List<String> parentTreeNumbers = new ArrayList<>();
    private List<String> synonyms = new ArrayList<>();

    Collection<Term> chebiTerms; // chebi terms matching incoming chemical by name

    /**
     * two chemicals are identical if they have same chemical id
     * @param obj
     * @return
     */
    public boolean equals(Object obj) {
        CtdChemical chemical = (CtdChemical) obj;
        return chemical.getChemicalID().equals(this.getChemicalID());
    }

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

    public String getChemicalDefinition() {
        return chemicalDefinition;
    }

    public void setChemicalDefinition(String chemicalDefinition) {
        this.chemicalDefinition = chemicalDefinition;
    }

    public List<String> getParentIDs() {
        return parentIDs;
    }

    public void setParentIDs(String parentIDs) {
        this.parentIDs = splitByBar(parentIDs);
    }

    public List<String> getChemicalTreeNumbers() {
        return chemicalTreeNumbers;
    }

    public void setChemicalTreeNumbers(String chemicalTreeNumbers) {
        this.chemicalTreeNumbers = splitByBar(chemicalTreeNumbers);
    }

    public List<String> getParentTreeNumbers() {
        return parentTreeNumbers;
    }

    public void setParentTreeNumbers(String parentTreeNumbers) {
        this.parentTreeNumbers = splitByBar(parentTreeNumbers);
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(String synonyms) {
        this.synonyms = splitByBar(synonyms);
    }

    private List<String> splitByBar(String str) {
        if( str.length()>0 ) {
            String[] words = str.split("\\|");
            return Arrays.asList(words);
        }
        else {
            return Collections.emptyList();
        }
    }
}

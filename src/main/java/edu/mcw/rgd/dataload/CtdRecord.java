package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.ontology.Annotation;

import java.util.LinkedList;
import java.util.List;

/**
 * @author mtutaj
 * @since 3/21/12
 * represents a chemical being processed
 */
public class CtdRecord {

    public CtdChemGeneInteraction interaction;
    public CtdChemical chemical; // chemical matching the interaction

    public Gene gene; // gene matching geneid|symbol|Organism in interaction
    public List<Gene> homologs;  // matching ortholog(s)

    // annots produced from incoming interaction line -- to be sync-ed against database
    public List<Annotation> incomingAnnots = new LinkedList<>();
    public List<Annotation> inRgdAnnots = new LinkedList<>();

    int recno;

    // called before QC
    public void initQC() {
        gene = null;
        homologs = null;

        incomingAnnots.clear();
        inRgdAnnots.clear();
    }
}

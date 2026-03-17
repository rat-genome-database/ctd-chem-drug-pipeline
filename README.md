# ctd-chem-drug-pipeline

Imports chemical-gene interaction annotations from the [Comparative Toxicogenomics Database (CTD)](http://ctdbase.org) into the RGD database.

## Pipeline Summary

1. **Load CHEBI mappings** — Queries RGD for CHEBI ontology terms that have CAS Registry Number or MESH ID synonyms, building lookup maps for chemical matching.

2. **Download and match chemicals** — Downloads `CTD_chemicals.tsv.gz` from CTD and matches each chemical to a CHEBI term by CasRN, MESH ID, or normalized term name. Unmatched chemicals are discarded.

3. **Download and parse interactions** — Downloads `CTD_chem_gene_ixns.tsv.gz` from CTD. Each interaction links a chemical to a gene (by NCBI Gene ID) in a specific organism. Interactions for unmapped chemicals or unsupported species are rejected.

4. **Gene matching and ortholog expansion** — For each interaction, the pipeline looks up the gene in RGD by NCBI Gene ID (primary) or gene symbol/alias (fallback). For rat, mouse, and human genes, it retrieves cross-species homologs to create ortholog-inferred annotations.

5. **Create annotations** — Builds annotation objects with CHEBI term, evidence code (EXP for direct interactions, ISO for ortholog-inferred), PubMed references, and interaction qualifiers.

6. **QC and merge** — Groups incoming annotations by a unique key (term + gene + evidence + qualifier + qualifier2 + associatedWith + reference + withInfo). Merges annotations that share the same key by consolidating notes and PubMed IDs into combined entries.

7. **Database sync** — Compares incoming annotations against existing RGD annotations: inserts new ones, updates changed notes/references, and refreshes timestamps on unchanged matches.

8. **Delete obsolete annotations** — Removes annotations not seen in the current run (with a safety limit of 500,000 deletions to prevent accidental mass removal).

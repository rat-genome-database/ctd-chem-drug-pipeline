2020-04-14
  updated jar dependencies

2019-11-14
  fixed loading of CAS RN ids
  added support for species other than rat/mouse/human

2019-11-01
  improved logging (no more System.out.println)

2019-06-19
  sped up the annotation loading part
   
v. 1.3.11, build Feb 19, 2019
  - updated dependencies

v. 1.3.10, build Sep 13, 2018
  - moved from subversion to github

v. 1.3.9, build Jan 3, 2018
  - fixed logging, master script, dropped dead code

v. 1.3.8, build Jul 31, 2017
  - updated from ANT to GRADLE

v. 1.3.7, build May 12, 2017
  - implemented merging of annotations differing only by XREF_SOURCE and NOTES

v. 1.3.6, build Mar 15, 2017
v. 1.3.5, build Mar 14, 2017
v. 1.3.4, build Mar 06, 2017
  - fixed handling of annotation notes

v. 1.3.3, build Mar 03, 2017
  - added matching of chemicals by term name, in addition to matching by CasRN and MESH_ID

v. 1.3.2, build Jan 25, 2017
  - fixed handling of annot notes: unnecessary updates of notes caused creation of massive
    400GB+ update_annot_notes.log, and unnecessary bulk updates in Oracle db
  - optimized some Oracle queries
  - added logging of inserted 'xref_mesh' synonyms

v. 1.3.1, build Dec 28, 2016
  - reduced record queue size from 6000 to 2000, to avoid running of memory

v. 1.3.0, build Dec 12, 2016
  - added additional matching of CHEBI terms by MESH ids (in addition to matching by CAS) -- per RGDD-1319

v. 1.2.2, build Aug 22, 2016
  - updated jars; fixed loading of CAS numbers -- per RGDD-1256

v. 1.2.1, build Feb 8, 2016
  - updated jars; fixed ortholog handling per RGDD-1152

v. 1.2.0, build Jun 11, 2015
  - updated jars; fixed loading of CasRN ids

v. 1.1.11, build Jun 24, 2014
  - updated rgdcore (to support inserting synonyms with new fields)

v. 1.1.10, build Jun 16, 2014
  - limited ortholog handling to rat, mouse and human

v. 1.1.9, build Apr 14, 2014
  - improved logging (added logs for no matches and multi matches)
  - when combining notes, the combined notes are sorted

v. 1.1.8, build Dec 20, 2013
  - improved logging (added separate logs for inserted, updated, deleted annotations)

v. 1.1.7, build Dec 13, 2013
  - updated rgdcore.jar
  - weak orthologs (other homologs) are used in addition to regular orthologs to create orthologous annotations
    per RGDD-854
  - fixed a bug that was assigning wrong ISO/EXP codes and WITH_INFO fields

v. 1.1.6, build Oct 14, 2013
  - updated rgdcore.jar
  - processing logic changed, so it won't longer throw exceptions internally
    at the expense of processing speed (slower) but with increased accuracy of processing
  - obsolete annotations are deleted at the end of processing - per RGDD-785
    (in-RGD annotations that have not been processed by the pipeline, are considered to be obsolete annotations)
    also to avoid spurious deletion of huge number of annotations, a limit has been set up:
    if there are more annotations than the limit, no annotations will be deleted

v. 1.1.5, build July 23, 2013
  - updated rgdcore.jar
  - updated file parsing logic for CTD_Chemicals file: new column DrugBankIDs added - per RGDD-760

v. 1.1.4, build Dec 28, 2012
  - updated rgdcore.jar, because columns TERM and EXP_RGD_ID have been dropped from unique
    index on FULL_ANNOT table - per RGDD-555

v. 1.1.3, build August 23, 2012
  - updated rgdcore.jar, because columns TERM and EXP_RGD_ID have been dropped from unique
    index on FULL_ANNOT table - per RGDD-555

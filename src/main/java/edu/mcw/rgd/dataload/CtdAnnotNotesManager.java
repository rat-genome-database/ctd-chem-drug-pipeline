package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;

/**
 * Created by mtutaj on 3/6/2017.
 */
public class CtdAnnotNotesManager {

    public static String computeAnnotKey(Annotation annot) {
        return annot.getTermAcc() + "~"
                + annot.getAnnotatedObjectRgdId() + "~"
                + (annot.getRefRgdId()!=null ? annot.getRefRgdId() : 0) + "~"
                + annot.getEvidence() + "~"
                + Utils.defaultString(annot.getWithInfo()) + "~"
                + Utils.defaultString(annot.getQualifier());
    }
}

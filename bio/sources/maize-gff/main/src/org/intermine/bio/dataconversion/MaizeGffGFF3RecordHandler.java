package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2015 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for the MaizeGff dataset via GFF files.
 */

public class MaizeGffGFF3RecordHandler extends GFF3RecordHandler
{

    /**
     * Create a new MaizeGffGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public MaizeGffGFF3RecordHandler (Model model) {
        super(model);
        // refsAndCollections controls references and collections that are set from the
        // Parent= attributes in the GFF3 file.
        refsAndCollections.put("Exon", "transcripts");
        refsAndCollections.put("ThreePrimeUTR", "transcripts");
        refsAndCollections.put("FivePrimeUTR", "transcripts");
        refsAndCollections.put("CDS", "transcript");
        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("MiRNA", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
    	//custom logs
//        Item ft = getFeature();
//        String clsName = ft.getClassName();
//        if("exon".equalsIgnoreCase(record.getType()) || "exon".equalsIgnoreCase(clsName)) {
//            System.out.println("DEBUG MESSAGE: " + record.getType());
//        }
//        if(record.getType().equalsIgnoreCase("gene")){
//            Item feature = getFeature();
//            System.out.println("Feature primaryIdentifier: " + feature.getAttribute("primaryIdentifier").getValue());
//            String name = record.getAttributes().get("Name").iterator().next();
//            System.out.println("Name for gene: " + name);
//            feature.setAttribute("primaryIdentifier", name);
//            System.exit(1);
//            if (feature.getAttribute("primaryIdentifier") != null) {
//                String secondary = feature.getAttribute("primaryIdentifier").getValue();
//                feature.setAttribute("secondaryIdentifier", secondary.substring(secondary.indexOf(':') + 1));
//            }
//        }
        if(record.getType().equalsIgnoreCase("gene") || record.getType().equalsIgnoreCase("transcript")) {
            Item feature = getFeature();
            String primaryIdentifier = feature.getAttribute("primaryIdentifier").getValue();
            feature.setAttribute("primaryIdentifier", primaryIdentifier.substring(primaryIdentifier.indexOf(':') + 1));
        }
    }

}

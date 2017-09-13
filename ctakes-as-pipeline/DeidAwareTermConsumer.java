/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.ctakes.core.util.collection.CollectionMap;
import org.apache.ctakes.dictionary.lookup2.concept.Concept;
import org.apache.ctakes.dictionary.lookup2.consumer.AbstractTermConsumer;
import org.apache.ctakes.dictionary.lookup2.consumer.UmlsConceptCreator;
import org.apache.ctakes.dictionary.lookup2.consumer.DefaultUmlsConceptCreator;
import org.apache.ctakes.dictionary.lookup2.textspan.TextSpan;
import org.apache.ctakes.dictionary.lookup2.util.CuiCodeUtil;
import org.apache.ctakes.dictionary.lookup2.util.SemanticUtil;
import org.apache.ctakes.typesystem.type.constants.CONST;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;

import java.util.*;

import static org.apache.ctakes.typesystem.type.constants.CONST.*;


/**
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 1/9/14
 */
final public class DeidAwareTermConsumer extends AbstractTermConsumer {

   private String[] phiArray = new String[]{ "[org_name]", "[pt_name]", "[doc_name]", "[date]",
                                              "[address]", "[phone]", "[fax]", "[age]", "[email]",
                                              "[ip_addr]", "[url]", "[dev_id]", "[vehicle_id]",
                                              "[cert_num]", "[acct_num]", "[plan_benf_num]",
                                              "[med_rec_num]", "[ssn]", "[other_id]"};
   final private UmlsConceptCreator _umlsConceptCreator;

   public DeidAwareTermConsumer( final UimaContext uimaContext, final Properties properties ) {
      this( uimaContext, properties, new DefaultUmlsConceptCreator() );
   }

   public DeidAwareTermConsumer( final UimaContext uimaContext, final Properties properties,
                               final UmlsConceptCreator umlsConceptCreator ) {
      super( uimaContext, properties );
      _umlsConceptCreator = umlsConceptCreator;
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void consumeTypeIdHits( final JCas jcas, final String codingScheme, final int cTakesSemantic,
                                  final CollectionMap<TextSpan, Long, ? extends Collection<Long>> textSpanCuis,
                                  final CollectionMap<Long, Concept, ? extends Collection<Concept>> cuiConcepts )
         throws AnalysisEngineProcessException {
     List<IdentifiedAnnotation> toRemove = new ArrayList<>();

     // Find the spans associated with de-id strings:
     String docText = jcas.getDocumentText();
     for(String phiString : phiArray){
         int searchInd=0;
         int startInd=0;
         int endInd;
         while((startInd = docText.indexOf(phiString, searchInd)) >= 0){
             endInd = startInd + phiString.length();
             for(IdentifiedAnnotation covered : JCasUtil.selectCovered(jcas, IdentifiedAnnotation.class, startInd, endInd)){
                 toRemove.add(covered);
             }
             searchInd = startInd+1;
             //System.err.println("Found phi string " + phiString + " at index: " + startInd + " to: " + endInd);
         }
     }

     // Remove all those identified annotations that fall within de-id strings.
     for(IdentifiedAnnotation annot : toRemove){
         annot.removeFromIndexes();
     }
      // Collection of UmlsConcept objects
      final Collection<UmlsConcept> umlsConceptList = new ArrayList<>();
      try {
         for ( Map.Entry<TextSpan, ? extends Collection<Long>> spanCuis : textSpanCuis ) {
            umlsConceptList.clear();
            for ( Long cuiCode : spanCuis.getValue() ) {
               umlsConceptList.addAll(
                     createUmlsConcepts( jcas, codingScheme, cTakesSemantic, cuiCode, cuiConcepts ) );
            }
            final FSArray conceptArr = new FSArray( jcas, umlsConceptList.size() );
            int arrIdx = 0;
            for ( UmlsConcept umlsConcept : umlsConceptList ) {
               conceptArr.set( arrIdx, umlsConcept );
               arrIdx++;
            }
            final IdentifiedAnnotation annotation = createSemanticAnnotation( jcas, cTakesSemantic );
            annotation.setTypeID( cTakesSemantic );
            annotation.setBegin( spanCuis.getKey().getStart() );
            annotation.setEnd( spanCuis.getKey().getEnd() );
            annotation.setDiscoveryTechnique( CONST.NE_DISCOVERY_TECH_DICT_LOOKUP );
            annotation.setOntologyConceptArr( conceptArr );
            annotation.addToIndexes();
         }
      } catch ( CASRuntimeException crtE ) {
         // What is really thrown?  The jcas "throwFeatMissing" is not a great help
         throw new AnalysisEngineProcessException( crtE );
      }
   }

   static private IdentifiedAnnotation createSemanticAnnotation( final JCas jcas, final int cTakesSemantic ) {
      switch ( cTakesSemantic ) {
         case NE_TYPE_ID_DRUG: {
            return new MedicationMention( jcas );
         }
         case NE_TYPE_ID_ANATOMICAL_SITE: {
            return new AnatomicalSiteMention( jcas );
         }
         case NE_TYPE_ID_DISORDER: {
            return new DiseaseDisorderMention( jcas );
         }
         case NE_TYPE_ID_FINDING: {
            return new SignSymptomMention( jcas );
         }
         case NE_TYPE_ID_LAB: {
            return new LabMention( jcas );
         }
         case NE_TYPE_ID_PROCEDURE: {
            return new ProcedureMention( jcas );
         }
      }
      return new EntityMention( jcas );
   }

   private Collection<UmlsConcept> createUmlsConcepts( final JCas jcas,
                                                       final String codingScheme,
                                                       final int cTakesSemantic,
                                                       final Long cuiCode,
                                                       final CollectionMap<Long, Concept, ? extends Collection<Concept>> conceptMap ) {
      final Collection<Concept> concepts = conceptMap.getCollection( cuiCode );
      if ( concepts == null || concepts.isEmpty() ) {
         return Collections.singletonList( createSimpleUmlsConcept( jcas, codingScheme,
               CuiCodeUtil.getInstance().getAsCui( cuiCode ) ) );
      }
      final Collection<UmlsConcept> umlsConcepts = new HashSet<>();
      for ( Concept concept : concepts ) {
         final Collection<Integer> allSemantics = concept.getCtakesSemantics();
         if ( !allSemantics.contains( cTakesSemantic ) ) {
            continue;
         }
         boolean added = false;
         final Collection<String> tuis = concept.getCodes( Concept.TUI );
         if ( !tuis.isEmpty() ) {
            for ( String tui : tuis ) {
               // the concept could have tuis outside this cTakes semantic group
               if ( SemanticUtil.getTuiSemanticGroupId( tui ) == cTakesSemantic ) {
                  umlsConcepts.addAll( _umlsConceptCreator.createUmlsConcepts( jcas, codingScheme, tui, concept ) );
                  added = true;
               }
            }
         }
         if ( !added ) {
            umlsConcepts.addAll( _umlsConceptCreator.createUmlsConcepts( jcas, codingScheme, null, concept ) );
         }
      }
      return umlsConcepts;
   }

   static private UmlsConcept createSimpleUmlsConcept( final JCas jcas, final String codingScheme, final String cui ) {
      final UmlsConcept umlsConcept = new UmlsConcept( jcas );
      umlsConcept.setCodingScheme( codingScheme );
      umlsConcept.setCui( cui );
      return umlsConcept;
   }

}


//package org.apache.ctakes.core.cc;

//import org.apache.ctakes.core.cc.AbstractJdbcWriter;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.util.OntologyConceptUtil;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Logger;
import org.apache.uima.util.Level;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * JCas Annotator to write a table to an i2b2 sql database using jdbc
 *
 * @since 10/2017 
 */
@PipeBitInfo(
      name = "JDBC Writer for i2b2",
      description = "Stores extracted information and document metadata in a database.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { PipeBitInfo.TypeProduct.DOCUMENT_ID, PipeBitInfo.TypeProduct.IDENTIFIED_ANNOTATION }
)
public class I2b2JdbcWriter extends AbstractJdbcWriter {

   private static Logger logger = null;

   // Parameter names for the desc file
   static public final String PARAM_VECTOR_TABLE = "VectorTable";

   static protected final String SPAN_START_LABEL = "START";
   static protected final String SPAN_END_LABEL = "END";

   static private final String AFFIRMED = "1";
   static private final String NEGATED = "-1";

   
   static private final String INSTANCE_NUM_DEFAULT = "-1";     // when not a CUSTOM one
   static private final String MODIFIER_CD_DEFAULT = "@";     // when not a CUSTOM one
   static private final String MODIFIER_CD_BEGIN = "CUSTOM:BEGIN:";
   static private final String MODIFIER_CD_END = "CUSTOM:END:";
   static private final String MODIFIER_CD_SENT = "CUSTOM:SENT:";
   static private final String MODIFIER_CD_POLARITY = "CUSTOM:POLARITY:";
   static private final String MODIFIER_CD_SUBJECT = "CUSTOM:SUBJECT:";
   static private final String MODIFIER_CD_SECTION = "CUSTOM:SECTION:";
   static private final String MODIFIER_CD_UNCERTAINTY = "CUSTOM:UNCERTAINTY:";
   static private final String MODIFIER_CD_BODY_LOCATION = "CUSTOM:BODY_LOCATION:";
   static private final String MODIFIER_CD_SEVERITY = "CUSTOM:SEVERITY:";
   static private final String MODIFIER_CD_DOC_TIME_REL = "CUSTOM:DOC_TIME_REL:";
   
   static private final String CTAKES_VERSION = "CTAKES_4.0.0_PL_171129";

   public enum I2b2FieldInfo implements AbstractJdbcWriter.FieldInfo {
      ENCOUNTER_NUM( 1, "encounter_num", String.class ),
      PATIENT_NUM( 2, "patient_num", String.class ),
      CONCEPT_CD( 3, "concept_cd", String.class ),
      PROVIDER_ID( 4, "provider_id", String.class ),
      START_DATE( 5, "start_date", String.class ),
      MODIFIER_CD( 6, "modifier_cd", String.class ),
      INSTANCE_NUM( 7, "instance_num", String.class ),
      VALTYPE_CD( 8, "valtype_cd", String.class ),
      TVAL_CHAR( 9, "tval_char", String.class ),
      I2B2_OBERVATION_BLOB( 10, "observation_blob", String.class ),
      SOURCESYSTEM_CD( 11, "sourcesystem_cd", String.class);
	  
      final private String name;
      final private int index;
      final private Class<?> clazz;

      I2b2FieldInfo( final int index, final String name, final Class<?> valueClass ) {
         this.name = name;
         this.index = index;
         clazz = valueClass;
      }

      @Override
      public String getFieldName() {
         return name;
      }

      @Override
      public int getFieldIndex() {
         return index;
      }

      @Override
      public Class<?> getValueClass() {
         return clazz;
      }
   }

   @ConfigurationParameter(name = PARAM_VECTOR_TABLE)
   private String tableName;


   /**
    * {@inheritDoc}
    */
   @Override
   public void initialize(UimaContext context) throws ResourceInitializationException {
      super.initialize(context);
      if(logger == null) {
          logger = context.getLogger();
      }
      logger.log(Level.INFO, "Table name = " + tableName);
   }


   /**
    * {@inheritDoc}
    */
   @Override
   protected Collection<TableInfo> getTableInfos() {
      final TableInfo tableInfo = new TableInfo() {
         @Override
         public String getTableName() {
            return tableName;
         }

         @Override
         public FieldInfo[] getFieldInfos() {
            return I2b2FieldInfo.values();
         }
      };
      return Collections.singletonList( tableInfo );
   }


   /**
    * {@inheritDoc}
    * Like process for AEs
    */
   @Override
   protected void writeJCasInformation( final JCas jcas, 
                                        final String instance,
                                        final int encounterNum,
                                        final long patientNum, final String providerId,
                                        final Timestamp startDate ) throws SQLException {
	   
      JCas deidView = null;
      try {
    	  deidView = jcas.getView("DeidView");
      } catch(CASException e) {
    	  throw new RuntimeException(e);
      }

      logger.log(Level.INFO,"view = " + deidView.getViewName());
      writeConceptRows( deidView, instance, Long.toString(patientNum), Integer.toString(encounterNum), providerId );

   }

private void addNonNull(List<String> list, int i) {
   addNonNull(list, Integer.toString(i));
}

private void addNonNull(List<String> list, String s) {

   if (s==null) {
       list.add("");
       return;
   }

   list.add(s);

}

	private void writeConceptRows(final JCas jcas, final String instance, final String patient, final String encounter, final String provider) throws SQLException {

		final Collection<IdentifiedAnnotation> annotations = JCasUtil.select(jcas, IdentifiedAnnotation.class);
		if (annotations.isEmpty()) {
			logger.log(Level.WARNING, "No annotations found. Patient, encounter, provider =  " + patient + ", " + encounter + ", " + provider);
			return;
		}
		
		// Do not use try with resource as it will close the prepared statement, which we do not yet want
		final PreparedStatement preparedStatement = _tableSqlInfoMap.get( tableName ).getPreparedStatement();
		int batchCount = _tableSqlInfoMap.get( tableName ).getBatchCount();

		final Map<I2b2FieldInfo, Object> fieldInfoValues = new EnumMap<>(I2b2FieldInfo.class);
            
            // Example from Thomas DeSain for    FileName: 2597_0036_0002.txt.output.txt
            // <PATIENT_NUM>_<ENCOUNTER_NUM>_<INSTANCE_NUM>.txt.output.txt
            // Document_ID = 2597_0036_0002.txt.output.txt Patient_Num = 2597 Encounter_Num = 0036 Instance_Num = 0002
            // Note the above example includes .txt.output.txt because it used to read text files
            // That part is now left off
            fieldInfoValues.put(I2b2FieldInfo.ENCOUNTER_NUM, encounter+"_"+patient+"_"+instance);
	    fieldInfoValues.put(I2b2FieldInfo.PATIENT_NUM, patient);
	    fieldInfoValues.put(I2b2FieldInfo.PROVIDER_ID, provider);
	    fieldInfoValues.put(I2b2FieldInfo.START_DATE, "" );   // was not filled in by I2b2ReadyFileWriter, not expected to be filled in now
	    fieldInfoValues.put(I2b2FieldInfo.SOURCESYSTEM_CD, CTAKES_VERSION);
	    fieldInfoValues.put(I2b2FieldInfo.INSTANCE_NUM, INSTANCE_NUM_DEFAULT);

		for (IdentifiedAnnotation annotation : annotations) {

			logger.log(Level.FINE, "Anno = " + annotation);
			fieldInfoValues.put(I2b2FieldInfo.I2B2_OBERVATION_BLOB, annotation.getCoveredText());
			
			ArrayList<String> modifierCds = new ArrayList<String>();
			ArrayList<String> tvalChars = new ArrayList<String>(); // Note I knew of an easier way to deal with Pair in Java, I wouldn't rely on modifierCd(1) being associated with tVals(1). 
			final Set<String> codes = new HashSet<String>(); 
		        // A Cui may belong to multiple Tuis, making multiple UmlsConcept objects (one per tui).
		        // I2b2 does NOT want multiple rows of a single Cui just because it has multiple tuis.
			codes.addAll(OntologyConceptUtil.getCuis(annotation));

			// MODIFIER_CD column contains "@" or an attribute name formatted like this "CUSTOM:attributename:" such as "CUSTOM:SECTION:" for "SECTION" 
			modifierCds.add(MODIFIER_CD_DEFAULT); // 

			addNonNull(tvalChars, "");
			
			modifierCds.add(MODIFIER_CD_BEGIN); 
			addNonNull(tvalChars, annotation.getBegin());
			
			modifierCds.add(MODIFIER_CD_END); 
			addNonNull(tvalChars, annotation.getEnd());
			
			modifierCds.add(MODIFIER_CD_POLARITY); 
			addNonNull(tvalChars, (annotation.getPolarity() < 0 ? NEGATED : AFFIRMED));
			
			modifierCds.add(MODIFIER_CD_SUBJECT); 
			addNonNull(tvalChars, annotation.getSubject());
			
			modifierCds.add(MODIFIER_CD_SECTION); 
			addNonNull(tvalChars, annotation.getSegmentID());
			
			modifierCds.add(MODIFIER_CD_UNCERTAINTY); 
			addNonNull(tvalChars, annotation.getUncertainty());
			
			modifierCds.add(MODIFIER_CD_BODY_LOCATION); 
			addNonNull(tvalChars, "");
			
			modifierCds.add(MODIFIER_CD_SEVERITY); 
			addNonNull(tvalChars, "");
			
			modifierCds.add(MODIFIER_CD_DOC_TIME_REL); 
			addNonNull(tvalChars, getDocTimeRel(annotation));
			
			modifierCds.add(MODIFIER_CD_SENT); 
			addNonNull(tvalChars, getSentenceCoverage(jcas, annotation));
			
			for (String code : codes) {
				fieldInfoValues.put(I2b2FieldInfo.CONCEPT_CD, code);
				for (int i = 0; i<modifierCds.size(); i++) {
					String modifierCd = modifierCds.get(i);
					fieldInfoValues.put(I2b2FieldInfo.MODIFIER_CD, modifierCd);
					if (modifierCd.equals(MODIFIER_CD_DEFAULT)) {
						fieldInfoValues.put(I2b2FieldInfo.VALTYPE_CD, "");
						fieldInfoValues.put(I2b2FieldInfo.TVAL_CHAR, tvalChars.get(i)); // "" when MODIFIER_CD_DEFAULT
					} else {
						fieldInfoValues.put(I2b2FieldInfo.VALTYPE_CD, "T");
						fieldInfoValues.put(I2b2FieldInfo.TVAL_CHAR, tvalChars.get(i)); // Attribute value such as offset, or text of sentence
					}
			        logger.log(Level.FINE, "before: batchCount = " + batchCount);
                    logger.log(Level.FINE,"preparedStatement = " + preparedStatement);
					batchCount = writeTableRow( preparedStatement, batchCount, fieldInfoValues );
                    logger.log(Level.FINE," after: batchCount = " + batchCount);
				}
			}
		}
        _tableSqlInfoMap.get( tableName ).setBatchCount( batchCount );
	}   

	static private String getDocTimeRel(final IdentifiedAnnotation annotation) {
		if (annotation == null || !(annotation instanceof EventMention)) {
			return "";
		}
		final Event event = ((EventMention) annotation).getEvent();
		if (event != null && event.getProperties() != null && event.getProperties().getDocTimeRel() != null) {
			return event.getProperties().getDocTimeRel();
		}
		return "";
	}

        static private String getSentenceCoverage( final JCas jcas, final IdentifiedAnnotation annotation ) {
	      final Collection<Sentence> entitySentences = JCasUtil.selectCovering( jcas,
	            Sentence.class, annotation.getBegin(), annotation.getEnd() );
              int MAX_TVAL_LEN = 4000; 
              int window = MAX_TVAL_LEN / 2; // count of characters not bytes
              if (annotation.getCoveredText().length() > window) {
                  logger.log(Level.WARNING,"Annotation is longer than MAX_TVAL_LEN / 2. MAX_TVAL_LEN = " + MAX_TVAL_LEN + "\n'" + annotation.getCoveredText() + "'");
                  return annotation.getCoveredText().substring(0, window);
              }

	      final StringBuilder sb = new StringBuilder();
	      for ( Sentence sentence : entitySentences ) {
	         sb.append( sentence.getCoveredText() ).append( "\n" );
	      }
              String s = sb.toString().trim();
              if (s.length() <=  window) { 
                  return s;
              }
              logger.log(Level.WARNING,"SentenceCoverage is longer than MAX_TVAL_LEN / 2. MAX_TVAL_LEN = " + MAX_TVAL_LEN + "\n'" + s + "'");

              // get a window around the entity that is at most 'window' long
              // For this unusual case, only look at the first sentence covering the annotation
	      for ( Sentence sentence : entitySentences ) {
                     int begin = sentence.getBegin();
                     int end = sentence.getEnd();
                     if ((end - begin) > window) {
                       begin = (annotation.getEnd() - (window/2));
                       if (begin < sentence.getBegin()) begin = sentence.getBegin();
                       if (begin > annotation.getBegin()) return sentence.getCoveredText().substring(0, window);
                       end = annotation.getEnd() + (window/2);
                       if (end > sentence.getEnd()) end = sentence.getEnd();

                     }
	             return sentence.getCoveredText().substring(begin - sentence.getBegin(), end - sentence.getBegin());
	      }
          
              // if no sentence found covering the annotation, return just the annotation's covered text.
              // This should never happen so don't bother with doing anything fancy like returning a larger string that 
              // includes annotation in the middle.
              logger.log(Level.SEVERE, "No sentence found covering annotation\n" + annotation + "\n");
              return annotation.getCoveredText(); // verified earlier in this method that this is not longer than window

	}

//		private Collection<String> buildLines() {
//			final Collection<String> conceptLines = new ArrayList<>(attributes.size() + 1);
//			final StringBuffer line = new StringBuffer();
//			final String conceptLine = line.append(encounterNum) // "ENCOUNTER_NUM" VARCHAR2(1000 BYTE)
//					.append(patientNum)  // "PATIENT_NUM" VARCHAR2(50 BYTE)
//					.append(conceptCd)   // "CONCEPT_CD" VARCHAR2(50 BYTE)    // a CUI or other code
//					.append(providerId)  // "PROVIDER_ID" VARCHAR2(50 BYTE)   // null
//					.append(startDate)   // "START_DATE" VARCHAR2(50 BYTE)    // (empty string)
//					.append(modifierCd)  // "MODIFIER_CD" VARCHAR2(100 BYTE)  // @ in this case; for attributes, CUSTOM:attr-name: such as CUSTOM:SENT:
//					.append(instanceNum) // "INSTANCE_NUM" VARCHAR2(100 BYTE),
//					.append("")          // "VALTYPE_CD" VARCHAR2(50 BYTE)    // Attribute has T 
//					.append("")          // "TVAL_CHAR" CLOB                  // Attribute has attribute value
//					.append("|||||||")   // "NVAL_NUM", "VALUEFLAG_CD", "QUANTITY_NUM", "UNITS_CD", "END_DATE", "LOCATION_CD", "CONFIDENCE_NUM"  
//					.append(CTAKES_VERSION)    // "SOURCESYSTEM_CD"           // CTAKES_*
//					.append(observationBlob)   // "OBSERVATION_BLOB"          // such as 'hearing loss'
//					.append("\n").toString();
//			conceptLines.add(conceptLine);
//			for (ConceptAttribute attribute : attributes) {
//				line.setLength(0);
//				final String attributeLine = line.append(encounterNum)
//						.append(patientNum).append(conceptCd)
//						.append(providerId).append(startDate)
//                      .append(attribute.n)        // "MODIFIER_CD" VARCHAR2(100 BYTE)  // for attributes CUSTOM:attr-name: such as CUSTOM:SENT:
//                      .append(instanceNum)
//						.append("T")                // always "T" for attributes
//						.append(attribute.v)        // "TVAL_CHAR" CLOB,  // Attribute value such as offset, or text of sentence
//						.append("|||||||")          // "NVAL_NUM", "VALUEFLAG_CD", "QUANTITY_NUM", "UNITS_CD", "END_DATE", "LOCATION_CD", "CONFIDENCE_NUM"
//						.append(CTAKES_VERSION)     // "SOURCESYSTEM_CD"  // CTAKES_*
//						.append(observationBlob)    // "OBSERVATION_BLOB" // hearing loss
//						.append("\n").toString();
//				conceptLines.add(attributeLine);
//			}
//			return conceptLines;
//		}


}

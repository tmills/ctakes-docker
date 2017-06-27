import org.apache.ctakes.core.util.OntologyConceptUtil;
import org.apache.ctakes.core.util.SourceMetadataUtil;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

//import javax.annotation.concurrent.Immutable;
import java.io.*;
import java.sql.Timestamp;
import java.util.*;

/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 5/4/2016
 */
//@Immutable
final public class I2b2ReadyFileWriter extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = Logger.getLogger( "I2b2ReadyFileWriter" );
   static private final String CTAKES_VERSION = "CTAKES_4.0.0";


   static public final String PARAM_OUTPUTDIR = "OutputDirectory";

   static private final String AFFIRMED = "1";
   static private final String NEGATED = "-1";

   static private final String PREFIX_CUI = "CUI:";
   static private final String MODIFIER_DEFAULT = "@";
   static private final String MODIFIER_CD_CUI = "CUSTOM:CUI:";
   static private final String MODIFIER_CD_TUI = "CUSTOM:TUI:";
   static private final String MODIFIER_CD_CODE = "CUSTOM:CODE:";
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
   
   static private final String DEID_VIEW = "DeidView";

   /**
    * Name of configuration parameter that must be set to the path of a directory into which the
    * output files will be written.
    */
   @ConfigurationParameter( name = PARAM_OUTPUTDIR,
         description = "Root output directory to write files",
         mandatory = true )
   private File _outputRootDir;

   @Override
   public void initialize( final UimaContext context ) throws ResourceInitializationException {
      super.initialize( context );
   }

   @Override
   public void process( final JCas jcas ) throws AnalysisEngineProcessException {

      final String patient = Long.toString( SourceMetadataUtil.getPatientNum( jcas ) );
      final SourceData sourceData = SourceMetadataUtil.getSourceData( jcas );
      final String encounter = sourceData==null?"null":sourceData.getSourceEncounterId();
      final String providerId = sourceData==null?"null":SourceMetadataUtil.getProviderId( sourceData );
      // source date not used ???
//      final String sourceDate = sourceData.getSourceOriginalDate();
      JCas deidView = null;
      try{
        deidView = jcas.getView(DEID_VIEW);
      }catch(CASException e){
        throw new AnalysisEngineProcessException(e);
      }
      final Collection<String> conceptLines = createConceptLines( deidView, patient, encounter, providerId );

      final File outputFile = new File( _outputRootDir, encounter );
      saveAnnotations( outputFile, conceptLines );

   }


   static private Collection<String> createConceptLines( final JCas jcas,
                                                 final String patient, final String encounter, final String provider ) {
      final Collection<IdentifiedAnnotation> annotations = JCasUtil.select( jcas, IdentifiedAnnotation.class );
      if ( annotations.isEmpty() ) {
         return Collections.emptyList();
      }
      final ConceptLinesBuilder conceptLinesBuilder = new ConceptLinesBuilder();
      conceptLinesBuilder.patient( patient ).encounter( encounter ).provider( provider );
      final Collection<String> annotationDataLines = new ArrayList<>();
      for ( IdentifiedAnnotation annotation : annotations ) {
         conceptLinesBuilder
               .clearAttributes()
               .value( annotation.getCoveredText() )
               .attribute( MODIFIER_CD_BEGIN, annotation.getBegin() )
               .attribute( MODIFIER_CD_END, annotation.getEnd() )
               .attribute( MODIFIER_CD_POLARITY, annotation.getPolarity() < 0 ? NEGATED : AFFIRMED )
               .attribute( MODIFIER_CD_SUBJECT, annotation.getSubject() )
               .attribute( MODIFIER_CD_SECTION, annotation.getSegmentID() )
               .attribute( MODIFIER_CD_UNCERTAINTY, annotation.getUncertainty() )
               .attribute( MODIFIER_CD_BODY_LOCATION, null )
               .attribute( MODIFIER_CD_SEVERITY, null )
               .attribute( MODIFIER_CD_DOC_TIME_REL, getDocTimeRel( annotation ) )
               .attribute( MODIFIER_CD_SENT, getSentenceCoverage(jcas, annotation ) );
         // At this time the codes are all that change
         final Collection<String> codes = OntologyConceptUtil.getCodes( annotation );
         codes.addAll( OntologyConceptUtil.getCuis( annotation ) );
         for ( String code : codes ) {
            conceptLinesBuilder.code( code );
            annotationDataLines.addAll( conceptLinesBuilder.buildLines() );
         }
      }
      return annotationDataLines;
   }

   static private String getDocTimeRel( final IdentifiedAnnotation annotation ) {
      if ( annotation == null || !(annotation instanceof EventMention) ) {
         return "";
      }
      final Event event = ((EventMention) annotation).getEvent();
      if ( event != null
          && event.getProperties() != null
          && event.getProperties().getDocTimeRel() != null ) {
         return event.getProperties().getDocTimeRel();
      }
      return "";
   }

   static private String getSentenceCoverage( final JCas jcas, final IdentifiedAnnotation annotation ) {
      final Collection<Sentence> entitySentences = JCasUtil.selectCovering( jcas,
            Sentence.class, annotation.getBegin(), annotation.getEnd() );
      final StringBuilder sb = new StringBuilder();
      for ( Sentence sentence : entitySentences ) {
         sb.append( sentence.getCoveredText() ).append( "\n" );
      }
      return sb.toString().trim();
   }


   static private void saveAnnotations( final File outputFile, final Collection<String> conceptLines ) {
      try ( Writer writer = new BufferedWriter( new FileWriter( outputFile ) ) ) {
         for ( String line : conceptLines ) {
            writer.write( line );
         }
      } catch ( IOException ioE ) {
         LOGGER.error( ioE.getMessage() );
      }
   }



   static private final class ConceptLinesBuilder {
      private String __instance = "-1";
      private String __code;
      private String __modifier = MODIFIER_DEFAULT;
      private String __value;
      private String __provider;
      private String __encounter;
      private String __patient;
      private String __startDate = "";
      private Collection<ConceptAttribute> __attributes = new HashSet<>();
      private ConceptLinesBuilder instance( final String instance ) {
         __instance = instance;
         return this;
      }
      private ConceptLinesBuilder code( final String code ) {
         __code = code;
         return this;
      }
      private ConceptLinesBuilder modifier( final String modifier ) {
         __modifier = modifier;
         return this;
      }
      private ConceptLinesBuilder value( final String value ) {
         __value = value;
         return this;
      }
      private ConceptLinesBuilder provider( final String provider ) {
         __provider = provider;
         return this;
      }
      private ConceptLinesBuilder encounter( final String encounter ) {
         __encounter = encounter;
         return this;
      }
      private ConceptLinesBuilder patient( final String patient ) {
         __patient = patient;
         return this;
      }
      private ConceptLinesBuilder startDate( final Timestamp startDate ) {
         __startDate = startDate.toString();
         return this;
      }
      private ConceptLinesBuilder clearAttributes() {
         __attributes.clear();
         return this;
      }
      private ConceptLinesBuilder attribute( final String name, final String value ) {
         __attributes.add( new ConceptAttribute( name, value ) );
         return this;
      }
      private ConceptLinesBuilder attribute( final String name, final int value ) {
         return attribute( name, Integer.toString( value ) );
      }
      private Collection<String> buildLines() {
         final Collection<String> conceptLines = new ArrayList<>( __attributes.size()+1 );
         final LineBuilder lineBuilder = new LineBuilder();
         final String conceptLine = lineBuilder
               .append( __encounter )
               .append( __patient )
               .append( __code )
               .append( __provider )
               .append( __startDate )
               .append( __modifier )
               .append( __instance )
               .append( "" )  // Attribute has T
               .append( "" )  // Attribute has attribute value
               .appendFiller()
               .append( CTAKES_VERSION )
               .append( __value )
               .buildLine();
         conceptLines.add( conceptLine );
         for ( ConceptAttribute attribute : __attributes ) {
            lineBuilder.clear();
            final String attributeLine = lineBuilder
                  .append( __encounter )
                  .append( __patient )
                  .append( __code )
                  .append( __provider )
                  .append( __startDate )
                  .append( attribute.__name )
                  .append( __instance )
                  .append( "T" )
                  .append( attribute.__value )
                  .appendFiller()
                  .append( CTAKES_VERSION )
                  .append( __value )
                  .buildLine();
            conceptLines.add( attributeLine );
         }
         return conceptLines;
      }

      static private class LineBuilder {
         final private StringBuilder __sb = new StringBuilder();
         private void clear() {
            __sb.setLength( 0 );
         }
         private LineBuilder append( final String cell ) {
            __sb.append( cell ).append( "|" );
            return this;
         }
         private LineBuilder appendFiller() {
            __sb.append( "|||||||" );
            return this;
         }
         private String buildLine() {
            return __sb.append( "\n" ).toString();
         }
      }

      static private final class ConceptAttribute {
         private final String __name;
         private final String __value;
         private ConceptAttribute( final String name, final String value ) {
            __name = name;
            __value = value;
         }
      }

   }



   public static AnalysisEngineDescription createAnnotatorDescription( final String outputDirectory )
         throws ResourceInitializationException {
      return AnalysisEngineFactory.createEngineDescription(
            I2b2ReadyFileWriter.class,
            I2b2ReadyFileWriter.PARAM_OUTPUTDIR, outputDirectory );
   }


}

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.util.OntologyConceptUtil;
import org.apache.ctakes.core.util.SourceMetadataUtil;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.Logger;
import org.apache.uima.util.Level;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import com.mongodb.MongoCredential;
import com.mongodb.MongoClientOptions;
import org.bson.Document;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MongoDBWriter extends JCasAnnotator_ImplBase {

    public static final String PARAM_MONGO_HOST = "MongoHost";
    @ConfigurationParameter(name = PARAM_MONGO_HOST, mandatory=false)
    private String mongoHost="localhost";

    public static final String PARAM_MONGO_DB = "MongoDb";
    @ConfigurationParameter(name = PARAM_MONGO_DB, mandatory=false)
    private String mongoDbName = "mydb";

    public static final String PARAM_MONGO_COLLECTION = "MongoCollection";
    @ConfigurationParameter(name = PARAM_MONGO_COLLECTION, mandatory=false)
    private String collection = "cuis";

    public static final String CUI_FIELD = "cui";
    public static final String ENCOUNTER_FIELD = "encounter";
    public static final String PT_FIELD = "patient_num";
    public static final String PROVIDER_FIELD = "provider";
    public static final String SOURCESYSTEM_FIELD = "sourcesystem_cd";
    public static final String SENT_FIELD = "sentence";
    public static final String DOCTIMEREL_FIELD = "doctimerel";
    public static final String UNCERTAINTY_FIELD = "uncertainty";
    public static final String POLARITY_FIELD = "polarity";
    public static final String END_FIELD = "end";
    public static final String START_FIELD = "start";

    static private final String CTAKES_VERSION = "CTAKES_4.0.0_PL_171129";


    private MongoClient mongoClient = null;
    private MongoDatabase mongoDb = null;
    private MongoCollection<Document> coll = null;

    private static Logger logger = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
    
        if(logger == null) {
            logger = context.getLogger();
        }

        // Initialize the MongoDB Connection
        try{
            this.mongoClient = MongoClients.create("mongodb://" + this.mongoHost);
            this.mongoDb = mongoClient.getDatabase(this.mongoDbName);
            this.coll = mongoDb.getCollection(this.collection);
        }catch(Exception e){
            throw new ResourceInitializationException(e);
        }
    }

    /**
        * {@inheritDoc}
        */
    @Override
    public void process( final JCas jcas ) throws AnalysisEngineProcessException {
        final SourceData sourceData = SourceMetadataUtil.getSourceData( jcas );
        if ( sourceData == null ) {
            logger.log(Level.SEVERE,  "Missing source metadata for document!" );
            return;
        }
        final long patientNum = SourceMetadataUtil.getPatientNum( jcas );
        final int encounterNum;
        try {
            encounterNum = SourceMetadataUtil.getEncounterNum( sourceData );
        } catch (ResourceProcessException e) {
            throw new AnalysisEngineProcessException(e);
        }
        final String providerId = SourceMetadataUtil.getProviderId( sourceData );

        JCas deidView = null;
        try {
            deidView = jcas.getView("DeidView");
        } catch(Exception e) {
            logger.log(Level.SEVERE, "No deid view found... falling back to normal jCAS");
            deidView = jcas;
        }

        final Collection<IdentifiedAnnotation> annotations = JCasUtil.select(deidView, IdentifiedAnnotation.class);
        if (annotations.isEmpty()) {
            logger.log(Level.WARNING, "No annotations found. Patient, encounter, provider =  " + patientNum + ", " + encounterNum + ", " + providerId);
            return;
        }

        List<Document> docs = new ArrayList<>();
        // iterate over jcas identified annotations creating new documents
        for(IdentifiedAnnotation ent : annotations){
			final Set<String> codes = new HashSet<String>(); 
            // A Cui may belong to multiple Tuis, making multiple UmlsConcept objects (one per tui).
            // I2b2 does NOT want multiple rows of a single Cui just because it has multiple tuis.
            // FIXME: Can make the TUI field be a list?
			codes.addAll(OntologyConceptUtil.getCuis(ent));
            for(String code : codes){
                Document doc = new Document()
                                    .append(CUI_FIELD, code)
                                    .append(PT_FIELD, patientNum)
                                    .append(ENCOUNTER_FIELD, encounterNum)
                                    .append(SOURCESYSTEM_FIELD, CTAKES_VERSION)
                                    .append(START_FIELD, ent.getBegin())
                                    .append(END_FIELD, ent.getEnd())
                                    .append(POLARITY_FIELD, ent.getPolarity())
                                    .append(UNCERTAINTY_FIELD, ent.getUncertainty())
                                    .append(DOCTIMEREL_FIELD, getDocTimeRel(ent))
                                    .append(SENT_FIELD, getCoveringSent(deidView, ent));
                docs.add(doc);
            }
        }
        this.coll.insertMany(docs);
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        // Create an index over the 'cui' field for faster searching
        this.coll.createIndex(Indexes.text(CUI_FIELD));
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

    // FIXME, selectCovering is known to be slow, this could potentially slow down writes
    static private String getCoveringSent( final JCas jcas, final IdentifiedAnnotation annotation ) {
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

}

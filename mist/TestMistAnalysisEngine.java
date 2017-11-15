import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.metadata.TypeSystemDescription;

/**
 * Created by tmill on 11/15/17.
 */
public class TestMistAnalysisEngine {
    public static void main(String[] args) throws Exception {

//        TypeSystemDescription tsd = TypeSystemDescriptionFactory.createTypeSystemDescriptionFromPath("../desc/TypeSystem.xml");
        JCas jcas = JCasFactory.createJCas();
        jcas.setDocumentText("Patient is a 30-year-old man named Leroy Butler from Green Bay, WI.");
        AnalysisEngineDescription aed = AnalysisEngineFactory.createEngineDescription(MistAnalysisEngine.class,
                MistAnalysisEngine.PARAM_MODEL_PATH,
                "SHARP/model/model");
        SimplePipeline.runPipeline(jcas, aed);
        for(Annotation annot : JCasUtil.select(jcas, Annotation.class)){
            System.out.println("Found annotation: " + annot.getCoveredText());
        }
        JCas deidView = jcas.getView(MistAnalysisEngine.DEID_VIEW_NAME);
        System.out.println("Deidentified version:");
        System.out.println(deidView.getDocumentText());
    }

}


import java.io.FileWriter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;

public class GenerateDescriptors{
    public static void main(String[] args) throws Exception {
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(AnalysisEngineFactory.createEngineDescriptionFromPath("desc/remoteMist.xml"));

        AnalysisEngineDescription aed = builder.createAggregateDescription();
        aed.toXML(new FileWriter(args[0]));
    }
}

import org.apache.uima.UimaContext;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.mitre.itc.jcarafe.crf.Decoder;
import org.mitre.itc.jcarafe.crf.TextDecoder;
import org.mitre.itc.jcarafe.tokenizer.Element;
import org.mitre.itc.jcarafe.tokenizer.FastTokenizer;
import org.mitre.itc.jcarafe.util.Annotation;
import org.mitre.itc.jcarafe.util.Options;
import scala.collection.immutable.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MistAnalysisEngine extends JCasAnnotator_ImplBase{
  public static final String DEID_VIEW_NAME = "DeidView";
  private TextDecoder decoder = null;

  public static final String PARAM_MODEL_PATH = "ModelPath";
  @ConfigurationParameter(
          name = PARAM_MODEL_PATH,
          description = "Filesystem location of MIST model directory",
          mandatory = false
  )
  private String modelPath = "/MIST_1_3_1/src/tasks/SHARP/model/model";

  private Pattern xmlPatt = Pattern.compile("<(.*)>(.*?)<\\/\\1>", Pattern.DOTALL);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);

    Options options = new Options(new String[]{"--prior-adjust", "-1.0", "--region", "zone:region_type=body"});
    decoder = new TextDecoder(options, this.modelPath);
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    try{
      JCas deidView = CasUtil.getView(jCas.getCas(), DEID_VIEW_NAME, true).getJCas();

      String text = jCas.getDocumentText();

      String decoderOut = decoder.decodeString(text);

      while(true) {
        Matcher m = xmlPatt.matcher(decoderOut);
        if (!m.find()) {
          break;
        }
        String matchType = m.group(1);
        int matchStart = m.start();
        int matchEnd = m.end();
        decoderOut = decoderOut.substring(0, matchStart) + "[" + matchType + "]" + decoderOut.substring(matchEnd);
      }

      deidView.setDocumentText(decoderOut);
    }catch(Exception e){
      System.err.println("Error trying to run mist!");
      throw new AnalysisEngineProcessException(e);
    }
  }

  public static String[] getCommand(String outFilename){
      return new String[]{"/MIST_1_3_1/src/MAT/bin/MATEngine", "--task", "SHARP Deidentification", "--workflow", "Demo", "--steps", "zone,tag,nominate,transform", "--input_file", "-", "--input_file_type", "raw", "--output_file", outFilename, "--output_file_type", "raw", "--tagger_local", "--tagger_model", "/MIST_1_3_1/src/tasks/SHARP/model/model", "--subprocess_debug", "0",
      "--replacer", "clear -> [ ]"};
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    decoder.cleanUp();
  }
}

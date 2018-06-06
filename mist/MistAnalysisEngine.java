import org.apache.ctakes.core.util.DocumentIDAnnotationUtil;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.internal.util.XMLUtils;

import org.mitre.itc.jcarafe.crf.TextDecoder;
import org.mitre.itc.jcarafe.util.Options;

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

  private static Pattern xmlPatt = Pattern.compile("<(.*)>(.*?)<\\/\\1>", Pattern.DOTALL);

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

      copyDocIdToView(jCas, deidView);

      String text = forceXmlSerializable(jCas.getDocumentText().replace("<","&lt;").replace(">","&gt;"));

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

  private void copyDocIdToView(JCas jCas, JCas deidView) {
    DocumentID newId = new DocumentID(deidView);
    newId.setDocumentID(DocumentIDAnnotationUtil.getDocumentID(jCas));
    newId.addToIndexes();
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    decoder.cleanUp();
  }

  /// copied from some cTAKES collection readers.
  private String forceXmlSerializable( String s ) {
    if (s==null) return "";
    if (s.length()==0) return s;

    int badChar = XMLUtils.checkForNonXmlCharacters(s);

    // Performance-wise this is not the best but since this is not that common an occurrence,
    // it is good enough
    while (badChar > -1) {
      char c = s.charAt(badChar);
      s = s.replace(c, ' ');
      badChar = XMLUtils.checkForNonXmlCharacters(s);
    }

    return s;
  }
}

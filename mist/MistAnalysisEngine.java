import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileReader;
import java.io.File;

public class MistAnalysisEngine extends JCasAnnotator_ImplBase{
  public static final String DEID_VIEW_NAME = "DeidView";

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    try{
      JCas deidView = CasUtil.getView(jCas.getCas(), DEID_VIEW_NAME, true).getJCas();

      File tempOut = File.createTempFile("deidfile", "mist");

      Process p = new ProcessBuilder(getCommand(tempOut.getAbsolutePath())).start();

      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
      writer.write(jCas.getDocumentText());
      writer.flush();
      writer.close();

      p.waitFor();

      BufferedReader reader = new BufferedReader(new FileReader(tempOut));
      String line;
      StringBuilder buff = new StringBuilder();
      while((line = reader.readLine()) != null){
        //System.err.println("Seeing input line: " + line);
        if(line.contains(": -")) continue;
        buff.append(line);
        buff.append('\n');
      }
      deidView.setDocumentText(buff.toString());
    }catch(Exception e){
      System.err.println("Error trying to run mist!");
      throw new AnalysisEngineProcessException(e);
    }
  }

  public static String[] getCommand(String outFilename){
      return new String[]{"/MIST_1_3_1/src/MAT/bin/MATEngine", "--task", "SHARP Deidentification", "--workflow", "Demo", "--steps", "zone,tag,nominate,transform", "--input_file", "-", "--input_file_type", "raw", "--output_file", outFilename, "--output_file_type", "raw", "--tagger_local", "--tagger_model", "/MIST_1_3_1/src/tasks/SHARP/model/model", "--subprocess_debug", "0",
      "--replacer", "clear -> [ ]"};
  }
}

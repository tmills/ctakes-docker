import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.jcas.JCas;

import java.io.FileWriter;
import java.util.List;

/**
 * Created by tmill on 2/21/18.
 * This class is a UIMA Analysis Engine whose purpose is to detect lines in a clinical narrative that are not
 * "linguistic", and therefore should not be processed by downstream components. We have some simple rules for
 * identifying non-linguistic lines. Then we make segments for every other line.
 * The actual classifier was trained using 200 notes manually annotated for ignorable sections. We identified a
 * few features and used some rule-generating machine learning systems to make this as simple as possible.
 */
public class IgnorableSectionAnnotator extends JCasAnnotator_ImplBase {
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        String text = jCas.getDocumentText();
        String[] lines = text.split("\n");
        boolean prevIgnorable = false;

        int currentSegmentStart = 0;
        int currentSegmentEnd = 0;

        for(String line : lines){
            int alpha = 0, digit = 0, ws = 0, other = 0;
            for(char c : line.toCharArray()){
                if(Character.isAlphabetic(c)) alpha++;
                if(Character.isDigit(c)) digit++;
                if(Character.isWhitespace(c)) ws++;
            }
            other = line.length() - alpha - digit - ws;
            float letterRatio = (float) alpha / line.length();

            // get classification for this line:
            boolean ignorable = getJriprClassification(prevIgnorable, letterRatio, line.length());

            if(ignorable){
                if(!prevIgnorable){
                    // just starting ignorable section -- create ignorable segment:
                    Segment seg = new Segment(jCas, currentSegmentStart, currentSegmentEnd);
                    seg.addToIndexes();
                }
                // in the middle of an ignorable section. update segmentStart
                currentSegmentStart += line.length() + 1;
                currentSegmentEnd = currentSegmentStart;
            }else if(!ignorable){
                // segment start will be correct -- but we can move the end pointer to the end of this line.
                // updated so we need to update only segment end
                currentSegmentEnd += line.length() + 1;
            }
        }
    }

    /*
        This particular classifier learns a series of propositional rules meant to explain the data.
        Somewhat simpler than a decision tree. The comments below are my rough interpretation of what
        each rule is doing if I can figure out the logic.
     */
    private static boolean getJriprClassification(boolean prev, float letterRatio, int sentLength){
        // if the previous line was ignorable, the next lien has content, but low letter ratio, this line is ignorable too.
        if(prev && sentLength > 2 && letterRatio <= 0.72) return true;
        // if prev line was ignorable, but letter ratio not high enough, then also ignorable.
        else if(prev && letterRatio >= 0.721739 && letterRatio <= 0.809524) return true;
        // if prev line was ignorable, line length is long enough, and high letter ratio, then ignorable (this one is confusin)
        else if(prev && letterRatio >= 0.8125 && sentLength >= 15) return true;
        // if prev line was ignorable and high label ratio, then true
        else if(prev && letterRatio >= 0.875) return true;
        // if sentence is long but with low letter ratio, then its ignorable (this is the only way to start a new
        // ignorable section:
        else if(sentLength >= 69 && letterRatio <= 0.572581 && letterRatio >= 0.20339) return  true;
        // if prev line was ignorable and sentence length is really short, we are still ignorable
        // this might handle segments with many lines and that are mostly ignorable, with one short line of text
        // in the middle that is still probably not processable but may have something looking like an appropriate
        // letter ratio.
        else if(prev && sentLength <= 11 && sentLength >= 1) return true;
        else return false;
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 1){
            System.err.println("Required argument: <output filename>");
            System.exit(-1);
        }
        AnalysisEngineDescription aed = AnalysisEngineFactory.createEngineDescription(IgnorableSectionAnnotator.class);
        aed.toXML(new FileWriter(args[0]));
    }
}

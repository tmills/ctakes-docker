import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.JCasIterable;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.xerces.parsers.DOMParser;
import org.cleartk.util.ViewUriUtil;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import scala.math.Ordering;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tmill on 2/7/18.
 */
public class PrecisionLinkAnaforaReader  extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        File txtFile = new File(ViewUriUtil.getURI(jCas));
        File xmlFile = null;
        for(File file : txtFile.getParentFile().listFiles()){
            if(file.getName().endsWith(".xml")){
                xmlFile = file;
                break;
            }
        }

        System.out.println("Processing file: " + xmlFile.getPath());

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        Document doc = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(xmlFile);
        } catch (ParserConfigurationException|SAXException|IOException e) {
            e.printStackTrace();
            throw new AnalysisEngineProcessException(e);
        }
        doc.getDocumentElement().normalize();
        NodeList entities = doc.getElementsByTagName("entity");
        for(int i = 0; i < entities.getLength(); i++){
            Node node = entities.item(i);
            boolean ignorable = false;
            boolean ack = false;
            int begin=-1, end=-1;
            NodeList entityAttributes = node.getChildNodes();
            for(int j = 0; j < entityAttributes.getLength(); j++){
                Node child = entityAttributes.item(j);
                if(child.getNodeName().equals("span")){
                    String[] span = child.getTextContent().split(",");
                    begin = Integer.parseInt(span[0]);
                    end = Integer.parseInt(span[1]);
                }else if(child.getNodeName().equals("type") && child.getTextContent().equals("Ignorable")){
                    ignorable = true;
                }else if(child.getNodeName().equals("Type") && child.getTextContent().equals("Ack")){
                    ack = true;
                }
            }
            if(ignorable){
                System.out.println(String.format("Found ignorable string with span %d - %d", begin, end));
                Segment seg = new Segment(jCas, begin, end);
                seg.setId("Ignorable");
                seg.addToIndexes();
            }else if(ack){
                Segment seg = new Segment(jCas, begin, end);
                seg.setId("Ack");
                seg.addToIndexes();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 1){
            System.err.println("One required argument: <anafora directory>");
            System.exit(-1);
        }
        Path startingDir = Paths.get(new File(args[0]).toURI());
        AddFilesWithAnnotations fileAdder = new AddFilesWithAnnotations();
        Files.walkFileTree(startingDir, fileAdder);

        System.out.println(String.format("Directory contains %d completed xml files", fileAdder.textFiles.size()));

        CollectionReaderDescription reader = UriCollectionReader.getDescriptionFromFiles(fileAdder.textFiles);
        AggregateBuilder builder = new AggregateBuilder();
        builder.add(UriToDocumentTextAnnotator.getDescription());
        builder.add(AnalysisEngineFactory.createEngineDescription(PrecisionLinkAnaforaReader.class));

        for(JCas jCas : new JCasIterable(reader, builder.createAggregateDescription())){
            String filename = ViewUriUtil.getURI(jCas).toASCIIString();
//            System.out.println("Starting to post-process URI: " + ViewUriUtil.getURI(jcas));
            List<Segment> segments = new ArrayList<>(JCasUtil.select(jCas, Segment.class));
            if(segments.size() == 0){
                System.out.println("No segments found in file: " + filename +". If there are no ignorable segments then an Ack annotatino should be created.");
                continue;
            }
            List<String> lines = Files.readAllLines(Paths.get(ViewUriUtil.getURI(jCas)));
            int lineStart = 0;
            int lineEnd;
            boolean lastIgnorable=false;
            for(String line : lines){
                lineEnd = lineStart + line.length();
                int alpha = 0, digit = 0, ws = 0, other = 0;
                for(char c : line.toCharArray()){
                    if(Character.isAlphabetic(c)) alpha++;
                    if(Character.isDigit(c)) digit++;
                    if(Character.isWhitespace(c)) ws++;
                }
                other = line.length() - alpha - digit - ws;
                float letterRatio = (float) alpha / line.length();
                boolean ignorable=false;
                for(Segment seg: segments){
                    if(seg.getId().equals("Ack")) continue;
                    if(lineStart < seg.getEnd() && lineEnd >= seg.getBegin()){
                        ignorable = true;
                        break;
                    }
                }
                System.out.println("Line length=" + line.length() + ", letterRatio=" + letterRatio + ", previous ignorable=" + lastIgnorable + ", and is " + (ignorable ? "ignorable" : "not ignorable") + " with text: " + line);
                lineStart = lineEnd + 1;
                lastIgnorable = ignorable;
            }
        }
    }

    public static class AddFilesWithAnnotations extends SimpleFileVisitor<Path> {
        List<File> textFiles = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
            File file = path.toFile();
            if(file.getName().endsWith(".xml")){
                File parentDir = file.getParentFile();
                String txtFileName = parentDir.getName();
                textFiles.add(new File(parentDir, txtFileName));
            }

            return super.visitFile(path, basicFileAttributes);
        }
    }
}

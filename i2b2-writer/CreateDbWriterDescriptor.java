import org.apache.ctakes.core.resource.JdbcConnectionResourceImpl;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.xml.sax.SAXException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Created by tmill on 3/29/18.
 */
public class CreateDbWriterDescriptor {
    public static void main(String[] args) throws ResourceInitializationException, IOException, SAXException {
        Map<String,String> env = System.getenv();
        ExternalResourceDescription erd = ExternalResourceFactory.createExternalResourceDescription(
                JdbcConnectionResourceImpl.class,
                "null",   // method is ambiguous because all strings are objects so this is here as the unneede (i think) aURL argument
                JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS,
                "oracle.jdbc.OracleDriver",
                JdbcConnectionResourceImpl.PARAM_URL,
                System.getProperty("oracle_url"),
                JdbcConnectionResourceImpl.PARAM_USERNAME,
                System.getProperty("oracle_user"),
                JdbcConnectionResourceImpl.PARAM_PASSWORD,
                System.getProperty("oracle_pw"),
                JdbcConnectionResourceImpl.PARAM_KEEP_ALIVE,
                "false",
                AbstractJdbcWriter.PARAM_DB_CONN_RESRC,
                "DbConnectionWrite");

        AnalysisEngineDescription aed = AnalysisEngineFactory.createEngineDescription(I2b2JdbcWriter.class,
                I2b2JdbcWriter.PARAM_VECTOR_TABLE,
                System.getProperty("oracle_table"),
                AbstractJdbcWriter.PARAM_DB_CONN_RESRC,
                erd
                );

        aed.toXML(new FileWriter(args[0]));
    }
}

import org.apache.ctakes.core.resource.JdbcConnectionResource;
import org.apache.ctakes.core.resource.JdbcConnectionResourceImpl;
import org.apache.ctakes.i2b2.cr.I2b2CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.xml.sax.SAXException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Created by tmill on 3/29/18.
 */
public class CreateDbReaderDescriptor {
    public static void main(String[] args) throws ResourceInitializationException, IOException, SAXException, InvalidXMLException {
        String sqlStatement = String.format("select encounter_num,patient_num,observation_blob,start_date,provider_id,modifier_cd,concept_cd,instance_num from %s where sourcesystem_cd='NOTES' and observation_blob is not null and length(observation_blob) > 0", System.getProperty("oracle_table"));

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
                I2b2CollectionReader.PARAM_DB_CONN_RESRC,
                "DbConnectionRead");

        CollectionReaderDescription aed = CollectionReaderFactory.createReaderDescription(MemReleaseI2b2CollectionReader.class,
                I2b2CollectionReader.PARAM_SQL,
                sqlStatement,
                I2b2CollectionReader.PARAM_DOCTEXT_COL,
                "OBSERVATION_BLOB",
                I2b2CollectionReader.PARAM_DOCID_COLS,
                new String[]{"encounter_num", "patient_num", "modifier_cd"},
                I2b2CollectionReader.PARAM_DOCID_DELIMITER,
                "_",
                I2b2CollectionReader.PARAM_VALUE_PASSPHARASE,
                "",
                I2b2CollectionReader.PARAM_PATIENT_NUM_COL,
                "patient_num",
                I2b2CollectionReader.PARAM_ENCOUNTER_NUM_COL,
                "encounter_num",
                I2b2CollectionReader.PARAM_PROVIDER_ID_COL,
                "provider_id",
                I2b2CollectionReader.PARAM_START_DATE_COL,
                "start_date",
                I2b2CollectionReader.PARAM_CONCEPT_CD_COL,
                "concept_cd",
                I2b2CollectionReader.PARAM_INSTANCE_NUM_COL,
                "instance_num",
                I2b2CollectionReader.PARAM_DB_CONN_RESRC,
                "DbConnectionRead"
                );

        ExternalResourceFactory.createDependency(aed, "DbConnectionRead", JdbcConnectionResource.class);
        ExternalResourceFactory.bindExternalResource(aed, "DbConnectionRead", erd);
        aed.toXML(new FileWriter(args[0]));
    }
}


/////// package org.apache.ctakes.i2b2.cr;

import org.apache.uima.internal.util.XMLUtils;
import org.apache.ctakes.core.cr.JdbcCollectionReader;
import org.apache.ctakes.core.resource.JdbcConnectionResource;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.ctakes.typesystem.type.structured.Metadata;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.collection.CasInitializer;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
/////////// import org.i2b2.encryption.hive.I2b2Cryptography;

import java.io.*;
import java.sql.*;

import static org.apache.uima.util.Level.*;

/**
* Author: SPF
* Affiliation: CHIP-NLP
* Date: 2/12/13
*/
final public class MemReleaseI2b2CollectionReader extends JdbcCollectionReader {

   // LOG4J logger based on class name
   private org.apache.uima.util.Logger logger = null;

   /**
    * SQL statement to retrieve the document.
    */
   public static final String PARAM_SQL = "SqlStatement";

   /**
    * Name of column from resultset that contains the document text. Supported
    * column types are CHAR, VARCHAR, and CLOB.
    */
   public static final String PARAM_DOCTEXT_COL = "DocTextColName";


   public static final String PARAM_ENCOUNTER_NUM_COL = "db_col_encounter_num";
   public static final String PARAM_PATIENT_NUM_COL = "db_col_patient_num";
   public static final String PARAM_START_DATE_COL = "db_col_start_date";
   public static final String PARAM_PROVIDER_ID_COL = "db_col_provider_id";
   public static final String PARAM_CONCEPT_CD_COL = "db_col_concept_cd";
   public static final String PARAM_INSTANCE_NUM_COL = "db_col_instance_num";

   /**
    * Name of external resource for database connection.
    */
   public static final String PARAM_DB_CONN_RESRC = "DbConnResrcName";

   /**
    * Optional parameter. Specifies column names that will be used to form a
    * document ID.
    */
   public static final String PARAM_DOCID_COLS = "DocIdColNames";

   /**
    * Optional parameter. Specifies delimiter used when document ID is built.
    */
   public static final String PARAM_DOCID_DELIMITER = "DocIdDelimiter";

   public static final String PARAM_VALUE_PASSPHARASE = "i2b2Passphrase";

   private PreparedStatement _queryPrepStatement;
   private ResultSet _resultSet;

   private String _passphrase;

   private String _docTextColName;
   private int _docColType;
   private String _docColTypeName;

   // optional, will remain null if not set
   private String[] _docIdColNames = null;

   // default is underscore
   private String _docIdDelimiter = "_";

   private int _currRowCount = 0;
   private int _encounter_num;

   private long _startMillis;

   private String _db_col_encounter_num;
   private String _db_col_patient_num;
   private String _db_col_start_date;
   private String _db_col_provider_id;
   private String _db_col_concept_cd;
   private String _db_col_instance_num;

   public void initialize() throws ResourceInitializationException {
      logger = getUimaContext().getLogger();
      logger.log(SEVERE, "Initializing MemReleaseI2b2CollectionReader");
      String sqlStatement;
      String resourceName;
      try {
         _passphrase = (String) getConfigParameterValue( PARAM_VALUE_PASSPHARASE );
         _db_col_encounter_num = (String) getConfigParameterValue( PARAM_ENCOUNTER_NUM_COL );
         _db_col_patient_num = (String) getConfigParameterValue( PARAM_PATIENT_NUM_COL );
         _db_col_start_date = (String) getConfigParameterValue( PARAM_START_DATE_COL );
         _db_col_provider_id = (String) getConfigParameterValue( PARAM_PROVIDER_ID_COL );
         _db_col_concept_cd = (String) getConfigParameterValue( PARAM_CONCEPT_CD_COL );
         _db_col_instance_num = (String) getConfigParameterValue( PARAM_INSTANCE_NUM_COL );
         _docTextColName = (String) getConfigParameterValue( PARAM_DOCTEXT_COL );
         _docIdColNames = (String[]) getConfigParameterValue( PARAM_DOCID_COLS );
         if ( getConfigParameterValue( PARAM_DOCID_DELIMITER ) != null ) {
            _docIdDelimiter = (String) getConfigParameterValue( PARAM_DOCID_DELIMITER );
         }
         sqlStatement = (String) getConfigParameterValue( PARAM_SQL );
         resourceName = (String) getConfigParameterValue( PARAM_DB_CONN_RESRC );
      } catch ( ClassCastException ccE ) {
         // thrown because non-specific method getConfigParameterValue(..) returns an Object and we are casting
         logger.log(SEVERE, "Could not fetch a configuration parameter value of the proper type" );
         throw new ResourceInitializationException( ccE );
      }
      loadResources( resourceName, sqlStatement );
      _startMillis = System.currentTimeMillis();
   }

   private void loadResources( final String resourceName, final String sqlStatement ) throws ResourceInitializationException {
      try {
         final JdbcConnectionResource jdbcConnectionResource
               = (JdbcConnectionResource) getUimaContext().getResourceObject( resourceName );
         final Connection connection = jdbcConnectionResource.getConnection();
         _queryPrepStatement = connection.prepareStatement( sqlStatement );
         // TODO Upon migration to Java 7, consider merging into "catch ( ResourceAccessException | SQLException e )"
      } catch ( ResourceAccessException | ClassCastException raEccE ) {
         // thrown by UimaContext.getResourceObject(..)
          // thrown because non-specific method UimaContext.getResourceObject(..) returns an Object and we are casting
         logger.log(SEVERE, "Could not obtain a uima resource" );
         throw new ResourceInitializationException( raEccE );
      } catch ( SQLException sqlE ) {
         // thrown by Connection.prepareStatement(..) and getTotalRowCount(..)
          logger.log(SEVERE, "Could not interact with Database");
         throw new ResourceInitializationException( sqlE );
      }
   }

    /**
     *
     * @return id number for the encounter
     */
   protected int getEncounterNum() {
      return _encounter_num;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void getNext( final CAS cas ) throws IOException, CollectionException {
     logger.log(SEVERE, "getNext(" );
      _currRowCount++;
      if ( cas == null ) {
         throw new CollectionException( new NullPointerException( "Null CAS " + _currRowCount
                                                               + " for encounter_num " + _encounter_num // fix typo  
                                                               + " in " + getClass().getName() + ".getNext( CAS )") );
      }
      // pull doc text from resultset - may throw IOException
      final String clobDocument = getClobDocument();
      // get the plain text version of the clob document
      final String document = getTextDocument( clobDocument );
      // if there's a CAS Initializer, call it
       // TODO uima CasInitializer has been deprecated.  Find and use its replacement
      final CasInitializer casInitializer = getCasInitializer();
      if ( casInitializer != null ) {
         // Use the CasInitializer to set the document in the cas
         final Reader reader = new StringReader( document );
         // CasInitializer.initializeCas(..) can throw CollectionException or IOException, both declared
         // TODO uima CollectionReader_ImplBase.getCasInitializer has been deprecated.  Find and use its replacement
         getCasInitializer().initializeCas( reader, cas );
      }
      JCas jCas;
      try {
         jCas = cas.getJCas();
      } catch ( CASException casE ) {
         // thrown by Cas.getJCas() , rethrow as declared CollectionException
         throw new CollectionException( casE );
      }
      if ( casInitializer == null ) {
         // No CAS Initializer, so set document text ourselves. put document in CAS (assume CAS)
         try {
            jCas.setDocumentText( document );
         } catch ( CASRuntimeException casRTE ) {
            // thrown by JCas.setDocumentText(..) , rethrow as declared CollectionException
            throw new CollectionException( casRTE );
         }
      }
      final DocumentID docIdAnnot = new DocumentID( jCas );
      final String documentID = createDocumentID();
      docIdAnnot.setDocumentID( documentID );
      docIdAnnot.addToIndexes();
      logger.log(SEVERE, "Reading document number " + _currRowCount + " with ID " + docIdAnnot.getDocumentID() );
      try {
         setMetadata( jCas );
      } catch ( SQLException sqlE ) {
         // thrown by setMetaData(..) inner calls to ResultSet.get*(..) , rethrow as declared IOException
         throw new IOException( sqlE );
      }
   }

   private String getClobDocument() throws IOException {
      // pull doc text from resultset
      String document;
      try {
         if ( _docColType == Types.CHAR || _docColType == Types.VARCHAR ) {
            document = _resultSet.getString( _docTextColName );
         } else if ( _docColType == Types.CLOB ) {
            document = convertToString( _resultSet.getClob( _docTextColName ) );
         } else {
            if ( !_docColTypeName.equals( "text" ) ) {
               logger.log(WARNING, "Inferring document text column as string type: " + _docColTypeName );
            }
            document = _resultSet.getString( _docTextColName );
         }
      } catch ( SQLException sqlE ) {
         // thrown by ResultSet.getString(..) and ResultSet.getClob(..) and convertToString(..)
         // rethrow as declared IOException
         throw new IOException( sqlE );
         // IOException thrown by convertToString(..) , ignoring as it will be passed through as declared
      }
      return document;
   }

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

   private String getTextDocument( final String clobDocument ) throws IOException {
      if ( _passphrase == null || _passphrase.trim().isEmpty() ) {
         // Assume that the clob document is not encrypted
         return forceXmlSerializable(clobDocument);
      }
      //Decrypt the i2b2 encrypted doc
      try {
	 throw new IOException("File encrypted...");
         ///////return decrypt( _passphrase, clobDocument );
      } catch ( Exception e ) {
         // raw Exception thrown by decrypt(..) , rethrow as declared IOException
         throw new IOException( e );
      }
   }

   /**
    * Builds a document ID from one or more pieces of query data.
    * If the query data is not specified OR if an SQLException is caught, the current row # is used.
    * This method should not throw an exception that stops the entire run when a row index can be used as an identifier
    * @return document ID
    */
   private String createDocumentID( ) {
      if ( _docIdColNames == null ) {
         return String.valueOf( _currRowCount );
      }
      final StringBuilder sb = new StringBuilder();
      // use flag to determine the first iteration in the loop, used for delimiter
      boolean firstColumn = true;
      try {
         for ( String columnName : _docIdColNames ) {
            if ( !firstColumn ) {
               sb.append( _docIdDelimiter );
            } else {
               firstColumn = false;
            }
            final String columnValue = _resultSet.getObject( columnName ).toString();
            sb.append( columnValue );
         }
      } catch ( SQLException sqlE ) {
         // thrown by ResultSet.getObject(..) and should be handled in this method createDocumentID(..)
         // do not throw an exception here if there is default behavior, which is to use row number
         return String.valueOf( _currRowCount );
      }
      return sb.toString();
   }

   /**
    * Loads the clob data into a String object.
    *
    * @param clob -
    * @return clob as single string with \n as line separator
    * @throws SQLException
    * @throws IOException
    */
   static private String convertToString( final Clob clob ) throws SQLException, IOException {
      final StringBuilder sb = new StringBuilder();
      final BufferedReader br = new BufferedReader( clob.getCharacterStream() );
      String line = br.readLine();
      while ( line != null ) {
         sb.append( line );
         sb.append( '\n' );
         line = br.readLine();
      }
      br.close();
      return sb.toString();
   }

   private void fillResultSet() throws SQLException {
      logger.log(INFO, "SQL:" + _queryPrepStatement.toString() );
      _resultSet = _queryPrepStatement.executeQuery();
   }

   private void setupDocColumnType() throws SQLException {
      final ResultSetMetaData rsMetaData = _resultSet.getMetaData();
      final int colIdx = _resultSet.findColumn( _docTextColName );
      _docColType = rsMetaData.getColumnType( colIdx );
      _docColTypeName = rsMetaData.getColumnTypeName( colIdx );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean hasNext() throws IOException, CollectionException {
      if ( _resultSet == null ) {
         try {
            fillResultSet();
            setupDocColumnType();
         } catch ( SQLException sqlE ) {
            // thrown by createResultSet() and setupDocColumnType(), rethrow as declared CollectionException
            throw new CollectionException( sqlE );
         }
      }
      boolean hasAnotherRow;
      try {
         hasAnotherRow = _resultSet.next();
         if ( hasAnotherRow ) {
            _encounter_num = _resultSet.getInt( _db_col_encounter_num );
         } else {
            // it's important to close ResultSets as they can accumulate
            // in the JVM heap. Too many open result sets can inadvertently
            // cause the DB conn to be closed by the server.
            _resultSet.close();
         }
      } catch ( SQLException sqlE ) {
         // thrown by ResultSet.next() and ResultSet.close()
         throw new CollectionException( sqlE );
      }
      return hasAnotherRow;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Progress[] getProgress() {
      final Progress p = new ProgressImpl( _currRowCount, Integer.MAX_VALUE, Progress.ENTITIES );
      return new Progress[]{p};
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws IOException {
      final long totalMillis = System.currentTimeMillis() - _startMillis;
      final long totalSeconds = totalMillis / 1000l;
      final long hourSeconds = 60*60;
      final long daySeconds = 24*hourSeconds;
      final long days = totalSeconds / daySeconds;
      final long hours = (totalSeconds - days*daySeconds) / hourSeconds;
      final long minutes = (totalSeconds - days*daySeconds - hours*hourSeconds) / 60;
      final long seconds = totalSeconds % 60;
      logger.log(INFO, getClass().getName() + " read " + _currRowCount + " documents in "
                         + days + " days, " + hours + " hours, " + minutes + " minutes and " + seconds + " seconds" );
      try {
         if ( !_resultSet.isClosed() ) {
            // Some jdbc drivers may not close the ResultSet when the PreparedStatement is closed
            _resultSet.close();
         }
         if ( !_queryPrepStatement.isClosed() ) {
            _queryPrepStatement.close();
         }
      } catch ( SQLException sqlE ) {
         // thrown by ResultSet.close() and Statement.close()
         // rethrow as IOException to fit the declared exception type
         throw new IOException( sqlE );
      }
   }

/**********************************
   public static String decrypt( final String key, final String note ) throws Exception {
      String str = "";
      str = I2b2Cryptography.decrypt( key, note );
      return str;
   }
**********************************/

   private JCas setMetadata( final JCas jCas ) throws SQLException {
      final Metadata metadata = new Metadata( jCas );
      final SourceData sourcedata = new SourceData( jCas );
      metadata.setPatientID( _resultSet.getLong( _db_col_patient_num ) );
      sourcedata.setAuthorSpecialty( _resultSet.getString( _db_col_provider_id ) );
      sourcedata.setNoteTypeCode( _resultSet.getString( _db_col_concept_cd ) );
      sourcedata.setSourceEncounterId( _resultSet.getString( _db_col_encounter_num ) );
      sourcedata.setSourceInstanceId( _resultSet.getLong( _db_col_instance_num )+"" );
      final Timestamp ts = _resultSet.getTimestamp( _db_col_start_date );
      sourcedata.setSourceOriginalDate( ts.toString() );
      metadata.setSourceData( sourcedata );
      jCas.addFsToIndexes( metadata );
      logger.log(SEVERE, metadata.getPatientID() + " " + sourcedata.getSourceEncounterId() + " " + sourcedata.getSourceInstanceId());
      return jCas;
   }


}


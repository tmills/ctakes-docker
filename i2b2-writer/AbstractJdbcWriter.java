import org.apache.ctakes.core.resource.JdbcConnectionResource;
import org.apache.ctakes.core.util.SourceMetadataUtil;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;

import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Write cas to a database using jdbc
 *
 * @author SPF , chip-nlp
 * @version %I%
 * @since 1/8/2015
 */
abstract public class AbstractJdbcWriter extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = Logger.getLogger( "AbstractJdbcWriter" );

   // Parameter names for the desc file
   static public final String PARAM_DB_CONN_RESRC = "DbConnResrcName";

   // Maximum row count for prepared statement batches
   static private final int MAX_BATCH_SIZE = 100;

//   @ConfigurationParameter(name=PARAM_DB_CONN_RESRC)
//   String resourceName;

   @ExternalResource(key=PARAM_DB_CONN_RESRC)
   JdbcConnectionResource jdbcConnectionResource;

   protected interface TableInfo {
      String getTableName();

      FieldInfo[] getFieldInfos();
   }

   protected interface FieldInfo {
      String getFieldName();

      int getFieldIndex();

      Class<?> getValueClass();
   }

   static protected class TableSqlInfo {
      final private PreparedStatement __preparedStatement;
      private int __batchCount;

      protected TableSqlInfo( final Connection connection, final TableInfo tableInfo ) throws SQLException {
         final String sql = createRowInsertSql( tableInfo.getTableName(), tableInfo.getFieldInfos() );
         LOGGER.info("sql with single quotes added around it = '" + sql + "'");
         __preparedStatement = connection.prepareStatement( sql );
         LOGGER.info("parm meta data = " + __preparedStatement.getParameterMetaData());
         LOGGER.info("getQueryTimeout = " + __preparedStatement.getQueryTimeout());
         LOGGER.info("See java.sql.Types for meaning of getParameterType");
         for (int i=0; i <= __preparedStatement.getParameterMetaData().getParameterCount(); i++) { // note start 0 just in case and go to <= because it's 1-based
             try {
            	 LOGGER.info("getParameterType("+i+")" + __preparedStatement.getParameterMetaData().getParameterType(i));
             } catch (SQLException e) {
            	  LOGGER.info("Unable to retrieve getParameterType("+i+")");
             }
             
             try {
             	 LOGGER.info("getParameterTypeName("+i+")" + __preparedStatement.getParameterMetaData().getParameterTypeName(i));
             } catch (SQLException e) {
             	  LOGGER.info("Unable to retrieve getParameterTypeName("+i+")");
             }
             
             try {
            	 LOGGER.info("getParameterClassName("+i+")" + __preparedStatement.getParameterMetaData().getParameterClassName(i));
             } catch (SQLException e) {
              	  LOGGER.info("Unable to retrieve getParameterClassName("+i+")");
             }

         }

      }

      protected PreparedStatement getPreparedStatement() {
         return __preparedStatement;
      }

      protected void setBatchCount( final int batchCount ) {
         __batchCount = batchCount;
      }

      protected int getBatchCount() {
         return __batchCount;
      }
   }


   final protected Map<String, TableSqlInfo> _tableSqlInfoMap = new HashMap<>();


   /**
    * {@inheritDoc}
    */
   @Override
   public void initialize(UimaContext context) throws ResourceInitializationException {
      super.initialize(context);
      //LOGGER.setLevel(Level.INFO);
      final Connection connection = jdbcConnectionResource.getConnection();
      final Collection<TableInfo> tableInfos = getTableInfos();
      try {
         for ( TableInfo tableInfo : tableInfos ) {
            _tableSqlInfoMap.put( tableInfo.getTableName(), new TableSqlInfo( connection, tableInfo ) );
         }
      } catch ( SQLException sqlE ) {
         // thrown by Connection.prepareStatement(..)
         throw new ResourceInitializationException( sqlE );
      }
   }

   /**
    * {@inheritDoc}
    * closes the PreparedStatements
    */
   @Override
   public void collectionProcessComplete( )
         throws AnalysisEngineProcessException {
      try {
         for ( TableSqlInfo tableSqlInfo : _tableSqlInfoMap.values() ) {
            tableSqlInfo.__preparedStatement.close();
         }
      } catch ( SQLException sqlE ) {
         LOGGER.warn( sqlE.getMessage() );
      }
      super.collectionProcessComplete( );
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void process( final JCas jcas ) throws AnalysisEngineProcessException {
      final SourceData sourceData = SourceMetadataUtil.getSourceData( jcas );
      if ( sourceData == null ) {
         LOGGER.error( "Missing source metadata for document!" );
         return;
      }
      final long patientNum = SourceMetadataUtil.getPatientNum( jcas );
      final int encounterNum;
      try {
         encounterNum = SourceMetadataUtil.getEncounterNum( sourceData );
      } catch (ResourceProcessException e) {
         throw new AnalysisEngineProcessException(e);
      }
      //String instance = SourceMetadataUtil.getSourceInstanceId( sourceData );
      String instance = "0000";
      if (instance==null) {
          instance = "null";  // unknown instance_num
      }
      final String providerId = SourceMetadataUtil.getProviderId( sourceData );
      final Timestamp startDate = SourceMetadataUtil.getStartDate( sourceData );
      try {

         writeJCasInformation( jcas, instance, encounterNum, patientNum, providerId, startDate );

         for ( TableSqlInfo tableSqlInfo : _tableSqlInfoMap.values() ) {
            if ( tableSqlInfo.getBatchCount() > 0 ) {
               LOGGER.info( "About to executeBatch()" );
               LOGGER.info( "  prepared statement = " +  tableSqlInfo.getPreparedStatement());

               tableSqlInfo.getPreparedStatement().executeBatch();
               // Not all drivers automatically clearCollection the batch.  This is considered by some to be a feature, by most a bug.
               tableSqlInfo.getPreparedStatement().clearBatch();
               tableSqlInfo.setBatchCount( 0 );
            }

         }
      } catch ( SQLException sqlE ) {
         // thrown by PreparedStatement methods
         throw new AnalysisEngineProcessException( sqlE );
      }
   }


   /**
    * Called from initialize()
    *
    * @return Table Info Objects for all tables of interest
    */
   abstract protected Collection<TableInfo> getTableInfos();

   /**
    * The main "process" method, called from processCas
    *
    * @throws SQLException if implementations throw SQLException
    */
   abstract protected void writeJCasInformation( final JCas jcas, 
                                                 final String instance,
                                                 final int encounterNum,
                                                 final long patientNum, final String providerId,
                                                 final Timestamp startDate ) throws SQLException;


   /**
    * @return the map of table name to table sql info objects
    */
   protected Map<String, TableSqlInfo> getTableSqlInfoMap() {
      return _tableSqlInfoMap;
   }

   /**
    * This is a safety method to set values of fieldInfoMaps instead of doing a direct .put in the map.
    * an IllegalArgumentException will be thrown if the given value is not the same class type as what the given
    * FieldInfo wants
    *
    * @param fieldInfoMap map in which to set the value
    * @param fieldInfo    key
    * @param value        value
    */
   static protected void setFieldInfoValue( final Map<FieldInfo, Object> fieldInfoMap,
                                            final FieldInfo fieldInfo, final Object value ) {
      final Class<?> valueClass = fieldInfo.getValueClass();
      if ( !valueClass.isInstance( value ) ) {
         throw new IllegalArgumentException( "Invalid Value for Field " + fieldInfo.getFieldName() );
      }
      fieldInfoMap.put( fieldInfo, value );
   }

   /**
    * Adds a new row of values to a batch in the prepared statement.  If the number of rows hits a maximum size (100)
    * then the batch is executed.
    *
    * @param preparedStatement -
    * @param batchSize         the current batch row count in the prepared statement
    * @param fieldInfoMap      for row value assignment
    * @return new batchCount (incremented by one or reset to zero)
    * @throws SQLException if a PreparedStatement call throws one or if there is a type, value mismatch in fieldInfoMap
    */
   static protected int writeTableRow( final PreparedStatement preparedStatement, final int batchSize,
                                       final Map<? extends FieldInfo, Object> fieldInfoMap ) throws SQLException {
      LOGGER.info( "writeTableRow " );
      LOGGER.info( "preparedStatment =  " + preparedStatement );
      for ( Map.Entry<? extends FieldInfo, Object> fieldInfoEntry : fieldInfoMap.entrySet() ) {
         final int fieldIndex = fieldInfoEntry.getKey().getFieldIndex();
         final Class<?> valueClass = fieldInfoEntry.getKey().getValueClass();
         final Object value = fieldInfoEntry.getValue();
         if ( valueClass.isAssignableFrom( String.class ) && String.class.isInstance( value ) ) {
            preparedStatement.setString( fieldIndex, (String)value );
         } else if ( valueClass.isAssignableFrom( Integer.class ) && Integer.class.isInstance( value ) ) {
            preparedStatement.setInt( fieldIndex, (Integer)value );
         } else if ( valueClass.isAssignableFrom( Long.class ) && Long.class.isInstance( value ) ) {
            preparedStatement.setLong( fieldIndex, (Long)value );
         } else if ( valueClass.isAssignableFrom( Float.class ) && Float.class.isInstance( value ) ) {
            preparedStatement.setFloat( fieldIndex, (Float)value );
         } else if ( valueClass.isAssignableFrom( Double.class ) && Double.class.isInstance( value ) ) {
            preparedStatement.setDouble( fieldIndex, (Double)value );
         } else if ( valueClass.isAssignableFrom( Boolean.class ) && Boolean.class.isInstance( value ) ) {
            preparedStatement.setBoolean( fieldIndex, (Boolean)value );
         } else if ( valueClass.isAssignableFrom( Timestamp.class ) && Timestamp.class.isInstance( value ) ) {
            preparedStatement.setTimestamp( fieldIndex, (Timestamp)value );
         } else {
            LOGGER.error( "About to throw SQLDataException ");
            LOGGER.error( "  for "  + fieldInfoEntry.getKey().getFieldName() );
            LOGGER.error( " with valueClass = " + valueClass);
            LOGGER.error( " with value = " + value);
            LOGGER.error( " with fieldIndex = " + fieldIndex);
            throw new SQLDataException( "Invalid Value Class for Field " + fieldInfoEntry.getKey().getFieldName() );
         }
      }
      LOGGER.info( "About to addBatch()" );
      preparedStatement.addBatch();
      if ( batchSize + 1 >= MAX_BATCH_SIZE ) {
               LOGGER.error( "About to executeBatch()" );
               LOGGER.info( "  prepared statement = " +  preparedStatement);
         try {
             preparedStatement.executeBatch();
         }  catch (java.sql.BatchUpdateException e) {
             LOGGER.error(e);         
             ///// >>>>>>>>>>>>>>>>
             LOGGER.error("parm meta data = " + preparedStatement.getParameterMetaData());
             LOGGER.error("getQueryTimeout = " + preparedStatement.getQueryTimeout());
             LOGGER.error("See java.sql.Types for meaning of getParameterType");
             for (int i=0; i <= preparedStatement.getParameterMetaData().getParameterCount(); i++) { // note start 0 just in case and go to <= because it's 1-based
                 try {
            	     LOGGER.error("getParameterType("+i+")" + preparedStatement.getParameterMetaData().getParameterType(i));
                 } catch (SQLException ignored) {
            	      LOGGER.error("Unable to retrieve getParameterType("+i+")");
                 }
                 
                 try {
             	     LOGGER.error("getParameterTypeName("+i+")" + preparedStatement.getParameterMetaData().getParameterTypeName(i));
                 } catch (SQLException ignored) {
             	      LOGGER.error("Unable to retrieve getParameterTypeName("+i+")");
                 }
                 
                 try {
            	     LOGGER.error("getParameterClassName("+i+")" + preparedStatement.getParameterMetaData().getParameterClassName(i));
                 } catch (SQLException ignored) {
              	      LOGGER.error("Unable to retrieve getParameterClassName("+i+")");
                 }

             }
             ///// <<<<<<<<<<<<<<<<<<

         }
         // Not all drivers automatically clear the batch.  This is considered by some to be a feature, by most a bug.
               LOGGER.info( "About to clearBatch()" );
         preparedStatement.clearBatch();
               LOGGER.info(  " done with clearBatch()" );
         return 0;
      }
     LOGGER.info(  " not a complete batch yet" );
      return batchSize + 1;
   }

   /**
    * @param tableName  -
    * @param fieldInfos -
    * @return -
    * @throws SQLDataException
    */
   static protected String createRowInsertSql( final String tableName,
                                               final FieldInfo... fieldInfos ) throws SQLDataException {
      if ( fieldInfos.length == 0 ) {
         throw new SQLDataException( "Must set at least one Field to create an sql insert Statement" );
      }
      final StringBuilder statement = new StringBuilder( "insert into" );
      final StringBuilder queries = new StringBuilder();
      statement.append( " " ).append( tableName );
      statement.append( " (" );
      for ( FieldInfo fieldInfo : fieldInfos ) {
         statement.append( fieldInfo.getFieldName() ).append( "," );
         queries.append( "?," );
      }
      // remove the last comma
      statement.setLength( statement.length() - 1 );
      queries.setLength( queries.length() - 1 );
      statement.append( ") values (" ).append( queries ).append( ")" );
      return statement.toString();
   }


}


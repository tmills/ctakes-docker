#!/bin/sh

if [ ! -e shared/broker_cert ] ;
then
  echo "Error: No broker_cert file found"
  exit -1
fi


# Call the java code to write the descriptor:
java \
-Doracle_table="$output_table_name" -Doracle_host="$oracle_host" -Doracle_user="$oracle_user" -Doracle_pw="$oracle_pw" \
-cp /apache-ctakes-4.0.0/lib/uimafit-core-2.2.0.jar:/apache-ctakes-4.0.0/lib/uimaj-core-2.9.0.jar:/apache-ctakes-4.0.0/lib/ctakes-type-system-4.0.0.jar:/apache-ctakes-4.0.0/lib/ctakes-core-4.0.0.jar:/apache-ctakes-4.0.0/lib/log4j-1.2.17.jar:.:/apache-ctakes-4.0.0/lib/spring-core-3.1.2.RELEASE.jar:/apache-ctakes-4.0.0/lib/spring-beans-3.1.2.RELEASE.jar:/apache-ctakes-4.0.0/lib/commons-lang-2.6.jar:/apache-ctakes-4.0.0/lib/commons-logging-1.1.1.jar:/apache-ctakes-4.0.0/lib/commons-io-2.2.jar \
CreateDbWriterDescriptor desc/custom_descriptor.xml

keytool -importcert -noprompt -file shared/broker_cert -alias brokerCA -keystore /usr/lib/jvm/java-1.8-openjdk/jre/lib/security/cacerts -storepass changeit

./deployAsyncService.sh desc/deploymentDescriptor.xml -brokerURL https://$broker_host:$broker_port


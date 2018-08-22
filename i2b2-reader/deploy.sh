#!/bin/sh

if [ ! -e shared/broker_cert ] ;
then
  echo "Error: No broker_cert file found"
  exit -1
fi

export UIMA_CLASSPATH=/apache-uima-as-2.10.3/lib/:ctakes-i2b2.jar:i2b2.jar:ojdbc8.jar:/apache-ctakes-4.0.0/lib/
keytool -importcert -noprompt -file shared/broker_cert -alias brokerCA -keystore /usr/lib/jvm/java-1.8-openjdk/jre/lib/security/cacerts -storepass changeit

# Call the java code to write the descriptor:
java \
-Doracle_table="$input_table_name" -Doracle_host="$oracle_host" -Doracle_user="$oracle_user" -Doracle_pw="$oracle_pw" \
-cp "ctakes-i2b2.jar:/apache-ctakes-4.0.0/lib/*:." \
CreateDbReaderDescriptor desc/custom_reader.xml

./runRemoteAsyncAE.sh -p 10  https://$broker_host:$broker_port mainQueue -d desc/localDeploymentDescriptor.xml  -c desc/custom_reader.xml


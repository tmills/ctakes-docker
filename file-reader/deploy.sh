#!/bin/sh

if [ ! -e shared/broker_cert ] ;
then
  echo "Error: No broker_cert file found"
  exit -1
fi

keytool -importcert -noprompt -file shared/broker_cert -alias brokerCA -keystore /usr/lib/jvm/java-1.8-openjdk/jre/lib/security/cacerts -storepass changeit

export UIMA_CLASSPATH=reader.jar:/apache-ctakes-4.0.0/lib/

# Call the java code to write the descriptor:

./runRemoteAsyncAE.sh -p 10  https://$broker_host:$broker_port mainQueue -d desc/localDeploymentDescriptor.xml  -c desc/FilesInDirectoryCollectionReader.xml

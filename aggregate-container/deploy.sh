#!/bin/sh

if [ ! -e shared/broker_cert ] ;
then
  echo "Error: No broker_cert file found"
  exit -1
fi

export ACTIVEMQ_LIB=$UIMA_HOME/apache-activemq/lib

keytool -importcert -noprompt -file shared/broker_cert -alias brokerCA -keystore /usr/lib/jvm/java-1.8-openjdk/jre/lib/security/cacerts -storepass changeit

java -Dbroker_host="$broker_host" -Dbroker_port="$broker_port" -cp "code.jar:$UIMA_HOME/lib/*:$ACTIVEMQ_LIB/*:." GenerateDeploymentDescriptors

./deployAsyncService.sh aggregateDeploymentDescriptor-auto.xml -brokerURL "failover:https://$broker_host:$broker_port"

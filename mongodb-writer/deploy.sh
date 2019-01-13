#!/bin/sh

if [ ! -e shared/broker_cert ] ;
then
  echo "Error: No broker_cert file found"
  exit -1
fi

# Create certificate so we can connect with broker over https:
keytool -importcert -noprompt -file shared/broker_cert -alias brokerCA -keystore /usr/lib/jvm/java-1.8-openjdk/jre/lib/security/cacerts -storepass changeit

./deployAsyncService.sh desc/deploymentDescriptor.xml -brokerURL https://$broker_host:$broker_port


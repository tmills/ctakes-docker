#!/bin/sh

# Call the java code to write the descriptor:
java \
-Doracle_table="$1" -Doracle_host="$2" -Doracle_user="$3" -Doracle_pw="$4" \
-cp uimafit-core-2.2.0.jar:uimaj-core-2.9.0.jar:ctakes-type-system-4.0.0.jar:ctakes-core-4.0.0.jar:log4j-1.2.17.jar:.:spring-core-3.1.2.RELEASE.jar:spring-beans-3.1.2.RELEASE.jar:commons-lang-2.6.jar:commons-logging-1.1.1.jar:commons-io-2.2.jar \
CreateDbWriterDescriptor desc/custom_descriptor.xml

./deployAsyncService.sh desc/deploymentDescriptor.xml -brokerURL tcp://$5:$6


#!/bin/sh

export UIMA_CLASSPATH=ctakes-i2b2.jar:i2b2.jar:ojdbc8.jar:/apache-ctakes-4.0.0/lib/

# Call the java code to write the descriptor:
java \
-Doracle_table="$1" -Doracle_url="$2" -Doracle_user="$3" -Doracle_pw="$4" \
-cp "ctakes-i2b2.jar:/apache-ctakes-4.0.0/lib/*:." \
CreateDbReaderDescriptor desc/custom_reader.xml

./runRemoteAsyncAE.sh -p 10  tcp://$5:$6 mainQueue -d desc/localDeploymentDescriptor.xml  -c desc/custom_reader.xml

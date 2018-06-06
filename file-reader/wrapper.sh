#!/bin/sh

export UIMA_CLASSPATH=reader.jar:/apache-ctakes-4.0.0/lib/

# Call the java code to write the descriptor:

./runRemoteAsyncAE.sh -p 10  tcp://$5:$6 mainQueue -d desc/localDeploymentDescriptor.xml  -c desc/FilesInDirectoryCollectionReader.xml

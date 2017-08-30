## cTAKES-Docker README

This project contains docker container definitions and config files to run
Apache cTAKES pipelines in a distributed, containerized fashion. The goal
is to create containers for collection readers, pipelines, and consumers,
and parameterized scripts for starting them at scale on HIPAA-compliant cloud
platforms.

Status as of June 21 2017: We have container definitions for the Apache
ActiveMQ server that coordinates between readers and pipelines. We have
one analysis engine for doing de-identification (mist) and another for
annotating concepts with negation, subject, and history attributes (ctakes-as-pipeline).
These can be run together in an EC2 instance.

### Steps for running pipeline locally:

#### Prerequisites:
1. Install Apache UIMA-AS and cTAKES with the proper environment variables:
```
cd /opt
wget http://apache.mirrors.ovh.net/ftp.apache.org/dist//uima/uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz
tar -xvf uima-as-2.9.0-bin.tar.gz
rm uima-as-2.9.0-bin.tar.gz
export UIMA_HOME=/opt/apache-uima-as-2.9.0 # you'll want to store this in your .bashrc as well
wget http://www-us.apache.org/dist//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz
tar -xvf apache-ctakes-4.0.0-bin.tar.gz
rm apache-ctakes-4.0.0-bin.tar.gz
export UIMA_CLASSPATH=/opt/apache-ctakes-4.0.0/lib # you'll want to store this in your .bashrc as well
```

2. The SHARP de-identification model cannot be publicly released, and in fact, there are no publicly available models for Mist that I am aware of (please let us know if you are aware of any!). If you do not have access to SHARP (you probably do not), you have two options:

 i) Use MIST and your own data to create your own model with the generic HIPAA framework. This is outside the scope of this readme and requires understanding Mist and its documentation. Installing that model and fixing the rest of the project to use it would look something like this:
```
sed -i'.bak' 's/install\ src\/tasks\/SHARP/install\ src\/tasks\/HIPAA/' mist/Dockerfile
sed -i'.bak' 's/RUN\ mkdir\ src\/tasks\/SHARP/#RUN\ mkdir\ src\/tasks\/SHARP/'  mist/Dockerfile
sed -i'.bak' 's/COPY\ SHARP\ src\/tasks\/SHARP/#COPY\ SHARP\ src\/tasks\/SHARP/' mist/Dockerfile
sed -i'.bak' 's/SHARP/HIPAA/' mist/MistAnalysisEngine.java
```
 ii) Skip de-identification. There are replacement pipelines that do not do de-identification. You will need to rebuild the ctakes-as-pipeline container, pointing it to the descriptor ```desc/nodeidPipeline.xml``` and when you run the CVD, point it to ```remoteNoDeid.xml``` instead of ```remoteFull.xml```. Adjust the pipeline by running the following:
```
sed -i'.bak' 's/dictionaryPipeline.xml/nodeidPipeline.xml/' ctakes-as-pipeline/Dockerfile
sed -i'.bak' 's/dictionaryPipeline.xml/nodeidPipeline.xml/' ctakes-as-pipeline/desc/deploymentDescriptor.xml
```

3. Copy env_file_sample.txt to env_file.txt and add your UMLS credentials and IP
address and port of broker to appropriate environment variables.

  *Note: The IP address must be visible from inside containers - something like the DHCP-assigned IP address of the host system running all the commands. In other words, localhost and 127.0.0.1 won't work here even if everything is running on the same machine.*

#### Steps:
1. Build containers inside each subdirectory (note that if you are running without de-identification, you can skip the Mist steps):
```
mist> docker build -t mist-container .
amq-broker> docker build -t amq-image .
ctakes-as-pipeline> docker build -t ctakes-as-pipeline .
```

2. Start AMQ container:
`./bin/runBrokerContainer.sh`

3. Start Mist container:
`./bin/runMistContainer.sh`

4. Start Pipeline container:
`./bin/runPipelineContainer.sh`

5. Start CVD:
`$UIMA_HOME/bin/cvd.sh`

6. Load descriptor to full pipeline:
`Run->Load AE->./desc/remoteFull.xml`

7. Enter text into text window.

8. Run descriptor: `Run->Run Aggregate with de-identification`

#### Output:

In most cases, you'll want to view the de-identified text and annotations with `Select View->DeidView`. However, if you're operating in a programmatic context, an xmi file (among other formats) is available for processing.

If you wish to view the annotations in an easy to use and visually rich viewer, run `$UIMA_HOME/bin/annotationViewer.sh` and select the descriptor used for processing, your outputted xmi file, and the Java Viewer.

### Running via collection reader
If you want to run on a collection of files rather than through the debugger, modify this sample pipeline. Perform the first 4 steps as above, then:

1. Edit `desc/remoteFastDescriptor.xml` and change `<import location="/Users/tmill/Projects/apache-ctakes/ctakes-clinical-pipeline/desc/analysis_engine/docker-fast-dictionary.xml"/>` to your absolute location.

2. Edit `desc/FilesInDirectoryCollectionReader.xml` and change `<string>samples/</string>` to the location that unstructured clinical text files will be placed for processing.

3. Run `./bin/runRemoteAsyncAE.sh tcp://<local ip address>:61616 mainQueue -d desc/localDeploymentDescriptor.xml -c desc/FilesInDirectoryCollectionReader.xml -o xmis/`. Note that `local ip address` is the address of the host you are running the command on.

4. Observe the outputted XMI in `xmis/`. You may use `CVD` to import the files if you want a visually rich experience.

### Running on ec2
If you install docker on an ec2 instance and check out this repo, you can build
the images and start mostly as in 1-4.

On our instance, we do not have all ports exposed so I modified the broker
container startup script (2) so that it maps port 80 on the host to 61616 on
the broker container:
`docker run -d -p 80:61616 amq-image`

then you change the env_file.txt to point to port 80 and the other scripts
should work as before.

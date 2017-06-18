## cTAKES-Docker README

This project contains docker container definitions and config files to run
Apache cTAKES pipelines in a distributed, containerized fashion. The goal
is to create containers for collection readers, pipelines, and consumers,
and parameterized scripts for starting them at scale on HIPAA-compliant cloud
platforms.

Status as of May 11, 2017: We have container definitions for the Apache
ActiveMQ server that coordinates between readers and pipelines. We have
one analysis engine for doing de-identification (mist) and another for
annotating concepts with negation, subject, and history attributes (ctakes-as-pipeline).

### Steps for running pipeline locally:

#### Prerequisites:
1. Install Apache UIMA and set $UIMA_HOME:
```
cd /opt
wget http://apache.org/dist/uima/uimaj-2.10.0/uimaj-2.10.0-bin.tar.gz
tar -xvf uimaj-2.10.0-bin.tar.gz
rm uimaj*.gz
export UIMA_HOME=/opt/apache-uima # you'll want to store this in your .bashrc as well
```

2. The SHARP de-identification model (licensing status unclear) in mist/SHARP. If not using SHARP, the generic HIPAA model can be used via:
```
sed -i 's/install\ src\/tasks\/SHARP/install\ src\/tasks\/HIPAA/' mist/Dockerfile
sed -i 's/SHARP\ Deidentification/HIPAA\ Deidentification/' mist/MistAnalysisEngine.java
```

3. Copy env_file_sample.txt to env_file.txt and add your UMLS credentials and IP
address and port of broker to appropriate environment variables.

#### Steps:
1. Build containers inside each subdirectory:
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

9. Look at de-identified text and annotations with `Select View->DeidView`


### Running via collection reader
If you want to run on a collection of files rather than through the debugger,
modify this sample pipeline. Perform the first 4 steps as above, then:

5. `./bin/runRemoteAsyncAE.sh tcp://<local ip address>:61616 mainQueue -d desc/localDeploymentDescriptor.xml -c desc/FilesInDirectoryCollectionReader.xml -o xmis/`

Replacing `<my ip address>` with the IP address of the host you are running the command on. This will read from the samples sub-directory and write the output to serialized xmi files in the xmis subdirectory. You can use the CVD as above to view the annotations of these files. To modify this for your data, edit the FilesInDirectoryCollectionReader.xml file to point at a folder on your machine, or use a completely different collection reader if you like.


### Running on ec2
If you install docker on an ec2 instance and check out this repo, you can build
the images and start mostly as in 1-4.

On our instance, we do not have all ports exposed so I modified the broker
container startup script (2) so that it maps port 80 on the host to 61616 on
the broker container:
`docker run -d -p 80:61616 amq-image`

then you change the env_file.txt to point to port 80 and the other scripts
should work as before.

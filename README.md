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
* Apache UIMA somewhere on your system ($UIMA_HOME)
* The SHARP de-identification model (licensing status unclear) in mist/SHARP
* Copy env_file_sample.txt to env_file.txt and add your UMLS credentials and IP
address to appropriate environment variables.

#### Steps:
1. Build containers inside each subdirectory:
```
mist> docker build -t mist-container .
amq-broker> docker built -t amq-image .
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

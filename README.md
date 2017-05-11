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

4. Start CVD:
`$UIMA_HOME/bin/cvd.sh`

5. Load descriptor to mist:
`Run->Load AE->./desc/remoteMist.xml`

6. Enter text into text window.

7. Run descriptor: `Run->Run remoteMist`

8. Look at de-identified text with `Select View->DeidView`


### Running the ctakes pipeline works similarly, but by running `bin/runPipelineContainer.sh` and using the descriptor `desc/remoteFastDescriptor.xml`.

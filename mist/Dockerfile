FROM ubuntu:latest

ENV MIST_VER 1_3_1
ENV MIST_HOME /MIST_1_3_1
ENV UIMA_HOME=/apache-uima-as-2.9.0

RUN apt-get -y update && apt-get -y install openjdk-8-jdk wget git zip python

RUN wget -O MIST_1.3.1.zip "https://sourceforge.net/projects/mist-deid/files/MIST 1.3.1/MIST_1_3_1.zip/download"
RUN unzip MIST_1.3.1.zip
RUN wget -q -O - http://archive.apache.org/dist/uima/uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -

## Run mist install and then install the SHARP-trained deidentification model:
WORKDIR $MIST_HOME
## Install script looks for line in java version output that starts "java version" but that is specific to oracle jvm
## so we just change the regex via regex :/
RUN perl -pi -e 's/java version/openjdk version/' install.py
RUN ./install.sh
RUN mkdir src/tasks/SHARP
COPY SHARP src/tasks/SHARP/
RUN src/MAT/bin/MATManagePluginDirs install src/tasks/SHARP

## Copy over the UIMA annotator and descriptor files
WORKDIR /

COPY MistAnalysisEngine.java /
COPY mistDescriptor.xml /
COPY mistDeploymentDescriptor.xml /
COPY deployAsyncService.sh /
COPY uimafit-core-2.2.0.jar /
COPY uimaj-core-2.9.0.jar /
COPY commons-lang-2.6.jar /
RUN javac -cp uimafit-core-2.2.0.jar:uimaj-core-2.9.0.jar:. MistAnalysisEngine.java
RUN jar cf mist.jar MistAnalysisEngine.class

CMD ./deployAsyncService.sh mistDeploymentDescriptor.xml -brokerURL "tcp://$broker_host:$broker_port"

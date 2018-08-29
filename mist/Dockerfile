FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip python git perl

ENV MIST_VER 1_3_1
ENV MIST_HOME /MIST_1_3_1
ENV UIMA_HOME=/apache-uima-as-2.10.3
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties
ENV UIMA_JVM_OPTS="-Xms2G -Xmx5G"

RUN wget -O MIST_1.3.1.zip "https://sourceforge.net/projects/mist-deid/files/MIST 1.3.1/MIST_1_3_1.zip/download"
RUN unzip MIST_1.3.1.zip
RUN wget -q -O - http://archive.apache.org/dist/uima/uima-as-2.10.3/uima-as-2.10.3-bin.tar.gz | tar xzf -
RUN wget -q -O ctakes-type-system-4.0.0.jar http://search.maven.org/remotecontent?filepath=org/apache/ctakes/ctakes-type-system/4.0.0/ctakes-type-system-4.0.0.jar
RUN wget -q -O ctakes-core-4.0.0.jar http://search.maven.org/remotecontent?filepath=org/apache/ctakes/ctakes-core/4.0.0/ctakes-core-4.0.0.jar

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

COPY Logging.properties /
COPY mistDescriptor.xml /
COPY mistDeploymentDescriptor.xml /
COPY deployAsyncService.sh /
COPY uimafit-core-2.2.0.jar /
COPY commons-lang-2.6.jar /
COPY jcarafe_xmlrpc-0.9.2-bin.jar /
COPY MistAnalysisEngine.java /
COPY TypeSystem.xml /
COPY deploy.sh /

RUN mkdir shared 
RUN javac -cp ctakes-core-4.0.0.jar:ctakes-type-system-4.0.0.jar:uimafit-core-2.2.0.jar:$UIMA_HOME/lib/uima-core.jar:jcarafe_xmlrpc-0.9.2-bin.jar:. MistAnalysisEngine.java
RUN jar cf mist.jar *.class

CMD /deploy.sh

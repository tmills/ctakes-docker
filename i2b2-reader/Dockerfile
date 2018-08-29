FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -O apache-ctakes-4.0.0-bin.tar.gz http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz
RUN tar xzf apache-ctakes-4.0.0-bin.tar.gz

RUN wget -O uima-as-2.10.3-bin.tar.gz http://archive.apache.org/dist/uima//uima-as-2.10.3/uima-as-2.10.3-bin.tar.gz
RUN tar xzf uima-as-2.10.3-bin.tar.gz

RUN mkdir /desc
RUN mkdir /outputs

COPY desc/localDeploymentDescriptor.xml /desc/
COPY desc/remoteFull.xml /desc/
COPY desc/docker-mist.xml /desc/
COPY desc/docker-fast-dictionary.xml /desc/
COPY desc/docker-writer.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.10.3
ENV CTAKES_HOME=/apache-ctakes-4.0.0
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties
ENV UIMA_JVM_OPTS="-Xms2G -Xmx5G"

COPY ojdbc8.jar /
COPY ctakes-i2b2.jar /
COPY CreateDbReaderDescriptor.java /
COPY MemReleaseI2b2CollectionReader.java /

COPY runRemoteAsyncAE.sh /
COPY deploy.sh /
COPY Logging.properties /

RUN javac -cp ctakes-i2b2.jar:$UIMA_HOME/lib/uima-core.jar:$CTAKES_HOME/lib/uimafit-core-2.2.0.jar:$CTAKES_HOME/lib/ctakes-type-system-4.0.0.jar:$CTAKES_HOME/lib/ctakes-core-4.0.0.jar:$CTAKES_HOME/lib/log4j-1.2.17.jar  CreateDbReaderDescriptor.java MemReleaseI2b2CollectionReader.java
RUN jar cf i2b2.jar *.class

#CMD ./wrapper.sh $input_table_name $oracle_host $oracle_user $oracle_pw $broker_host $broker_port
CMD ./deploy.sh

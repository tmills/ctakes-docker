FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -

RUN mkdir /desc
RUN mkdir /outputs

COPY Logging.properties /
COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/
COPY desc/mongoWriter.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties

# This is not included in the repo so find it yourself and put in mongodb-writer directory before building
COPY mongo-java-driver-3.9.1.jar /

COPY deploy.sh /

COPY MongoDBWriter.java /

RUN javac -cp $UIMA_HOME/lib/uima-core.jar:$CTAKES_HOME/lib/uimafit-core-2.2.0.jar:$CTAKES_HOME/lib/ctakes-type-system-4.0.0.jar:$CTAKES_HOME/lib/ctakes-core-4.0.0.jar:$CTAKES_HOME/lib/log4j-1.2.17.jar:mongo-java-driver-3.9.1.jar MongoDBWriter.java

RUN jar cf mongodb-writer.jar *.class

CMD /deploy.sh
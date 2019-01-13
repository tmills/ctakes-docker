FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -

RUN mkdir /desc
RUN mkdir /outputs

COPY Logging.properties /
COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties

COPY mongo-java-driver-3.9.1.jar /


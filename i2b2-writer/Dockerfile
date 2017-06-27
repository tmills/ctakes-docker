FROM ubuntu:latest

RUN apt-get -y update && apt-get -y install openjdk-8-jdk wget zip
RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -

RUN mkdir /desc
RUN mkdir /outputs

COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/
COPY desc/i2b2Writer.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0

COPY uimafit-core-2.2.0.jar /
COPY uimaj-core-2.9.0.jar /
COPY ctakes-type-system-4.0.0.jar /
COPY ctakes-core-4.0.0.jar /
COPY log4j-1.2.17.jar /
COPY I2b2ReadyFileWriter.java /

RUN javac -cp uimafit-core-2.2.0.jar:uimaj-core-2.9.0.jar:ctakes-type-system-4.0.0.jar:ctakes-core-4.0.0.jar:log4j-1.2.17.jar:javax.annotation-api-1.2.jar:. I2b2ReadyFileWriter.java

RUN jar cf i2b2.jar *.class

CMD ./deployAsyncService.sh desc/deploymentDescriptor.xml -brokerURL "tcp://$broker_host:$broker_port"

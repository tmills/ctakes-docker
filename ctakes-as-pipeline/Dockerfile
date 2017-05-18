FROM ubuntu:latest

RUN apt-get -y update && apt-get -y install openjdk-8-jdk wget zip
RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -
RUN wget -q -O dict.zip https://downloads.sourceforge.net/project/ctakesresources/ctakes-resources-4.0-bin.zip
RUN unzip -o dict.zip -d apache-ctakes-4.0.0/
RUN mkdir /desc

COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/
COPY desc/dictionaryPipeline.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0

CMD ./deployAsyncService.sh desc/deploymentDescriptor.xml -brokerURL "tcp://$broker_host:61616"

FROM ubuntu:latest

RUN apt-get -y update && apt-get -y install openjdk-8-jdk wget git
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.4.0/uima-as-2.4.0-bin.tar.gz | tar xzf -
EXPOSE 61616

## FIXME -- maybe create a release and wget and avoid using git
RUN git clone https://github.com/tmills/ctakes-docker.git

ENV UIMA_HOME=/apache-uima-as-2.4.0

CMD ./ctakes-docker/bin/startBroker.sh

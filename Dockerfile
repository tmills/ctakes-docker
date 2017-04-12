FROM ubuntu:latest

RUN apt-get -y update && apt-get -y install openjdk-8-jdk git wget zip
RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-3.2.2/apache-ctakes-3.2.2-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.4.0/uima-as-2.4.0-bin.tar.gz | tar xzf -
RUN wget -q -O dict.zip http://sourceforge.net/projects/ctakesresources/files/ctakessnorx-3.2.1.1.zip
RUN unzip -o dict.zip -d apache-ctakes-3.2.2/resources/org/apache/ctakes/dictionary/lookup/fast/ctakessnorx/
EXPOSE 61616

## FIXME -- maybe create a release and wget and avoid using git
RUN git clone https://github.com/tmills/ctakes-docker.git

ENV UIMA_HOME=/apache-uima-as-2.4.0
ENV CTAKES_HOME=/apache-ctakes-3.2.2


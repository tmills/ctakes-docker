FROM ubuntu:latest

RUN apt-get -y update && apt-get -y install openjdk-8-jdk wget
RUN wget -q -O - http://archive.apache.org/dist/activemq/5.14.5/apache-activemq-5.14.5-bin.tar.gz | tar xzf -
EXPOSE 61616

COPY startBroker.sh /
COPY conf/* /apache-activemq-5.14.5/conf/

CMD ./startBroker.sh

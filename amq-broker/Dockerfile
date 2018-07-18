FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -q -O - http://archive.apache.org/dist/activemq/5.14.5/apache-activemq-5.14.5-bin.tar.gz | tar xzf -
EXPOSE 61616

COPY startBroker.sh /
COPY conf/* /apache-activemq-5.14.5/conf/
COPY bin/env /apache-activemq-5.14.5/bin/

CMD ./startBroker.sh

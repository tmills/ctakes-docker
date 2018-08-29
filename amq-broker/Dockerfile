FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -q -O - http://archive.apache.org/dist/activemq/5.15.2/apache-activemq-5.15.2-bin.tar.gz | tar xzf -
EXPOSE 8080

COPY startBroker.sh /
COPY conf/* /apache-activemq-5.15.2/conf/
COPY bin/env /apache-activemq-5.15.2/bin/

RUN mkdir certificate

CMD ./startBroker.sh

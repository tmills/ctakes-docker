FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -

RUN wget -q -O dict.zip https://downloads.sourceforge.net/project/ctakesresources/sno_rx_16ab.zip

RUN unzip -o dict.zip -d apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/

RUN wget https://sourceforge.net/p/ctakesresources/code/HEAD/tree/trunk/ctakes-resources-snomed-rword-hsqldb-2011ab/src/main/resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015/ctakesicd2015.properties

RUN wget https://sourceforge.net/p/ctakesresources/code/HEAD/tree/trunk/ctakes-resources-snomed-rword-hsqldb-2011ab/src/main/resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015/ctakesicd2015.script

RUN mkdir apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015

RUN mv ctakesicd2015* apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/

RUN mkdir /desc

COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/
COPY desc/nodeidPipeline.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0

CMD ./deployAsyncService.sh desc/deploymentDescriptor.xml -brokerURL "tcp://$broker_host:$broker_port"

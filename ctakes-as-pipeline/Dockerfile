FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

## ~1.7G
RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -

## ~76M
RUN wget -q -O dict.zip https://downloads.sourceforge.net/project/ctakesresources/sno_rx_16ab.zip
RUN unzip -o dict.zip -d apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.10.3/uima-as-2.10.3-bin.tar.gz | tar xzf -

# RUN mkdir apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/other_dictionary
# COPY other_dictionary apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/other_dictionary

COPY DefaultDictionaryLookupSpec.xml apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/

COPY UmlsLookupAnnotator.xml apache-ctakes-4.0.0/desc/ctakes-dictionary-lookup-fast/desc/analysis_engine/

RUN mkdir /desc

COPY log4j-1.2.17.jar /

COPY Logging.properties /
COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/
COPY desc/nodeidPipeline.xml /desc/
COPY desc/dictionaryPipeline.xml /desc/
COPY desc/NonIgnorableSegmentAnnotator.xml /apache-ctakes-4.0.0/desc/
COPY DeidAwareTermConsumer.java /
COPY IgnorableSectionAnnotator.java /
COPY lookup-descriptor-template.xml /apache-ctakes-4.0.0/resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab.xml
COPY deploy.sh /

ENV UIMA_HOME=/apache-uima-as-2.10.3
ENV CTAKES_HOME=/apache-ctakes-4.0.0
ENV UIMA_JVM_OPTS="-Xms2G -Xmx5G"
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties

RUN javac -cp $UIMA_HOME/lib/uima-core.jar:$CTAKES_HOME/lib/ctakes-dictionary-lookup-fast-4.0.0.jar:$CTAKES_HOME/lib/ctakes-core-4.0.0.jar:$CTAKES_HOME/lib/ctakes-type-system-4.0.0.jar:$CTAKES_HOME/lib/uimafit-core-2.2.0.jar DeidAwareTermConsumer.java
RUN javac -cp $UIMA_HOME/lib/uima-core.jar:$CTAKES_HOME/lib/ctakes-dictionary-lookup-fast-4.0.0.jar:$CTAKES_HOME/lib/ctakes-core-4.0.0.jar:$CTAKES_HOME/lib/ctakes-type-system-4.0.0.jar:$CTAKES_HOME/lib/uimafit-core-2.2.0.jar:$CTAKES_HOME/lib/log4j-1.2.17.jar IgnorableSectionAnnotator.java
RUN jar cf consumer.jar DeidAwareTermConsumer.class IgnorableSectionAnnotator.class


CMD /deploy.sh

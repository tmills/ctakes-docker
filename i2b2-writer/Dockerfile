FROM openjdk:8-alpine

RUN apk update && apk add ca-certificates openssl wget unzip

RUN wget -q -O - http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz | tar xzf -
RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz | tar xzf -

RUN mkdir /desc
RUN mkdir /outputs

COPY Logging.properties /
COPY deployAsyncService.sh /
COPY desc/deploymentDescriptor.xml /desc/
#COPY desc/deploymentDescriptorDbWriter.xml /desc/
#COPY desc/deploymentDescriptorFileWriter.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties

COPY ojdbc8.jar /
#COPY commons-io-2.2.jar /
#COPY commons-logging-1.1.1.jar /
#COPY commons-lang-2.6.jar /
#COPY spring-beans-3.1.2.RELEASE.jar /
#COPY spring-core-3.1.2.RELEASE.jar /
COPY I2b2ReadyFileWriter.java /
COPY I2b2JdbcWriter.java /
COPY AbstractJdbcWriter.java /
COPY CreateDbWriterDescriptor.java /

COPY deploy.sh /

RUN javac -cp $CTAKES_HOME/lib/uimafit-core-2.2.0.jar:$UIMA_HOME/lib/uima-core.jar:$CTAKES_HOME/lib/ctakes-type-system-4.0.0.jar:$CTAKES_HOME/lib/ctakes-core-4.0.0.jar:$CTAKES_HOME/lib/log4j-1.2.17.jar:javax.annotation-api-1.2.jar:. I2b2ReadyFileWriter.java
RUN javac -cp $UIMA_HOME/lib/uima-core.jar:$CTAKES_HOME/lib/uimafit-core-2.2.0.jar:$CTAKES_HOME/lib/ctakes-type-system-4.0.0.jar:$CTAKES_HOME/lib/ctakes-core-4.0.0.jar:$CTAKES_HOME/lib/log4j-1.2.17.jar AbstractJdbcWriter.java I2b2JdbcWriter.java CreateDbWriterDescriptor.java
RUN jar cf i2b2.jar *.class

CMD /deploy.sh

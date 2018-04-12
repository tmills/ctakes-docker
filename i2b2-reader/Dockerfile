FROM openjdk:8-alpine

RUN echo 'https://dl-3.alpinelinux.org/alpine/v3.4/main' > /etc/apk/repositories  && \
    echo '@testing https://dl-3.alpinelinux.org/alpine/edge/testing' >> /etc/apk/repositories && \
    echo '@community https://dl-3.alpinelinux.org/alpine/v3.4/community'

RUN apk update && apk add ca-certificates openssl wget unzip

ENV http_proxy http://192.168.13.14:3128
ENV https_proxy http://192.168.13.14:3128

RUN wget -O apache-ctakes-4.0.0-bin.tar.gz http://apache.osuosl.org//ctakes/ctakes-4.0.0/apache-ctakes-4.0.0-bin.tar.gz
RUN tar xzf apache-ctakes-4.0.0-bin.tar.gz

RUN wget -O uima-as-2.9.0-bin.tar.gz http://archive.apache.org/dist/uima//uima-as-2.9.0/uima-as-2.9.0-bin.tar.gz
RUN tar xzf uima-as-2.9.0-bin.tar.gz

RUN mkdir /desc
RUN mkdir /outputs

COPY desc/localDeploymentDescriptor.xml /desc/

ENV UIMA_HOME=/apache-uima-as-2.9.0
ENV CTAKES_HOME=/apache-ctakes-4.0.0

COPY ojdbc8.jar /
COPY ctakes-i2b2.jar /
# COPY uimafit-core-2.2.0.jar /
# COPY uimaj-core-2.9.0.jar /
# COPY ctakes-type-system-4.0.0.jar /
# COPY ctakes-core-4.0.0.jar /
# COPY log4j-1.2.17.jar /
# COPY commons-io-2.2.jar /
# COPY commons-logging-1.1.1.jar /
# COPY commons-lang-2.6.jar /
# COPY spring-beans-3.1.2.RELEASE.jar /
# COPY spring-core-3.1.2.RELEASE.jar /
COPY CreateDbReaderDescriptor.java /

COPY wrapper.sh /

RUN javac -cp ctakes-i2b2.jar:/apache-ctakes-4.0.0/lib/uimaj-core-2.9.0.jar:/apache-ctakes-4.0.0/lib/uimafit-core-2.2.0.jar:/apache-ctakes-4.0.0/lib/ctakes-type-system-4.0.0.jar:/apache-ctakes-4.0.0/lib/ctakes-core-4.0.0.jar:/apache-ctakes-4.0.0/lib/log4j-1.2.17.jar  CreateDbReaderDescriptor.java
RUN jar cf i2b2.jar *.class

CMD ./wrapper.sh $input_table_name $oracle_url $oracle_user $oracle_pw $broker_host $broker_port

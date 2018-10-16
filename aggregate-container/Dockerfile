FROM openjdk:8-alpine

# RUN echo 'https://dl-3.alpinelinux.org/alpine/v3.4/main' > /etc/apk/repositories  && \
#     echo '@testing https://dl-3.alpinelinux.org/alpine/edge/testing' >> /etc/apk/repositories && \
#     echo '@community https://dl-3.alpinelinux.org/alpine/v3.4/community'

RUN apk update && apk add ca-certificates openssl wget unzip

# ENV http_proxy http://192.168.13.10:3128
# ENV https_proxy http://192.168.13.10:3128

RUN wget -q -O - http://archive.apache.org/dist/uima//uima-as-2.10.3/uima-as-2.10.3-bin.tar.gz | tar xzf -

COPY deploy.sh /

ENV UIMA_HOME=/apache-uima-as-2.10.3
ENV UIMA_JVM_OPTS="-Xms2G -Xmx5G"
ENV UIMA_LOGGER_CONFIG_FILE=/Logging.properties


CMD ./deploy.sh

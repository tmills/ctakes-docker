#!/bin/sh

#   Licensed to the Apache Software Foundation (ASF) under one
#   or more contributor license agreements.  See the NOTICE file
#   distributed with this work for additional information
#   regarding copyright ownership.  The ASF licenses this file
#   to you under the Apache License, Version 2.0 (the
#   "License"); you may not use this file except in compliance
#   with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing,
#   software distributed under the License is distributed on an
#   #  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#   KIND, either express or implied.  See the License for the
#   specific language governing permissions and limitations
#   under the License.

# ActiveMQ needs a HOME
export ACTIVEMQ_HOME=/apache-activemq-5.15.2

# ActiveMQ needs a writeable directory for the log files and derbydb.
if [ -z "$ACTIVEMQ_BASE" ] ; then
  export ACTIVEMQ_BASE=$ACTIVEMQ_HOME
fi

# If the directory doesn't exist, make it
if [ ! -d "$ACTIVEMQ_BASE" ] ; then
  mkdir "$ACTIVEMQ_BASE"
  mkdir "$ACTIVEMQ_BASE/conf"
fi

if [ ! -x "$ACTIVEMQ_HOME/bin/activemq" ]; then
    chmod +x "$ACTIVEMQ_HOME/bin/activemq"
fi

# Set the public IP with the cmd line argument if it exists
if [ ! -z "$1" ]; then
  echo "Setting public ip to $1"
  public_ip=$1
elif [ ! -z "$broker_host" ]; then
  echo "Setting public ip based on broker_host environment variable of $broker_host"
  public_ip=$broker_host
else
  # otherwise check amazon
  echo "Attempting to set public ip based on AWS check"
  public_ip=`wget -q -O - http://169.254.169.254/latest/meta-data/public-hostname`
fi

# If still empty go with localhost, which probably won't work!
if [ -z $public_ip ]; then
  public_ip=localhost
fi
echo "public_ip set to $public_ip"

password=`date +%s | sha256sum | base64 | head -c 10 ; echo`
keytool -genkey -noprompt -alias broker -keyalg RSA -keystore /certificate/broker.ks -keypass $password -storepass $password -dname "CN=$public_ip, OU=CHIP, O=BCH, L=Boston, S=MA, C=US" -deststoretype pkcs12

keytool -export -noprompt -alias broker -keystore /certificate/broker.ks -file /certificate/broker_cert -storepass $password

export ACTIVEMQ_SSL_OPTS="-Djavax.net.ssl.keyStore=/certificate/broker.ks -Djavax.net.ssl.keyStorePassword=$password"

"$ACTIVEMQ_HOME/bin/activemq" "console" "xbean:file:$ACTIVEMQ_BASE/conf/activemq-nojournal.xml"


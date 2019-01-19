#!/bin/bash
envfile="env_file.txt"
if [ -r "$envfile" ]; then
  . env_file.txt
else
  echo "File $envfile not found!"
  exit 1
fi

cert_dir=`pwd`/certificate
rm -f $cert_dir/*

# The following maps $broker_port to 61616 which should match what 
# is EXPOSEd by the Dockerfile for the broker
docker run --name amq-broker -d -p $broker_port:8080 -e "broker_host=$broker_host" -v $cert_dir:/certificate amq-image 

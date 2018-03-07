#!/bin/bash
envfile="env_file.txt"
if [ -r "$envfile" ]; then
  . env_file.txt
else
  echo "File $envfile not found!"
  exit 1
fi
# The following maps $broker_port to 61616 which should match what 
# is EXPOSEd by the Dockerfile for the broker
docker run --name amq-broker -d -p $broker_port:61616 amq-image

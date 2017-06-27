#!/bin/bash
. env_file.txt
docker run -d -p $broker_port:61616 amq-image

#!/bin/bash

docker run --name mist  --env-file env_file.txt -v ~/ctakes-docker/log:/log -d mist-image

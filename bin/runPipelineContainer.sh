#!/bin/bash

shared_dir=`pwd`/shared

docker run --name ctakes-pipeline -v $shared_dir:/shared --env-file env_file.txt -d ctakes-as-pipeline

#!/bin/bash

docker run --name ctakes-pipeline -v ~/ctakes-docker/log:/log --env-file env_file.txt -d ctakes-as-pipeline

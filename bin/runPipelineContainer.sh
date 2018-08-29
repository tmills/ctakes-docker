#!/bin/bash

docker run --name ctakes-pipeline -v ~/ctakes-docker/shared:/shared --env-file env_file.txt -d ctakes-as-pipeline

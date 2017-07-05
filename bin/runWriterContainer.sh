#!/bin/bash

mkdir -p outputs
docker run --env-file env_file.txt -v ~/ctakes-docker/outputs/:/outputs -d i2b2-writer

#!/bin/bash

docker run --name ctakes-pipeline  --env-file env_file.txt -d ctakes-as-pipeline

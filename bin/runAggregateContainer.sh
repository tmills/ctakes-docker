#!/bin/bash

docker run --name aggregate-container -v ~/ctakes-docker/shared:/shared --env-file env_file.txt -d aggregate-container

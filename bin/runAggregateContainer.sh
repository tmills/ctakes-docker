#!/bin/bash

shared_dir=`pwd`/shared
docker run --name aggregate-container -v $shared_dir:/shared --env-file env_file.txt -d aggregate-container

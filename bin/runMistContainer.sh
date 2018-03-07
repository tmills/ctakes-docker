#!/bin/bash

docker run --name mist  --env-file env_file.txt -d mist-image

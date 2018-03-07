#!/bin/bash

if [ "$1" = "" ]
then
  echo
  echo "ERROR: Name of table to write to not given. Not processing any data."
  echo "       This script (" $0 ") requires one argument."
  echo
  exit 1
fi

if [ ! -f env_file.txt ]; then
    echo "ERROR: File env_file.txt not found."
    echo "       This script (" $0 ") requires an env_file.txt file."
    exit 1
fi

mkdir -p outputs
docker run --name i2b2-writer --env-file env_file.txt -v ~/ctakes-docker/outputs/:/outputs -d i2b2-writer 




#!/bin/bash

if [ ! -f env_file.txt ]; then
    echo "ERROR: File env_file.txt not found."
    echo "       This script (" $0 ") requires an env_file.txt file."
    exit 1
fi

docker run --name i2b2-reader --env-file env_file.txt -v ~/ctakes-docker/shared:/shared -d i2b2-reader




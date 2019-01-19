#!/bin/bash

if [ ! -f env_file.txt ]; then
    echo "ERROR: File env_file.txt not found."
    echo "       This script (" $0 ") requires an env_file.txt file."
    exit 1
fi

shared_dir=`pwd`/shared
out_dir=`pwd`/outputs

docker run --name mongodb-writer --env-file env_file.txt -v $out_dir:/outputs -v $shared_dir:/shared -d mongodb-writer




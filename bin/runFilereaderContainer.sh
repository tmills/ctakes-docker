#!/bin/bash

if [ ! -f env_file.txt ]; then
    echo "ERROR: File env_file.txt not found."
    echo "       This script (" $0 ") requires an env_file.txt file."
    exit 1
fi

shared_dir=`pwd`/shared
file_dir=`pwd`/files

docker run --name file-reader --rm --env-file env_file.txt -v $shared_dir:/shared -v $file_dir:/files -d file-reader


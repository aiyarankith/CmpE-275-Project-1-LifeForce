#!/bin/bash
# setup the python directory

basedir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
pydir=${basedir}/python

if [ -f ${pydir}/data_pb2.py ]; then
    rm ${pydir}/data_pb2.py 
fi

PROTOHOME=/usr/local/protobuf-2.5.0/bin
$PROTOHOME/protoc -I=${basedir} --python_out=${pydir} ${basedir}/data.proto

# this places the data_pb2.py file in a subdirectory of python

#!/bin/bash
#
# creates the python classes for our .proto
#

project_base="/home/ankith/core-netty-4.2/python"

rm ${project_base}/src/comm_pb2.py

protoc -I=${project_base}/resources --python_out=./src ../resources/comm.proto 

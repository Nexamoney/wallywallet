#!/bin/bash
cat readme.txt
docker build -t andrewstone/adk .
docker push andrewstone/adk:latest
command -v alertme && alertme "docker adk built"

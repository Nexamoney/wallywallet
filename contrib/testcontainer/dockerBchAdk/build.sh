#!/bin/bash
cat readme.txt
docker build -t andrewstone/bchadk .
docker push andrewstone/bchadk:latest
# If you want, create a script in your path called "alertme" that takes a string and somehow alerts you.
command -v alertme && alertme "docker BCH ADK built"

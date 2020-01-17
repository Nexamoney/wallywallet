#!/bin/bash
cat readme.txt
docker build -t andrewstone/bchadkdev .
docker push andrewstone/bchadkdev:latest
# If you want, create a script in your path called "alertme" that takes a string and somehow alerts you.
command -v alertme && alertme "docker BCH ADK dev built"

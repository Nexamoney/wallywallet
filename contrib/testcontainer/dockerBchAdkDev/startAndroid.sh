#!/bin/bash

# Note docker must be started with --devices=/dev/kvm (and --expose 5900 -p 5900:5900 to vnc the emulator's screen) or in gitlab-runner: /etc/gitlab-runner/config.toml [runners.docker] devices=["/dev/kvm"]

# Create a fake screen to send the android emulator's display to
Xvfb :1 -screen 0 1024x1024x24 &
export DISPLAY=:1
# start it up clean -- this assumes that an avd (android virtual device) was already created named a28_1
(cd /opt/adk/tools; emulator -no-audio -gpu off -no-snapshot -wipe-data -avd a28_1 &)

#!/bin/bash
rm -rf build
rm -rf androidApp/build
rm -rf .cxx
rm -rf app/build app/release
rm -rf .idea
rm -rf .gradle
find . -name "*.iml" -delete

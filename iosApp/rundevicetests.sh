#!/bin/bash

# The following command lists all available devices and their UDIDs:
# xcrun xctrace list devices

# Build the app and run test on device
XCODEBUILD_OUT=$(xcodebuild \
  -project iosApp.xcodeproj \
  -scheme "iosApp" \
  -sdk iphoneos \
  -destination "platform=iOS,id=$1" \
  test 2>&1)

XCODEBUILD_EXIT_CODE=$?

if [ $XCODEBUILD_EXIT_CODE -ne 0 ]; then
  # we need to keep the string we use here up to date
  MISSING_LINKED_PHONE=$(echo $XCODEBUILD_OUT | grep -c "xcodebuild: WARNING: Using the first of multiple matching destinations")
  if [ $MISSING_LINKED_PHONE -eq 0 ]; then
    echo "$XCODEBUILD_OUT"
  fi
  echo "----------------------"
  echo "Build failed, exiting."
  exit 1
fi

echo $XCODEBUILD_OUT | xcpretty -r junit --output iphone-ui-test-report.xml

echo "Test succesfully, exiting."

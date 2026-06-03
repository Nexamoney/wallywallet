#!/bin/bash

# The following command lists all available devices and their UDIDs:
# xcrun xctrace list devices

# Stable, predictable location for the .xcresult bundle so CI can collect it as
# an artifact. The path is relative to this script's working dir (iosApp/), which
# keeps it inside CI_PROJECT_DIR. xcodebuild refuses to write to an existing
# path, so clear it first and make sure the parent dir exists.
RESULT_BUNDLE_PATH="build/Test.xcresult"
rm -rf "$RESULT_BUNDLE_PATH"
mkdir -p "$(dirname "$RESULT_BUNDLE_PATH")"

# Build the app and run test on device
XCODEBUILD_OUT=$(xcodebuild \
  -project iosApp.xcodeproj \
  -scheme "iosApp" \
  -sdk iphoneos \
  -destination "platform=iOS,id=$1" \
  -resultBundlePath "$RESULT_BUNDLE_PATH" \
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

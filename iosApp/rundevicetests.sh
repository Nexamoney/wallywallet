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

# When the App Store Connect API key variables are present (CI), let xcodebuild
# authenticate to the developer portal itself so automatic signing can register
# the device and regenerate the managed profile when a capability or entitlement
# was added. The runner needs no signed-in Xcode account for this. The key only
# exists on disk for the duration of this script, is 0600, and is removed on
# exit. Without these variables the build uses the cached profile exactly as
# before, so local runs are unchanged.
AUTH_FLAGS=()
if [ -n "$KEYSP8" ] && [ -n "$key_id" ] && [ -n "$KEYSJSON" ]; then
  KEY_DIR=$(mktemp -d)
  trap 'rm -rf "$KEY_DIR"' EXIT
  echo "$KEYSP8" | base64 --decode > "$KEY_DIR/AuthKey_$key_id.p8"
  chmod 600 "$KEY_DIR/AuthKey_$key_id.p8"
  ISSUER_ID=$(echo "$KEYSJSON" | base64 --decode | python3 -c 'import json,sys; print(json.load(sys.stdin)["issuer_id"])')
  AUTH_FLAGS=(-allowProvisioningUpdates \
    -authenticationKeyPath "$KEY_DIR/AuthKey_$key_id.p8" \
    -authenticationKeyID "$key_id" \
    -authenticationKeyIssuerID "$ISSUER_ID")
fi

# Build the app and run test on device
XCODEBUILD_OUT=$(xcodebuild \
  -project iosApp.xcodeproj \
  -scheme "iosApp" \
  -sdk iphoneos \
  -destination "platform=iOS,id=$1" \
  -resultBundlePath "$RESULT_BUNDLE_PATH" \
  "${AUTH_FLAGS[@]}" \
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

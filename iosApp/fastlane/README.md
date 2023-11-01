fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

## env

Add your environment variables to the iosApp/.env file in the format KEY=VALUE. For example:

```.env
APPLE_ID=myappleid@example.com
ITC_TEAM_ID="1231231"
TEAM_ID="123456789"
APP_STORE_CONNECT_API_KEY_PATH=fastlane/keys/123456789.json
```

Add app store connect key to:
- iosApp/fastlane/keys/<teamid>.json
- iosApp/fastlane/keys/<teamid>.p8

Tutorial: https://docs.fastlane.tools/app-store-connect-api/


# Available Actions

## iOS

### ios beta

```sh
[bundle exec] fastlane ios beta
```

Push a new beta build to TestFlight

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).

# Internationalization

This directory holds the **source of truth** for all translatable strings:

* `i18n/res/values/strings.xml` --> English. Defines every key.
* `i18n/res/values-<lang>/strings.xml` --> one file per translated locale.

Note that `i18n/res` is a **symlink** to `shared/src/androidMain/res`, so
`i18n/res/values/strings.xml` and `shared/src/androidMain/res/values/strings.xml` are the
same file. Edit it via either path; there is nothing to keep in sync. The single file has
two consumers: Android's resource compiler reads it directly to generate `R.string.*`,
and the Gradle task below reads it to generate the `.bin` packs and the `S` object.

## How it works

The `:shared:generateI18nFiles` Gradle task reads these XML files on every build and generates:

* `strings_<lang>.bin` --> one binary pack per locale (null-terminated UTF-8 strings,
  looked up by position), written to `shared/build/generated/i18n/`.
* `strings.kt` --> the `S` object, mapping each key name to its index in the `.bin`.

Because both are generated from the same sorted key list, `S.someKey` always resolves to
the right string. The task is wired into the Kotlin compile and resource-processing tasks,
so it runs automatically for the Android, JVM and iOS targets. The iOS app additionally
runs it from a "Generate i18n Strings" build phase, since Xcode copies the `.bin` into the
app bundle itself.

**The generated files are no longer committed to the repository.** They live under
`build/` and are produced on demand.

## Adding or changing a string

1. Add the key to `i18n/res/values/strings.xml` (same file as
   `shared/src/androidMain/res/values/strings.xml` --> add it once, not twice).
2. Add translations to `i18n/res/values-<lang>/strings.xml` (optional, untranslated keys
   fall back to English, and the build prints a per-locale count of how many did).
3. Rebuild. Nothing else to do.

Reference the new string as `S.yourKey` in shared code, `R.string.yourKey` also resolves
from the same entry, but only on Android.

## Adding a locale

1. Create `i18n/res/values-<lang>/strings.xml`.
2. Add `<lang>` to `i18nLangs` in `shared/build.gradle.kts`.
3. For iOS, also add the new `.bin` to the `iosApp` target's Resources build phase and to
   the outputs of the "Generate i18n Strings" build phase in `iosApp.xcodeproj`.

## Note on `run.sh` / `preprocess.kt`

These are the **superseded** standalone generator. They are kept only for reference.
Do not run `run.sh`: it writes `strings.kt` and the `.bin` files back into
`shared/src/commonMain/`, where they would collide with the generated copies and fail the
build with a redeclaration of `object S`.

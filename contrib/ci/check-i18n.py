#!/usr/bin/env python3
"""Fail when a locale is missing a string that English defines.

The locales are the ones `i18nLangs` in shared/build.gradle.kts lists, so this
checks exactly what :shared:generateI18nFiles packs. A key the English file
marks translatable="false" is shared by every locale and needs no entry, and
neither does asset_statements, the App Links JSON that Android reads verbatim.

A new English string therefore has to land with its translation in every
locale, in the same merge request.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "i18n/res"
NOT_TRANSLATED = {"asset_statements"}


def strings(path):
    """Returns {key: translatable} for every <string> in a strings.xml."""
    return {
        s.get("name"): s.get("translatable") != "false"
        for s in ET.parse(path).getroot().iter("string")
    }


def locales():
    gradle = (ROOT / "shared/build.gradle.kts").read_text()
    m = re.search(r"val i18nLangs\s*=\s*listOf\(([^)]*)\)", gradle)
    if not m:
        sys.exit("check-i18n: cannot find i18nLangs in shared/build.gradle.kts")
    return re.findall(r'"([^"]+)"', m.group(1))


def main():
    english = strings(RES / "values/strings.xml")
    required = {k for k, translatable in english.items() if translatable} - NOT_TRANSLATED

    failed = False
    for lang in locales():
        missing = sorted(required - strings(RES / f"values-{lang}/strings.xml").keys())
        if missing:
            failed = True
            print(f"values-{lang}: {len(missing)} untranslated: {', '.join(missing)}")

    if failed:
        print("\nEvery English string needs a translation in each locale listed above, "
              "in i18n/res/values-<lang>/strings.xml.")
        return 1
    print(f"check-i18n: all {len(required)} translatable keys present in {len(locales())} locales")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/bin/sh
# Publishes a GitLab Release for the app version in gradle/libs.versions.toml,
# with a changelog listing every merge request merged since the previous release.
# Run by the gitlabRelease job in .gitlab-ci.yml on every push to main.
#
# Preview what CI would publish, without credentials and without publishing:
#
#     contrib/ci/release-changelog.sh --dry-run
#
# A new version exists when wallyApp differs from the value it had at the last
# release. Both sides of that comparison are the variable itself: the current
# one is read from the working tree, the released one from the same file at the
# last release's commit, fetched through the API so an ordinary push to main
# never needs more than the shallow clone GitLab gives it.
#
# Comparing against the last release rather than against the previous commit is
# what makes a missed bump recoverable: if a bump's pipeline goes red, is
# skipped or is auto-cancelled, wallyApp still differs from the released value,
# so the next push to main publishes it. Re-runs are no-ops for the same reason.
#
# The change list starts at the last release, so the notes cover exactly what
# shipped between the two versions. Before any release exists it starts at the
# previous change to wallyApp on main instead, so the first release still gets
# a real change list.
#
# The version is an opaque label, not a semver value. androidApp/build.gradle.kts
# turns "3.30.04" into versionCode 33004 by stripping the dots, so the leading
# zero matters. Never normalise it and never pass a component to $(( )): 08 and
# 09 are invalid octal in POSIX shells, so version 3.30.08 would abort the script.

set -eu

VERSION_FILE="gradle/libs.versions.toml"
VERSION_KEY="wallyApp"
TAG_PREFIX="release-"
MAX_ENTRIES="${MAX_ENTRIES:-200}"

DRY_RUN=0
HEAD_REV=""
VERSION=""
SINCE=""

case "${RELEASE_FORCE:-}" in
    [Tt]rue|[Yy]es|1) FORCE=1 ;;
    *) FORCE=0 ;;
esac

usage() {
    cat <<'EOF'
Usage: release-changelog.sh [options]

  --dry-run        Render the notes and print the payload; publish nothing.
  --force          Publish even when wallyApp matches the last release.
  --since <rev>    Start the change list here instead of at the last release.
  --version <ver>  Use this version instead of reading gradle/libs.versions.toml.
  --head <rev>     Release this commit (default: $CI_COMMIT_SHA, else HEAD).
  -h, --help       Show this text.

Environment:
  CI_API_V4_URL, CI_PROJECT_ID, CI_PROJECT_URL, CI_SERVER_URL, CI_COMMIT_SHA
                     supplied by GitLab CI; sensible defaults outside CI.
  CI_JOB_TOKEN       authenticates release creation in CI.
  RELEASE_API_TOKEN  optional access token with api scope, preferred over
                     CI_JOB_TOKEN when set.
  RELEASE_FORCE      set to true to publish a version that is already released,
                     which is how a bump whose pipeline never published is
                     recovered: re-run the job with this variable set.
  MAX_ENTRIES        cap on the number of listed changes (default 200).
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1 ;;
        --force) FORCE=1 ;;
        --since) SINCE="$2"; shift ;;
        --version) VERSION="$2"; shift ;;
        --head) HEAD_REV="$2"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

cd "$(git rev-parse --show-toplevel)"

API_URL="${CI_API_V4_URL:-https://gitlab.com/api/v4}"
PROJECT="${CI_PROJECT_ID:-wallywallet%2Fwallet}"
PROJECT_URL="${CI_PROJECT_URL:-https://gitlab.com/wallywallet/wallet}"
SERVER_URL="${CI_SERVER_URL:-https://gitlab.com}"
[ -n "$HEAD_REV" ] || HEAD_REV="${CI_COMMIT_SHA:-HEAD}"

# Everything the script produces lives outside the checkout, so a local dry run
# leaves no untracked files behind.
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
NOTES_FILE="$WORK_DIR/release-notes.md"

# printf, not echo: commit text reaches these, and echo expands backslash
# escapes in dash and in macOS /bin/sh (not in the job's busybox ash).
fail() { printf 'release-changelog: error: %s\n' "$*" >&2; exit 1; }
warn() { printf 'release-changelog: warning: %s\n' "$*" >&2; }

# Writes the response body to $WORK_DIR/body and echoes the HTTP status, so the
# caller can act on 404 and 409 instead of dying on them. Unauthenticated when
# no token is set, which is what makes --dry-run work on a workstation.
api() {
    api_method="$1"
    api_path="$2"
    shift 2
    if [ -n "${RELEASE_API_TOKEN:-}" ]; then
        set -- --header "PRIVATE-TOKEN: $RELEASE_API_TOKEN" "$@"
    elif [ -n "${CI_JOB_TOKEN:-}" ]; then
        set -- --header "JOB-TOKEN: $CI_JOB_TOKEN" "$@"
    fi
    curl --silent --show-error --retry 3 --request "$api_method" \
        --output "$WORK_DIR/body" --write-out '%{http_code}' \
        "$@" "$API_URL/projects/$PROJECT/$api_path"
}

extract_version() {
    sed -n "s/^$VERSION_KEY *= *\"\([^\"]*\)\".*/\1/p" | head -1
}

# GitLab clones this project shallow (depth 40) and without tags. A shallow
# "git log a..b" stops at the graft point and exits 0, so an unnoticed shallow
# clone yields a plausible but truncated changelog. Only the publishing path
# pays for the full history; an ordinary merge never gets here.
unshallow() {
    [ "$(git rev-parse --is-shallow-repository)" = "true" ] || return 0
    echo "release-changelog: clone is shallow, fetching the full history"
    git fetch --quiet --unshallow origin || git fetch --quiet origin || true
    if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
        fail "clone is still shallow; add GIT_DEPTH: \"0\" to the job variables"
    fi
}

# --------------------------------------------------------------------------
# Decide: has wallyApp changed since the last release?
# --------------------------------------------------------------------------

if [ -z "$VERSION" ]; then
    VERSION="$(extract_version < "$VERSION_FILE")"
fi
[ -n "$VERSION" ] || fail "no $VERSION_KEY version found in $VERSION_FILE"
case "$VERSION" in
    ''|*[!0-9.]*) fail "version '$VERSION' is not a dotted number" ;;
esac

TAG="$TAG_PREFIX$VERSION"
HEAD_SHA="$(git rev-parse "$HEAD_REV")"

# The last release is the anchor for both halves of the job: the version to
# compare against, and the point the change list starts from.
STATUS="$(api GET "releases?per_page=1")"
[ "$STATUS" = 200 ] || fail "GET releases returned HTTP $STATUS: $(cat "$WORK_DIR/body")"
LAST_SHA="$(jq -r '.[0].commit.id // ""' "$WORK_DIR/body")"
LAST_TAG="$(jq -r '.[0].tag_name // ""' "$WORK_DIR/body")"

RELEASED_VERSION=""
if [ -n "$LAST_SHA" ]; then
    # Read the variable out of the same file at the released commit. Going
    # through the API rather than git keeps this working on the shallow clone,
    # where that commit is usually not present.
    FILE_PATH="$(printf '%s' "$VERSION_FILE" | sed 's|/|%2F|g')"
    STATUS="$(api GET "repository/files/$FILE_PATH/raw?ref=$LAST_SHA")"
    case "$STATUS" in
        200) RELEASED_VERSION="$(extract_version < "$WORK_DIR/body")" ;;
        404) warn "$VERSION_FILE does not exist at $LAST_TAG; treating $VERSION as new" ;;
        *) fail "reading $VERSION_FILE at $LAST_TAG returned HTTP $STATUS" ;;
    esac
fi

if [ -z "$RELEASED_VERSION" ]; then
    echo "release-changelog: no previous release; publishing $VERSION as the first one"
elif [ "$VERSION" != "$RELEASED_VERSION" ]; then
    echo "release-changelog: $VERSION_KEY changed $RELEASED_VERSION -> $VERSION since $LAST_TAG"
else
    echo "release-changelog: $VERSION_KEY is unchanged at $VERSION since $LAST_TAG, nothing to do"
    [ "$FORCE" = 1 ] || [ "$DRY_RUN" = 1 ] || exit 0
    echo "release-changelog: publishing it anyway"
fi

# A version can differ from the last release and still be released already, if
# it was reverted to an earlier number. Creating it again would only earn a 409.
STATUS="$(api GET "releases/$TAG")"
case "$STATUS" in
    404) : ;;
    200)
        echo "release-changelog: $TAG is already released, nothing to do"
        [ "$FORCE" = 1 ] || [ "$DRY_RUN" = 1 ] || exit 0
        ;;
    *) fail "GET releases/$TAG returned HTTP $STATUS: $(cat "$WORK_DIR/body")" ;;
esac

# --------------------------------------------------------------------------
# Render
# --------------------------------------------------------------------------

unshallow

SINCE_TAG=""
if [ -z "$SINCE" ]; then
    if [ -n "$LAST_SHA" ]; then
        SINCE="$LAST_SHA"
        SINCE_TAG="$LAST_TAG"
    else
        # No release to measure from yet, so measure from the previous time
        # wallyApp changed on main. --first-parent makes this the merge commit
        # that carried the bump, not the commit inside the merged branch.
        SINCE="$(git log -1 --first-parent --format='%H' -G"^$VERSION_KEY *=" \
            "$HEAD_SHA^" -- "$VERSION_FILE" 2>/dev/null || true)"
    fi
fi

RANGE_OK=0
SINCE_LABEL="${SINCE_TAG:-$SINCE}"
if [ -n "$SINCE" ]; then
    if git rev-parse --quiet --verify "$SINCE^{commit}" >/dev/null 2>&1; then
        RANGE_OK=1
        SINCE="$(git rev-parse "$SINCE^{commit}")"
        if [ -n "$SINCE_TAG" ]; then
            SINCE_LABEL="[$SINCE_TAG]($PROJECT_URL/-/releases/$SINCE_TAG)"
        else
            SINCE_LABEL="[\`$(git log -1 --format='%h' "$SINCE")\`]($PROJECT_URL/-/commit/$SINCE)"
        fi
    else
        warn "$SINCE_LABEL is not reachable in this clone; omitting the change list"
    fi
fi

: > "$WORK_DIR/entries"
ENTRY_COUNT=0
if [ "$RANGE_OK" = 1 ]; then
    git log --first-parent --format='%H' "$SINCE..$HEAD_SHA" > "$WORK_DIR/commits"
    while read -r sha; do
        [ -n "$sha" ] || continue
        git log -1 --format='%b' "$sha" > "$WORK_DIR/msg"
        # A merge of a merge request carries "See merge request <path>!<iid>",
        # and its own subject is only the source branch name, so the readable
        # title is the first non-empty body line. Merges also carry extra
        # trailers such as "Closes #505 and #658" after that line.
        mr="$(sed -n 's|^See merge request \([^ !]*\)!\([0-9][0-9]*\).*|\1 \2|p' \
            "$WORK_DIR/msg" | head -1)"
        if [ -n "$mr" ]; then
            mr_path="${mr% *}"
            mr_iid="${mr##* }"
            title="$(grep -v '^See merge request ' "$WORK_DIR/msg" |
                sed -n '/[^[:space:]]/{p;q;}')"
            link="[!$mr_iid]($SERVER_URL/$mr_path/-/merge_requests/$mr_iid)"
        else
            # Direct pushes to main are blocked today but exist in the history.
            title=""
            link="[\`$(git log -1 --format='%h' "$sha")\`]($PROJECT_URL/-/commit/$sha)"
        fi
        [ -n "$title" ] || title="$(git log -1 --format='%s' "$sha")"
        printf -- '- %s (%s)\n' "$title" "$link" >> "$WORK_DIR/entries"
        ENTRY_COUNT=$((ENTRY_COUNT + 1))
    done < "$WORK_DIR/commits"
fi

{
    echo "## Wally $VERSION"
    echo
    if [ "$RANGE_OK" != 1 ]; then
        if [ -z "$SINCE" ]; then
            echo "First release. Later releases list every merge request merged"
            echo "since the previous release."
        else
            echo "The previous release could not be resolved in this clone, so the"
            echo "change list is omitted."
        fi
        echo
        printf 'Built from [`%s`](%s).\n' \
            "$(git log -1 --format='%h' "$HEAD_SHA")" "$PROJECT_URL/-/commit/$HEAD_SHA"
    else
        printf '[%s change(s)](%s) since %s.\n' \
            "$ENTRY_COUNT" "$PROJECT_URL/-/compare/$SINCE...$HEAD_SHA" "$SINCE_LABEL"
        echo
        if [ "$ENTRY_COUNT" -gt "$MAX_ENTRIES" ]; then
            warn "$ENTRY_COUNT entries since $SINCE_LABEL, listing the newest $MAX_ENTRIES"
            echo "Only the $MAX_ENTRIES most recent changes are listed; the compare link has the rest."
            echo
            head -n "$MAX_ENTRIES" "$WORK_DIR/entries"
        else
            cat "$WORK_DIR/entries"
        fi
    fi
} > "$NOTES_FILE"

echo "release-changelog: notes for $TAG at $HEAD_SHA:"
echo
cat "$NOTES_FILE"
echo

# --------------------------------------------------------------------------
# Publish
# --------------------------------------------------------------------------

if [ "$DRY_RUN" = 1 ]; then
    echo "release-changelog: dry run, nothing published"
    exit 0
fi

# tag_name plus ref creates the tag as part of creating the release, so the job
# needs no push credentials. The job token is read-only on the Tags API but has
# full access to the Releases API.
jq -n --arg name "Wally $VERSION" --arg tag "$TAG" --arg ref "$HEAD_SHA" \
    --rawfile description "$NOTES_FILE" \
    '{name: $name, tag_name: $tag, ref: $ref, description: $description}' \
    > "$WORK_DIR/payload.json"

STATUS="$(api POST releases --header "Content-Type: application/json" \
    --data @"$WORK_DIR/payload.json")"
case "$STATUS" in
    201) echo "release-changelog: created $PROJECT_URL/-/releases/$TAG" ;;
    # 409 is "Release already exists": a concurrent pipeline won the race, which
    # is the outcome this job wants anyway.
    409) echo "release-changelog: $TAG was created by another job, nothing to do" ;;
    *) fail "POST releases returned HTTP $STATUS: $(cat "$WORK_DIR/body")" ;;
esac

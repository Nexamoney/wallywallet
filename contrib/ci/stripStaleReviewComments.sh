#!/bin/sh
# Removes stale review comments from the default branch after a merge.
#
# A "stale review comment" is a line whose first non-whitespace text is the
# marker "//<<<". Developers use these in MRs to explain why old code is being
# changed or removed; once the MR is merged the referenced code no longer
# exists, so the comments are stripped here. Regular "//" comments are kept.
#
# Every code file still carrying a marker is stripped. Normally that is exactly
# the set of files changed by the push that triggered this job, but the
# tree-wide sweep also self-heals markers orphaned by an earlier push whose
# pipeline never ran this job (e.g. auto-canceled): the next merge catches
# them. Only whole lines are removed — the marker must be on its own line.
# The result is committed and pushed back to the branch with CI skipped. If
# the branch advanced while this job ran, the strip is redone on the new tip
# rather than rebased, so a concurrent merge can never cause a conflict.
#
# Required environment (see .gitlab-ci.yml):
#   STRIP_COMMENTS_PUSH_TOKEN  project access token with write_repository scope
#   CI_COMMIT_BRANCH / CI_SERVER_HOST / CI_PROJECT_PATH
set -eu

MARKER_REGEX='^[[:space:]]*//<<<'

# Strips every eligible tracked file that contains marker lines; sets
# stripped=1 if any file was rewritten.
strip_marked_files() {
    stripped=0
    git grep -l "$MARKER_REGEX" > /tmp/marked-files.txt || :
    while IFS= read -r file; do
        case "$file" in
            *.kt|*.kts|*.java|*.swift|*.m|*.h|*.js|*.ts|*.gradle) ;;
            *) continue ;;
        esac
        [ -f "$file" ] || continue
        count=$(grep -c "$MARKER_REGEX" "$file") || continue
        # grep -v exits 1 when it selects nothing (the file was only markers);
        # that is a valid, empty result.
        grep -v "$MARKER_REGEX" "$file" > "$file.strip-tmp" || [ $? -eq 1 ]
        mv "$file.strip-tmp" "$file"
        echo "Stripped $count stale review comment line(s) from $file"
        stripped=1
    done < /tmp/marked-files.txt
}

git config user.name "CI Review-Comment Cleanup"
git config user.email "ci-cleanup@${CI_SERVER_HOST}"
PUSH_URL="https://strip-comments:${STRIP_COMMENTS_PUSH_TOKEN}@${CI_SERVER_HOST}/${CI_PROJECT_PATH}.git"

attempt=1
while :; do
    strip_marked_files
    if [ "$stripped" -eq 0 ]; then
        echo "No stale review comments found."
        exit 0
    fi
    git commit -am "chore: strip stale review comments [skip ci]"
    if git push "$PUSH_URL" "HEAD:refs/heads/${CI_COMMIT_BRANCH}" -o ci.skip; then
        echo "Cleanup commit pushed to ${CI_COMMIT_BRANCH}."
        exit 0
    fi
    if [ "$attempt" -ge 3 ]; then
        echo "Failed to push cleanup commit after ${attempt} attempts." >&2
        exit 1
    fi
    attempt=$((attempt + 1))
    # The branch moved while this job ran. Rebasing the cleanup commit onto
    # the new tip could conflict with the newer changes; redoing the strip
    # there cannot, because deleting marker lines applies cleanly to any tree.
    git fetch origin "$CI_COMMIT_BRANCH"
    git checkout --force --detach FETCH_HEAD
done

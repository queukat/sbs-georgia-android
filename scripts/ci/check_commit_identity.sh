#!/usr/bin/env bash
set -euo pipefail

EXPECTED_NAME="${EXPECTED_NAME:-queukat}"
EXPECTED_EMAIL="${EXPECTED_EMAIL:-75810528+queukat@users.noreply.github.com}"
EXPECTED_SIGNOFF="Signed-off-by: ${EXPECTED_NAME} <${EXPECTED_EMAIL}>"
COMMIT_RANGE="${COMMIT_RANGE:-HEAD}"

mapfile -t commits < <(git rev-list --reverse "${COMMIT_RANGE}")

if [[ ${#commits[@]} -eq 0 ]]; then
  echo "No commits to validate for range: ${COMMIT_RANGE}"
  exit 0
fi

failures=0

for commit in "${commits[@]}"; do
  author_name="$(git show -s --format=%an "${commit}")"
  author_email="$(git show -s --format=%ae "${commit}")"
  committer_name="$(git show -s --format=%cn "${commit}")"
  committer_email="$(git show -s --format=%ce "${commit}")"
  message="$(git show -s --format=%B "${commit}")"

  if [[ "${author_name}" != "${EXPECTED_NAME}" || "${author_email}" != "${EXPECTED_EMAIL}" ]]; then
    echo "Commit ${commit} has unexpected author: ${author_name} <${author_email}>"
    failures=1
  fi

  if [[ "${committer_name}" != "${EXPECTED_NAME}" || "${committer_email}" != "${EXPECTED_EMAIL}" ]]; then
    echo "Commit ${commit} has unexpected committer: ${committer_name} <${committer_email}>"
    failures=1
  fi

  if ! grep -Fqx "${EXPECTED_SIGNOFF}" <<< "${message}"; then
    echo "Commit ${commit} is missing required sign-off: ${EXPECTED_SIGNOFF}"
    failures=1
  fi
done

if [[ "${failures}" -ne 0 ]]; then
  exit 1
fi

echo "Commit identity and sign-off checks passed for ${#commits[@]} commit(s)."

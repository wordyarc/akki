#!/usr/bin/env bash
set -euo pipefail

(( $# == 1 )) || { echo "usage: ${0##*/} <tag>" >&2; exit 2; }

tag=$1
describe=(git describe --tags --abbrev=0 --match 'v*.*.*')
[[ $tag == *-* ]] || describe+=(--exclude 'v*-*')
previous=$("${describe[@]}" "$tag^" 2>/dev/null || true)

repository=${GITHUB_REPOSITORY:-$(git remote get-url origin | sed -E 's#^(git@github\.com:|https://github\.com/)##; s#\.git$##')}
repo=${GITHUB_SERVER_URL:-https://github.com}/$repository

if [[ -n $previous ]]; then
    printf '**Full Changelog**: %s/compare/%s...%s\n' "$repo" "$previous" "$tag"
else
    printf '**Full Changelog**: %s/commits/%s\n' "$repo" "$tag"
fi

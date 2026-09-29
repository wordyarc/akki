#!/usr/bin/env bash
set -euo pipefail

(( $# == 1 )) || { echo "usage: ${0##*/} <tag>" >&2; exit 2; }

tag=$1
describe=(git describe --tags --abbrev=0 --match 'v*.*.*')
[[ $tag == *-* ]] || describe+=(--exclude 'v*-*')
previous=$("${describe[@]}" "$tag^" 2>/dev/null || true)

git log --format='- %s' "${previous:+$previous..}$tag" |
    perl -pe 's/(`+).*?\1|(?<![\w`])@[\w-]+/$1 ? $& : "`$&`"/ge'

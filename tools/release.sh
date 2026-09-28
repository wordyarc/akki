#!/usr/bin/env bash
set -euo pipefail

die() {
    echo "${0##*/}: $*" >&2
    exit 1
}

(( $# == 1 )) || { echo "usage: ${0##*/} <version>" >&2; exit 2; }

version=$1
tag=v$version
semver='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$'

[[ $version =~ $semver ]] || die "$version is not MAJOR.MINOR.PATCH[-PRERELEASE]"
git diff --quiet HEAD || die "uncommitted changes"
git fetch --quiet origin master
git merge-base --is-ancestor HEAD origin/master || die "HEAD is not on origin/master"
[[ $version == *-* ]] || grep -qF "akki\") version \"$version\"" README.md || die "README.md does not mention $version"

git tag -a -m "$tag" "$tag"
git push origin tag "$tag" || { git tag -d "$tag" >/dev/null; exit 1; }

#!/usr/bin/env bash
set -euo pipefail

die() {
    echo "${0##*/}: $*" >&2
    exit 1
}

(( $# == 1 )) || { echo "usage: ${0##*/} <version>" >&2; exit 2; }

version=$1
tag=v$version
snapshot=${version%%-*}-SNAPSHOT
semver='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$'

[[ $version =~ $semver ]] || die "$version is not MAJOR.MINOR.PATCH[-PRERELEASE]"
git diff --quiet HEAD || die "uncommitted changes"
git fetch --quiet origin master
git merge-base --is-ancestor HEAD origin/master || die "HEAD is not on origin/master"
grep -qxF "akki.version=$snapshot" gradle.properties || die "gradle.properties is not at $snapshot"

group=$(sed -n 's/^akki\.maven\.group=//p' gradle.properties)
plugin=$(sed -n 's/^akki\.plugin\.id=//p' gradle.properties)
stale=$(grep -F -e "$group:" -e "\"$plugin\"" README.md | grep -vF "$version\"" || true)
[[ $version == *-* || -z $stale ]] || die "README.md names another version: $stale"

git tag -a -m "$tag" "$tag"
git push origin tag "$tag" || { git tag -d "$tag" >/dev/null; exit 1; }

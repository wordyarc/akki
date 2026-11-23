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
[[ $(git branch --show-current) == master ]] || die "not on master"
git diff --quiet HEAD || die "uncommitted changes"
git fetch --quiet origin master
head=$(git rev-parse HEAD)
[[ $head == "$(git rev-parse origin/master)" ]] || die "HEAD is not origin/master"
git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null && die "tag $tag already exists"
grep -q '^akki\.version=' gradle.properties || die "gradle.properties has no akki.version"

group=$(sed -n 's/^akki\.maven\.group=//p' gradle.properties)
plugin=$(sed -n 's/^akki\.plugin\.id=//p' gradle.properties)
versioned=$(grep -E -e "$group:[^\":]+:" -e "\"$plugin\"" -e '<akki\.version>' README.md || true)
stale=$(grep -vF -e "$version\"" -e ">$version<" <<< "$versioned" || true)
[[ $version == *-* || -z $stale ]] || die "README.md names another version: $stale"

trap 'git tag -d "$tag" >/dev/null 2>&1 || true; git reset --quiet --hard "$head"' ERR

sed -i.bak "s/^akki\.version=.*/akki.version=$version/" gradle.properties
rm gradle.properties.bak
git diff --quiet -- gradle.properties || git commit --quiet -m "Release $version" -- gradle.properties
git tag -a -m "$tag" "$tag"
git push --quiet --atomic origin HEAD:refs/heads/master "refs/tags/$tag"

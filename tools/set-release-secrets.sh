#!/usr/bin/env bash
set -euo pipefail

die() {
    echo "${0##*/}: $*" >&2
    exit 1
}

(( $# <= 1 )) || { echo "usage: ${0##*/} [gpg key id]" >&2; exit 2; }

cd "$(dirname "$0")/.."

properties=${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties
[[ -f $properties ]] || die "$properties does not exist"

property() {
    local value
    value=$(sed -nE "s/^${1//./\\.}[[:space:]]*[=:][[:space:]]*//p" "$properties" | tail -n 1)
    [[ -n $value ]] || die "$1 is not set in $properties"
    printf '%s' "$value"
}

central_username=$(property mavenCentralUsername)
central_password=$(property mavenCentralPassword)
portal_key=$(property gradle.publish.key)
portal_secret=$(property gradle.publish.secret)

if (( $# == 1 )); then
    key=$1
else
    keys=$(gpg --list-secret-keys --with-colons | awk -F: '$1 == "sec" { getline; print $10 }')
    [[ -n $keys ]] || die "no gpg secret key"
    (( $(wc -l <<< "$keys") == 1 )) || die "several gpg secret keys, pass one of them:"$'\n'"$keys"
    key=$keys
fi
gpg --list-secret-keys "$key"
read -rsp "Passphrase of gpg key $key: " passphrase
echo
armored=$(gpg --batch --pinentry-mode loopback --passphrase-fd 3 --armor --export-secret-keys "$key" 3<<< "$passphrase") ||
    die "cannot export gpg key $key with this passphrase"

secret() {
    printf '%s' "$2" | gh secret set "$1"
}

secret SIGNING_KEY "$armored"
secret SIGNING_KEY_PASSWORD "$passphrase"
secret MAVEN_CENTRAL_USERNAME "$central_username"
secret MAVEN_CENTRAL_PASSWORD "$central_password"
secret GRADLE_PUBLISH_KEY "$portal_key"
secret GRADLE_PUBLISH_SECRET "$portal_secret"

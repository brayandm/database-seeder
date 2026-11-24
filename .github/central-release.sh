#!/usr/bin/env bash
set -euo pipefail

: "${CENTRAL_TOKEN_USERNAME:?Configure the Sonatype token username}"
: "${CENTRAL_TOKEN_PASSWORD:?Configure the Sonatype token password}"
authorization=$(printf '%s:%s' "$CENTRAL_TOKEN_USERNAME" "$CENTRAL_TOKEN_PASSWORD" | base64 -w 0)
portal=https://central.sonatype.com/api/v1/publisher
request=(curl --fail --silent --show-error --connect-timeout 15 --max-time 90
  --header "Authorization: Bearer $authorization" --request POST)

artifacts_available() {
  local version=$1 artifact extension
  local repository=https://repo.maven.apache.org/maven2/io/github/brayandm
  for artifact in database-seeder-core database-seeder-cli database-seeder-spring-boot-starter; do
    for extension in pom jar; do
      if ! curl --fail --silent --head --connect-timeout 5 --max-time 15 --output /dev/null \
        "$repository/$artifact/$version/$artifact-$version.$extension"; then
        printf 'Waiting for Maven Central to serve %s:%s (%s).\n' "$artifact" "$version" "$extension" >&2
        return 1
      fi
    done
  done
}

case "$1" in
  upload)
    version=$2
    sha=$3
    mode=USER_MANAGED
    if [[ "$4" == true ]]; then
      mode=AUTOMATIC
    fi
    bundle="build/distributions/database-seeder-$version.zip"
    "${request[@]}" --form "bundle=@$bundle" \
      "$portal/upload?name=database-seeder-$version-$sha&publishingType=$mode"
    ;;
  wait)
    version=$2
    deployment_id=$3
    prefix="database-seeder-$version-"
    deadline=$((SECONDS + 900))
    previous_state=''
    while (( SECONDS < deadline )); do
      response=$("${request[@]}" --retry 3 --retry-max-time 30 "$portal/status?id=$deployment_id")
      name=$(jq -er '.deploymentName' <<< "$response")
      sha=${name#"$prefix"}
      if [[ "$name" != "$prefix$sha" || ! "$sha" =~ ^[a-f0-9]{40}$ ]]; then
        echo "The deployment does not match Database Seeder $version or has no source commit." >&2
        exit 1
      fi

      state=$(jq -er '.deploymentState' <<< "$response")
      if [[ "$state" != "$previous_state" ]]; then
        echo "Maven Central deployment $deployment_id: $state" >&2
        previous_state=$state
      fi
      case "$state" in
        PUBLISHED)
          if artifacts_available "$version"; then
            printf '%s\n' "$sha"
            exit 0
          fi
          ;;
        FAILED)
          printf 'Sonatype validation errors: %s\n' "$(jq -c '.errors // {}' <<< "$response")" >&2
          exit 1
          ;;
        VALIDATED)
          echo "If manual publication was selected, click Publish in Central Portal." >&2
          ;;
        PENDING|VALIDATING|PUBLISHING) ;;
        *)
          echo "Unexpected Sonatype deployment state: $state" >&2
          exit 1
          ;;
      esac
      sleep 15
    done
    echo "Timed out. Resume the release with deployment_id=$deployment_id." >&2
    exit 1
    ;;
  *)
    echo "Expected the upload or wait command." >&2
    exit 2
    ;;
esac

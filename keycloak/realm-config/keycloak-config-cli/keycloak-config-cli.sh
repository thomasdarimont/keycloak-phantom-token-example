#!/usr/bin/env bash

set +x

script_dir=$(cd "$(dirname "$0")"; pwd)

CLI_ENV_FILE="$script_dir/.keycloak-config-cli.env"

set -a
echo "### Step 001: Loading variables for keycloak-config-cli from:"
echo "Location 1: $CLI_ENV_FILE"
source "$CLI_ENV_FILE"
set +a

echo "Using keycloak client $KEYCLOAK_CLIENT_ID"

docker run \
  --net=host \
  -it \
  -e KEYCLOAK_URL="$KEYCLOAK_URL" \
  -e KEYCLOAK_CLIENT_ID="$KEYCLOAK_CLIENT_ID" \
  -e KEYCLOAK_CLIENT_SECRET="$KEYCLOAK_CLIENT_SECRET" \
  -e KEYCLOAK_GRANT_TYPE=client_credentials \
  -e KEYCLOAK_AVAILABILITY_CHECK_ENABLED=true \
  -e KEYCLOAK_AVAILABILITY_CHECK_TIMEOUT=120s \
  -e IMPORT_CACHE_ENABLED="true" \
  -e IMPORT_VAR_SUBSTITUTION_ENABLED="true" \
  -e IMPORT_VALIDATE="true" \
  -e LOGGING_LEVEL_KCC="DEBUG" \
  -e LOGGING_LEVEL_ROOT="INFO" \
  --env-file $script_dir/.keycloak-config-cli.env \
  -v "$script_dir/realms:/config:z" \
  "$KC_CONFIG_CLI_IMAGE"

#!/usr/bin/env bash

shopt -s expand_aliases

script_dir=$(cd "$(dirname "$0")"; pwd)

ENV_FILE="$script_dir/.env"
CLI_ENV_FILE="$script_dir/.keycloak-config-cli.env"

set -a
echo "### Step 001: Loading variables for keycloak-config-cli from:"
echo "Location 1: $ENV_FILE"
echo "Location 2: $CLI_ENV_FILE"
source "$ENV_FILE"
source "$CLI_ENV_FILE"
set +a

mkdir -p "$LOCAL_KCADM_CONFIG_FOLDER"
echo "Using $LOCAL_KCADM_CONFIG_FOLDER to store kcadm.sh configuration"
alias kcadm="docker run --net=host -i --user=1000:1000 --rm -v $LOCAL_KCADM_CONFIG_FOLDER:/opt/keycloak/.keycloak:z --entrypoint /opt/keycloak/bin/kcadm.sh $KEYCLOAK_IMAGE:$KEYCLOAK_VERSION"

echo "### Step 002: Login"
kcadm config credentials \
--config /opt/keycloak/.keycloak/kcadm.config \
--server "$KEYCLOAK_URL"  \
--realm master \
--user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
--password "$KC_BOOTSTRAP_ADMIN_USERNAME"

echo "### Step 003: Create $KEYCLOAK_CLIENT_ID Client"
kcadm create clients \
--config /opt/keycloak/.keycloak/kcadm.config \
-r master \
-s clientId="$KEYCLOAK_CLIENT_ID" \
-s name="Keycloak Config CLI" \
-s description="Keycloak Config CLI Realm Provisioning Client" \
-s enabled=true \
-s clientAuthenticatorType=client-secret \
-s secret="$KEYCLOAK_CLIENT_SECRET" \
-s standardFlowEnabled=false \
-s directAccessGrantsEnabled=false \
-s serviceAccountsEnabled=true

echo "### Step 003: Add realm admin role to $KEYCLOAK_CLIENT_ID Service-Account"
kcadm add-roles \
--config /opt/keycloak/.keycloak/kcadm.config \
-r master \
--uusername "service-account-$KEYCLOAK_CLIENT_ID" \
--rolename admin
echo "### Finished"
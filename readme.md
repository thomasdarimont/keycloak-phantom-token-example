# Phantom Token Pattern with Keycloak and Nginx

Demonstrates the [Phantom Token pattern](https://curity.io/resources/learn/phantom-token-pattern/) using [Keycloak](https://www.keycloak.org/) as the identity provider and Nginx with the [Curity Phantom Token module](https://github.com/curityio/nginx_phantom_token_module).

## Overview

The Phantom Token pattern keeps opaque (reference) access tokens at the client side while backend APIs receive full JWT tokens. The Nginx reverse proxy sits between clients and APIs, intercepting requests and exchanging opaque tokens for JWTs via token introspection (RFC 7662) — transparent to both client and backend.

```
Client (opaque token) → Nginx (introspect + swap) → Backend API (JWT)
```

## Architecture

| Service | Hostname | Port | Description |
|---------|----------|------|-------------|
| Nginx proxy | `id.localhost` / `apps.localhost` / `api.localhost` | 80 | Reverse proxy with phantom token module |
| Keycloak | `id.localhost/auth` | 8080 | Identity provider (OAuth2/OIDC) |
| Echo server | — | 9080 | Spring Boot API that echoes request details |
| PostgreSQL | — | 45432 | Keycloak database |

### Nginx Virtual Hosts

- **`id.localhost`** — proxies to Keycloak for authentication
- **`apps.localhost`** — serves frontend static files
- **`api.localhost`** — API gateway with phantom token translation; introspects access tokens and forwards JWTs to the echo-server backend

### Token Introspection Caching

Introspection responses are cached in Nginx to avoid calling Keycloak on every API request. The cache uses `proxy_cache_lock`, `proxy_cache_use_stale`, and `proxy_cache_background_update` to prevent thundering herd and serve stale responses during background refresh.

## Prerequisites

- Docker and Docker Compose
- Java 21 and Maven (for local development of the echo-server or Keycloak extensions)
- Entries in `/etc/hosts` (or equivalent):
  ```
  127.0.0.1  id.localhost apps.localhost api.localhost
  ```

## Getting Started

```bash
# Start all services
docker compose up --build

# Keycloak admin console
open http://id.localhost/auth/admin
# Credentials: admin / admin
```

## Testing the API

The `requests/demo.http` file contains sample requests (for IntelliJ HTTP Client or similar tools).

Example flow:

1. Obtain an access token from Keycloak (realm `acme`, client `acme-testapp`)
2. Call the echo endpoint with the token:
   ```bash
   curl -H "Authorization: Bearer <access_token>" http://api.localhost/api/echo
   ```
3. Nginx introspects the opaque token, swaps it for a JWT, and forwards it to the echo-server
4. The echo-server returns the request headers (including the JWT) and decoded payload

## Project Structure

```
├── compose.yml                  # Docker Compose orchestration
├── nginx/
│   ├── Dockerfile               # Nginx + phantom token module
│   ├── config/
│   │   ├── nginx.conf           # Main nginx config (loads phantom token module)
│   │   └── default.conf         # Virtual hosts and introspection cache config
│   ├── modules/                 # Pre-built phantom token .so modules
│   └── wwwroot/                 # Static frontend files
├── apps/
│   └── echo-server/             # Spring Boot echo API (Java 21)
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/
├── keycloak/
│   ├── extensions/              # Custom Keycloak SPI (introspection provider)
│   └── realm-config/            # Realm import configuration
└── requests/
    └── demo.http                # Sample HTTP requests
```

## Phantom Token Nginx Module

The [Curity Phantom Token module](https://github.com/curityio/nginx_phantom_token_module) is a dynamic Nginx module that must match your exact Nginx version. This project includes pre-built binaries for Alpine in `nginx/modules/`.

### Using a pre-built binary

Check the [releases page](https://github.com/curityio/nginx_phantom_token_module/releases) for a binary matching your Nginx version and OS. Place it in `nginx/modules/` and update the `COPY` path in `nginx/Dockerfile`:

```dockerfile
FROM nginx:1.29.5-alpine
COPY ./modules/alpine.ngx_curity_http_phantom_token_module_1.29.5.so /usr/lib/nginx/modules/ngx_curity_http_phantom_token_module.so
```

### Building from source

If no pre-built binary is available for your Nginx version, build one yourself:

```bash
git clone https://github.com/curityio/nginx_phantom_token_module
cd nginx_phantom_token_module

export NGINX_VERSION='1.29.5'
export LINUX_DISTRO='alpine'
./build.sh
```

Copy the resulting `.so` file to `nginx/modules/` and update the Dockerfile accordingly. See the [build documentation](https://github.com/curityio/nginx_phantom_token_module/wiki/3.-Builds) for supported distros and options.

## Configuration

Nginx introspection credentials and endpoint are configured via environment variables in `compose.yml`:

| Variable | Description |
|----------|-------------|
| `CUSTOM_INTROSPECTION_AUTH_HEADER` | Base64-encoded `client_id:client_secret` for introspection |
| `CUSTOM_INTROSPECTION_ENDPOINT` | Keycloak token introspection URL |

These are substituted into the Nginx config template at container startup via `envsubst`.

package com.thomasdarimont.training.keycloak.oidc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.jbosslog.JBossLog;
import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.crypto.SignatureProvider;
import org.keycloak.crypto.SignatureVerifierContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.Constants;
import org.keycloak.models.ImpersonationSessionNote;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.AccessTokenIntrospectionProvider;
import org.keycloak.protocol.oidc.AccessTokenIntrospectionProviderFactory;
import org.keycloak.protocol.oidc.TokenIntrospectionProvider;
import org.keycloak.protocol.oidc.TokenIntrospectionProviderFactory;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.Urls;
import org.keycloak.services.util.DefaultClientSessionContext;
import org.keycloak.services.util.UserSessionUtil;
import org.keycloak.tracing.TracingAttributes;
import org.keycloak.tracing.TracingProvider;
import org.keycloak.util.JsonSerialization;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Custom Access Token Introspection to support Accept: application/jwt
 */
public class AcmeAccessTokenIntrospectionProvider<T extends AccessToken> extends AccessTokenIntrospectionProvider<T> {

    private static final Logger logger = Logger.getLogger(AcmeAccessTokenIntrospectionProvider.class);

    public AcmeAccessTokenIntrospectionProvider(KeycloakSession session) {
        super(session);
    }

    @Override
    public Response introspect(String tokenStr, EventBuilder eventBuilder) {
        this.eventBuilder = eventBuilder;
        AccessToken accessToken = null;
        try {
            ClientModel authenticatedClient = session.getContext().getClient();

            ObjectNode tokenMetadata;
            if (introspectionChecks(tokenStr)) {
                accessToken = transformAccessToken(this.token, userSession);

                tokenMetadata = JsonSerialization.createObjectNode(accessToken);
                tokenMetadata.put("client_id", accessToken.getIssuedFor());

                String scope = accessToken.getScope();
                if (scope != null && scope.trim().isEmpty()) {
                    tokenMetadata.remove("scope");
                }

                if (!tokenMetadata.has("username")) {
                    if (accessToken.getPreferredUsername() != null) {
                        tokenMetadata.put("username", accessToken.getPreferredUsername());
                    } else {
                        UserModel userModel = userSession.getUser();
                        if (userModel != null) {
                            tokenMetadata.put("username", userModel.getUsername());
                            eventBuilder.user(userModel);
                        }
                    }
                }

                String actor = userSession.getNote(ImpersonationSessionNote.IMPERSONATOR_USERNAME.toString());
                if (actor != null) {
                    // for token exchange delegation semantics when an entity (actor) other than the subject is the acting party to whom authority has been delegated
                    tokenMetadata.putObject("act").put("sub", actor);
                }

                tokenMetadata.put(OAuth2Constants.TOKEN_TYPE, accessToken.getType());
                tokenMetadata.put("active", true);
                eventBuilder.success();
            } else {
                tokenMetadata = JsonSerialization.createObjectNode();
                logger.debug("Keycloak token introspection return false");
                tokenMetadata.put("active", false);
            }

            // ACME:PATCH
            boolean isJwtRequest = org.keycloak.utils.MediaType.APPLICATION_JWT.equals(session.getContext().getRequestHeaders().getHeaderString(HttpHeaders.ACCEPT));

            // if the consumer requests application/jwt return a JWT representation of the introspection contents in a jwt claim
            if (isJwtRequest) {

                if (accessToken == null) {
                    logger.debugf("### Return invalid JWT token introspection response");
                    return Response.status(Response.Status.NO_CONTENT).type(org.keycloak.utils.MediaType.APPLICATION_JWT).build();
                }

                String jwt = session.tokens().encode(accessToken);
                if (!Boolean.parseBoolean(authenticatedClient.getAttribute(Constants.SUPPORT_JWT_CLAIM_IN_INTROSPECTION_RESPONSE_ENABLED))) {
                    logger.debugf("### Return valid JWT token introspection response");
                    return Response.ok(jwt).type(org.keycloak.utils.MediaType.APPLICATION_JWT).build();
                }

                tokenMetadata.put("jwt", jwt);
            }

            return Response.ok(JsonSerialization.writeValueAsBytes(tokenMetadata)).type(MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            String clientId = accessToken != null ? accessToken.getIssuedFor() : "unknown";
            logger.debugf(e, "Exception during Keycloak introspection for %s client in realm %s", clientId, realm.getName());
            eventBuilder.detail(Details.REASON, e.getMessage());
            eventBuilder.error(Errors.TOKEN_INTROSPECTION_FAILED);
            throw new RuntimeException("Error creating token introspection response.", e);
        }
    }

    @JBossLog
    @AutoService(TokenIntrospectionProviderFactory.class)
    public static class Factory extends AccessTokenIntrospectionProviderFactory {

        static {
            log.debugf("### Using custom Token Introspection");
        }

        @Override
        public TokenIntrospectionProvider create(KeycloakSession session) {
            return new AcmeAccessTokenIntrospectionProvider(session);
        }
    }
}

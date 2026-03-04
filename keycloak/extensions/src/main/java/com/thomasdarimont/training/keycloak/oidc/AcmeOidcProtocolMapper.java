package com.thomasdarimont.training.keycloak.oidc;

import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserPropertyMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.representations.IDToken;

import java.util.List;

@JBossLog
@AutoService(ProtocolMapper.class)
public class AcmeOidcProtocolMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static final String CUSTOM_VALUE_PROPERTY = "customValue";

    static {
        CONFIG_PROPERTIES = ProviderConfigurationBuilder.create() //
                .property() //
                .name(CUSTOM_VALUE_PROPERTY) //
                .type(ProviderConfigProperty.STRING_TYPE) //
                .label("Custom Value") //
                .helpText("Custom Value help") //
                .defaultValue("default") //
                .add() //
                .build();

        // adds add to access token / id token / userinfo and type buttons
        OIDCAttributeMapperHelper.addAttributeConfig(CONFIG_PROPERTIES, UserPropertyMapper.class);
    }

    @Override
    public String getId() {
        return "acme-oidc-mapper";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Acme OIDC Token Mapper";
    }

    @Override
    public String getHelpText() {
        return "Acme OIDC Token Mapper Help";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession, KeycloakSession keycloakSession, ClientSessionContext clientSessionCtx) {

        String customValue = mappingModel.getConfig().getOrDefault(CUSTOM_VALUE_PROPERTY, "fallback");
        log.infof("customValue=%s", customValue);
        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, customValue);
    }
}

package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ExpressionJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
import static org.springframework.security.oauth2.core.authorization.OAuth2AuthorizationManagers.hasScope;

@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {

        http.authorizeHttpRequests((authorize) -> {
            authorize.requestMatchers("/api/1/**").access(allOf(hasRole("acme-api-user"), hasScope("acme.api.1")));
            authorize.requestMatchers("/api/2/**").access(allOf(hasRole("acme-api-user"), hasScope("acme.api.2")));
            authorize.anyRequest().authenticated();
        });

        http.oauth2ResourceServer(oauth2 -> {
            oauth2.jwt(Customizer.withDefaults());
            oauth2.jwt(jwt -> {
                var jwtConverter = new JwtAuthenticationConverter();
                jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter());
                jwtConverter.setPrincipalClaimName("preferred_username");
                jwt.jwtAuthenticationConverter(jwtConverter);
            });
        });

        return http.build();
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter() {
        SpelExpression realmRolesExpression = new SpelExpressionParser().parseRaw("[\"realm_access\"][\"roles\"]");
        var realmRolesConverter = new ExpressionJwtGrantedAuthoritiesConverter(realmRolesExpression);
        realmRolesConverter.setAuthorityPrefix("ROLE_");

        return new DelegatingJwtGrantedAuthoritiesConverter(realmRolesConverter, new JwtGrantedAuthoritiesConverter());
    }

}
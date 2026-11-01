/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake;
import io.fireflysoftware.experience.lending.core.intake.DomainDraftIntakePort;
import io.fireflysoftware.experience.lending.core.intake.impl.TokenExchangedDomainDraftIntakePortImpl;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.intake.enabled",havingValue="true")
public class DraftApplicationConfiguration {
    @Bean public ApplicationIntakeExchangeSettings lendingIntakeExchangeSettings(Environment env){
        String p="lending.intake.";boolean sandbox=Boolean.parseBoolean(env.getProperty(p+"owned-loopback-sandbox","false"));
        return new ApplicationIntakeExchangeSettings(env.getRequiredProperty(p+"issuer"),env.getRequiredProperty(p+"incoming-audience"),
            env.getRequiredProperty(p+"incoming-client-id"),env.getRequiredProperty(p+"platform-tenant"),env.getRequiredProperty(p+"domain-audience"),
            env.getRequiredProperty(p+"domain-client-id"),URI.create(env.getRequiredProperty(p+"domain-endpoint")),sandbox);
    }
    @Bean public DomainDraftIntakePort domainDraftIntakePort(Environment env,ApplicationIntakeExchangeSettings settings){
        String p="lending.intake.";boolean sandbox=settings.ownedLoopbackSandbox();
        String token=ApplicationIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(p+"token-endpoint")),sandbox).toASCIIString();
        String jwks=ApplicationIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(p+"target-jwk-set-uri")),sandbox).toASCIIString();
        var registration=ClientRegistration.withRegistrationId("lendingDomainIntake").clientId(settings.domainClientId())
            .clientSecret(env.getRequiredProperty(p+"client-secret")).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token)
            .scope("lending:applications:draft:create").build();
        var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
        return new TokenExchangedDomainDraftIntakePortImpl(settings,registration,decoder,Clock.systemUTC());
    }
}

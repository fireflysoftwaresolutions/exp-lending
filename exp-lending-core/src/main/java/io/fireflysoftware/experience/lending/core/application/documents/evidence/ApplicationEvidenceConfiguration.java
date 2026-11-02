/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.impl.TokenExchangedDomainApplicationEvidencePortImpl;
import io.fireflysoftware.experience.lending.core.intake.ApplicationIntakeExchangeSettings;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.evidence.enabled",havingValue="true")
public class ApplicationEvidenceConfiguration {
    @Bean public ApplicationEvidenceSettings applicationEvidenceSettings(Environment env){String p="lending.evidence.";return new ApplicationEvidenceSettings(env.getRequiredProperty(p+"issuer"),env.getRequiredProperty(p+"incoming-audience"),env.getRequiredProperty(p+"incoming-client-id"),env.getRequiredProperty(p+"platform-tenant"),env.getRequiredProperty(p+"domain-audience"),env.getRequiredProperty(p+"domain-client-id"),URI.create(env.getRequiredProperty(p+"domain-endpoint")),Boolean.parseBoolean(env.getProperty(p+"owned-loopback-sandbox","false")));}
    @Bean public ApplicationEvidencePort applicationEvidencePort(Environment env,ApplicationEvidenceSettings settings){String p="lending.evidence.";String token=ApplicationIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(p+"token-endpoint")),settings.ownedLoopbackSandbox()).toASCIIString();String jwks=ApplicationIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(p+"target-jwk-set-uri")),settings.ownedLoopbackSandbox()).toASCIIString();
        var registration=ClientRegistration.withRegistrationId("lendingDomainEvidence").clientId(settings.domainClientId()).clientSecret(env.getRequiredProperty(p+"client-secret")).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token).scope(TokenExchangedDomainApplicationEvidencePortImpl.ACTION).build();var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));return new TokenExchangedDomainApplicationEvidencePortImpl(settings,registration,decoder,Clock.systemUTC(),System::nanoTime);
    }
}

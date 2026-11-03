/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import io.fireflysoftware.experience.lending.core.contract.impl.TokenExchangedDomainInitialContractPortImpl;
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
@ConditionalOnProperty(name="lending.initial-primary-contract.enabled",havingValue="true")
public class ApplicationInitialContractConfiguration {
    private static final String P="lending.initial-primary-contract.";
    @Bean public ApplicationInitialContractSettings applicationInitialContractSettings(Environment e){return new ApplicationInitialContractSettings(e.getRequiredProperty(P+"issuer"),e.getRequiredProperty(P+"incoming-audience"),e.getRequiredProperty(P+"incoming-client-id"),e.getRequiredProperty(P+"platform-tenant"),e.getRequiredProperty(P+"domain-audience"),e.getRequiredProperty(P+"domain-client-id"),URI.create(e.getRequiredProperty(P+"domain-endpoint")),Boolean.parseBoolean(e.getProperty(P+"owned-loopback-sandbox","false")));}
    @Bean public ApplicationInitialContractPort applicationInitialContractPort(Environment e,ApplicationInitialContractSettings s){String token=ApplicationIntakeExchangeSettings.endpoint(URI.create(e.getRequiredProperty(P+"token-endpoint")),s.ownedLoopbackSandbox()).toASCIIString();String jwks=ApplicationIntakeExchangeSettings.endpoint(URI.create(e.getRequiredProperty(P+"target-jwk-set-uri")),s.ownedLoopbackSandbox()).toASCIIString();var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(s.issuer()));
        return new TokenExchangedDomainInitialContractPortImpl(s,registration(e,s,token,"initialContractLink",TokenExchangedDomainInitialContractPortImpl.LINK),registration(e,s,token,"initialContractRead",TokenExchangedDomainInitialContractPortImpl.READ),decoder,Clock.systemUTC(),System::nanoTime);}
    private static ClientRegistration registration(Environment e,ApplicationInitialContractSettings s,String token,String id,String scope){return ClientRegistration.withRegistrationId(id).clientId(s.domainClientId()).clientSecret(e.getRequiredProperty(P+"client-secret")).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token).scope(scope).build();}
}

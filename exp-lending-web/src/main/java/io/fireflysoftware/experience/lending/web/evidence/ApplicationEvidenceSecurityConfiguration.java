/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.evidence;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.ApplicationEvidenceSettings;
import io.fireflysoftware.experience.lending.web.intake.impl.VerifiedLendingJwtWebFilterImpl;
import io.fireflysoftware.experience.lending.web.evidence.impl.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.evidence.enabled",havingValue="true")
public class ApplicationEvidenceSecurityConfiguration {
    @Bean @Order(-49) public WebFilter applicationEvidenceVerifiedSubject(ReactiveJwtDecoder decoder,@Qualifier("fireflyJwtAuthenticationConverter")Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,ApplicationEvidenceSettings settings){return new ApplicationEvidenceVerifiedSubjectFilterImpl(new VerifiedLendingJwtWebFilterImpl(decoder,projection,settings.issuer(),settings.platformTenant(),"/api/v1/experience/lending/applications/*/documents"));}
    @Bean @Order(-48) public WebFilter applicationEvidenceEnvelope(){return new ApplicationEvidenceEnvelopeWebFilterImpl();}
}

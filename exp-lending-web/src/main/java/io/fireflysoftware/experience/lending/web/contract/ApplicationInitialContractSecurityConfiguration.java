/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.contract;
import io.fireflysoftware.experience.lending.core.contract.ApplicationInitialContractSettings;
import io.fireflysoftware.experience.lending.web.intake.impl.VerifiedLendingJwtWebFilterImpl;
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
@ConditionalOnProperty(name="lending.initial-primary-contract.enabled",havingValue="true")
public class ApplicationInitialContractSecurityConfiguration {
    @Bean @Order(-47) public WebFilter applicationInitialContractVerifiedSubject(ReactiveJwtDecoder decoder,@Qualifier("fireflyJwtAuthenticationConverter")Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,ApplicationInitialContractSettings settings){
        var delegate=new VerifiedLendingJwtWebFilterImpl(decoder,projection,settings.issuer(),settings.platformTenant(),"/api/v1/experience/lending/applications");
        return (exchange,chain)->{String path=exchange.getRequest().getPath().pathWithinApplication().value();return path.matches("/api/v1/experience/lending/applications/[^/]+/initial-primary-contracts/actions/[^/]+")?delegate.filter(exchange,chain):chain.filter(exchange);};
    }
}

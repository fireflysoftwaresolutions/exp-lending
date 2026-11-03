/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;

import io.fireflysoftware.experience.lending.core.intake.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Controlled-port orchestration unit contracts; no Domain transport, owner write or complete journey is exercised. */
class DraftApplicationOrchestrationContractTest {
    final DraftApplicationRequest request=new DraftApplicationRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("1234.50"),24,"EUR","vehicle");
    final UUID application=UUID.randomUUID();final Instant created=Instant.parse("2026-11-01T10:00:00.123456Z");
    DraftApplicationReceipt receipt(boolean replay,String digest){return new DraftApplicationReceipt(request.requestId(),application,request.simulationId(),request.productId(),request.partyId(),
        request.requestedAmount(),request.term(),request.currency(),request.purpose(),"DRAFT","PENDING_DOCUMENTS",created,replay,digest);}
    DraftApplicationServiceImpl service(DomainDraftIntakePort... ports){var factory=new DefaultListableBeanFactory();
        for(int i=0;i<ports.length;i++)factory.registerSingleton("domainIntake"+i,ports[i]);return new DraftApplicationServiceImpl(factory.getBeanProvider(DomainDraftIntakePort.class));}
    @Test void forwardsTheOriginalActionIdentityAndEveryTermLazilyToOnlyTheDomainPort(){
        var received=new AtomicReference<DraftApplicationRequest>();var calls=new AtomicInteger();var expected=receipt(false,"a".repeat(64));
        var operation=service(value->{calls.incrementAndGet();received.set(value);return Mono.just(expected);}).create(request);
        assertThat(calls).hasValue(0);StepVerifier.create(operation).expectNext(expected).verifyComplete();
        assertThat(calls).hasValue(1);assertThat(received.get()).isSameAs(request);
    }
    @Test void retriesKeepTheOriginalRequestAndPreserveOpaqueOwnerApplicationAndDigest(){
        var received=new ArrayList<DraftApplicationRequest>();var digest="f".repeat(64);var calls=new AtomicInteger();
        var service=service(value->{received.add(value);return Mono.just(receipt(calls.getAndIncrement()>0,digest));});
        StepVerifier.create(service.create(request)).expectNext(receipt(false,digest)).verifyComplete();
        StepVerifier.create(service.create(request)).expectNext(receipt(true,digest)).verifyComplete();
        assertThat(received).hasSize(2).allSatisfy(value->assertThat(value).isSameAs(request));
    }
    @Test void changedOwnerIdentityOrAnyRequestedTermCannotBecomeAnExperienceReceipt(){
        var owner=receipt(false,"a".repeat(64));
        for(var changed:List.of(
            new DraftApplicationReceipt(UUID.randomUUID(),application,owner.simulationId(),owner.productId(),owner.partyId(),owner.requestedAmount(),24,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,UUID.randomUUID(),owner.productId(),owner.partyId(),owner.requestedAmount(),24,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),UUID.randomUUID(),owner.partyId(),owner.requestedAmount(),24,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),owner.productId(),UUID.randomUUID(),owner.requestedAmount(),24,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),owner.productId(),owner.partyId(),new BigDecimal("1234.51"),24,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),owner.productId(),owner.partyId(),owner.requestedAmount(),25,"EUR","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),owner.productId(),owner.partyId(),owner.requestedAmount(),24,"USD","vehicle","DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),owner.productId(),owner.partyId(),owner.requestedAmount(),24,"EUR",null,"DRAFT","PENDING_DOCUMENTS",created,false,owner.intentDigest()),
            new DraftApplicationReceipt(owner.requestId(),application,owner.simulationId(),owner.productId(),owner.partyId(),owner.requestedAmount(),24,"EUR","vehicle","APPROVED","PENDING_DOCUMENTS",created,false,owner.intentDigest())))
            StepVerifier.create(service(value->Mono.just(changed)).create(request)).expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)).verify();
    }
    @Test void missingOrMalformedOwnerDigestCannotBecomeAnExperienceReceipt(){
        for(String digest:Arrays.asList(null,"invalid","A".repeat(64),"a".repeat(63)))
            StepVerifier.create(service(value->Mono.just(receipt(false,digest))).create(request)).expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)).verify();
    }
    @Test void ownerConflictPropagatesOnceWithoutAnAutomaticRetryOrReplacementAction(){
        var calls=new AtomicInteger();var conflict=new ResponseStatusException(HttpStatus.CONFLICT,"Owner action conflict");
        StepVerifier.create(service(value->{calls.incrementAndGet();assertThat(value).isSameAs(request);return Mono.error(conflict);}).create(request)).expectErrorMatches(error->error==conflict).verify();
        assertThat(calls).hasValue(1);
    }
    @Test void missingOrAmbiguousOwnerAndEmptyOwnerResponseFailClosed(){
        DomainDraftIntakePort empty=value->Mono.empty();
        for(var unavailable:List.of(service(),service(empty,value->Mono.empty())))
            StepVerifier.create(unavailable.create(request)).expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)).verify();
        StepVerifier.create(service(empty).create(request)).expectErrorSatisfies(error->assertThat(((ResponseStatusException)error).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)).verify();
    }
}

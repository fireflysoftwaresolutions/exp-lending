/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.contract;
import io.fireflysoftware.experience.lending.core.contract.*;
import io.fireflysoftware.experience.lending.web.controllers.ApplicationInitialContractController;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ApplicationInitialContractControllerTest {
    @SuppressWarnings("unchecked") private static ObjectProvider<ApplicationInitialContractPort> provider(ApplicationInitialContractPort port){var p=(ObjectProvider<ApplicationInitialContractPort>)mock(ObjectProvider.class);when(p.getIfUnique()).thenReturn(port);return p;}
    private static ApplicationInitialContractReceipt receipt(UUID application,UUID action,boolean replay){return new ApplicationInitialContractReceipt(action,application,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"EUR","INITIAL_PRIMARY_LOAN_CONTRACT","DRAFT",true,Instant.parse("2026-11-02T00:00:00Z"),"a".repeat(64),replay);}
    @Test void commandUses201Then200AndReadonlyAbsent204WithoutCachedIdempotency()throws Exception {
        UUID app=UUID.randomUUID(),action=UUID.randomUUID();var writes=new AtomicInteger();var reads=new AtomicInteger();var owner=new ApplicationInitialContractPort(){public Mono<ApplicationInitialContractReceipt> link(UUID application,ApplicationInitialContractRequest request){assertThat(application).isEqualTo(app);assertThat(request.actionId()).isEqualTo(action);return Mono.just(receipt(app,action,writes.getAndIncrement()>0));}public Mono<Optional<ApplicationInitialContractReceipt>> observe(UUID application,UUID originalAction){reads.incrementAndGet();return Mono.just(Optional.empty());}};var controller=new ApplicationInitialContractController(provider(owner));var request=new ApplicationInitialContractRequest(action,null);
        assertThat(controller.link(app,action,request).block(Duration.ofSeconds(1)).getStatusCode().value()).isEqualTo(201);assertThat(controller.link(app,action,request).block(Duration.ofSeconds(1)).getStatusCode().value()).isEqualTo(200);var absent=controller.observe(app,action).block(Duration.ofSeconds(1));assertThat(absent.getStatusCode().value()).isEqualTo(204);assertThat(absent.getBody()).isNull();assertThat(writes.get()).isEqualTo(2);assertThat(reads.get()).isEqualTo(1);assertThat(ApplicationInitialContractController.class.getMethod("link",UUID.class,UUID.class,ApplicationInitialContractRequest.class).isAnnotationPresent(DisableIdempotency.class)).isTrue();
    }
    @Test void providerConstructionFailureHasTypedNoDispatchWriteAndReadonlyOutcomes(){
        UUID app=UUID.randomUUID(),action=UUID.randomUUID();var p=provider(null);when(p.getIfUnique()).thenThrow(new IllegalStateException("Controlled provider unavailable"));var controller=new ApplicationInitialContractController(p);
        assertThatThrownBy(()->controller.link(app,action,new ApplicationInitialContractRequest(action,null)).block(Duration.ofSeconds(1))).isInstanceOfSatisfying(ApplicationInitialContractFailure.class,error->assertThat(error.outcome()).isEqualTo(ApplicationInitialContractError.Outcome.REFUSED));assertThatThrownBy(()->controller.observe(app,action).block(Duration.ofSeconds(1))).isInstanceOfSatisfying(ApplicationInitialContractFailure.class,error->assertThat(error.outcome()).isEqualTo(ApplicationInitialContractError.Outcome.OBSERVATION_UNAVAILABLE));
    }
    @Test void lateGenericAuthorityRefusalCannotEraseAnAttemptButProvenPreworkAndKnownCommitRemainDistinct(){
        UUID app=UUID.randomUUID(),action=UUID.randomUUID();var error=new AtomicReference<RuntimeException>(new ResponseStatusException(HttpStatus.FORBIDDEN));var owner=new ApplicationInitialContractPort(){public Mono<ApplicationInitialContractReceipt> link(UUID application,ApplicationInitialContractRequest request){return Mono.error(error.get());}public Mono<Optional<ApplicationInitialContractReceipt>> observe(UUID application,UUID originalAction){return Mono.error(error.get());}};var controller=new ApplicationInitialContractController(provider(owner));var request=new ApplicationInitialContractRequest(action,null);
        assertThatThrownBy(()->controller.link(app,action,request).block(Duration.ofSeconds(1))).isInstanceOfSatisfying(ApplicationInitialContractFailure.class,value->assertThat(value.outcome()).isEqualTo(ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN));error.set(new ApplicationInitialContractPreworkRefusal(HttpStatus.FORBIDDEN));assertThatThrownBy(()->controller.link(app,action,request).block(Duration.ofSeconds(1))).isInstanceOf(ApplicationInitialContractPreworkRefusal.class);error.set(new ApplicationInitialContractFailure(action,ApplicationInitialContractError.Outcome.COMMITTED_RESPONSE_UNAVAILABLE));assertThatThrownBy(()->controller.link(app,action,request).block(Duration.ofSeconds(1))).isSameAs(error.get());
    }
}

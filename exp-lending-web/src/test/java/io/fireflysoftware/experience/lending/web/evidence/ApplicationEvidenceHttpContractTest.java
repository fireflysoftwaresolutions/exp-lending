/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.evidence;
import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.validation.impl.BoundedApplicationEvidenceContentValidatorImpl;
import io.fireflysoftware.experience.lending.web.controllers.ApplicationDocumentsController;
import io.fireflysoftware.experience.lending.web.evidence.impl.ApplicationEvidenceEnvelopeWebFilterImpl;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Input, actual JSON buffer and metadata contracts only; no business Main is replaced. */
class ApplicationEvidenceHttpContractTest {
    @Test void exactDecodedBoundaryPassesActualBase64EnvelopeAndOversizeCannotReachController()throws Exception{
        byte[] bytes=new byte[2097152];Arrays.fill(bytes,(byte)41);var request=new UploadDocumentCommand(UUID.randomUUID(),"a".repeat(200),"PAYSLIP",bytes,UUID.randomUUID());var mapper=new ObjectMapper();byte[] body=mapper.writeValueAsBytes(request);assertThat(body.length).isGreaterThan(2097152).isLessThan(3145728);var observed=new AtomicReference<byte[]>();var filter=new ApplicationEvidenceEnvelopeWebFilterImpl();
        StepVerifier.create(filter.filter(exchange(body),current->DataBufferUtils.join(current.getRequest().getBody()).doOnNext(buffer->{try{byte[] captured=new byte[buffer.readableByteCount()];buffer.read(captured);observed.set(captured);}finally{DataBufferUtils.release(buffer);}}).then())).verifyComplete();assertThat(mapper.readValue(observed.get(),UploadDocumentCommand.class).getContent()).isEqualTo(bytes);
        var called=new AtomicBoolean();StepVerifier.create(filter.filter(exchange(new byte[3145729]),current->{called.set(true);return Mono.empty();})).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ResponseStatusException.class);assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(413);}).verify();assertThat(called).isFalse();
    }
    @Test void decodedContentValidatorSeparatesByteLimitsFromPublishedBase64Length(){var validator=new BoundedApplicationEvidenceContentValidatorImpl();assertThat(validator.isValid(null,null)).isFalse();assertThat(validator.isValid(new byte[0],null)).isFalse();assertThat(validator.isValid(new byte[]{1},null)).isTrue();assertThat(validator.isValid(new byte[2097152],null)).isTrue();assertThat(validator.isValid(new byte[2097153],null)).isFalse();}
    @Test void existingUploadAndRecoveryRequireFreshAuthorityAndTypedProgressMetadata()throws Exception{
        for(String name:List.of("uploadDocument","recoverDocument")){var method=Arrays.stream(ApplicationDocumentsController.class.getDeclaredMethods()).filter(value->value.getName().equals(name)).findFirst().orElseThrow();assertThat(method.getAnnotation(PostMapping.class)).isNotNull();assertThat(method.getAnnotation(DisableIdempotency.class)).isNotNull();var responses=method.getAnnotation(ApiResponses.class);assertThat(Arrays.stream(responses.value()).map(value->value.responseCode()).toList()).contains("200","201","409","503");for(var response:responses.value())if(Set.of("409","503").contains(response.responseCode()))assertThat(response.content()[0].schema().implementation()).isEqualTo(ApplicationEvidenceError.class);}
    }
    private static MockServerWebExchange exchange(byte[] body){return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/experience/lending/applications/"+UUID.randomUUID()+"/documents").contentType(MediaType.APPLICATION_JSON).body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body))));}
}

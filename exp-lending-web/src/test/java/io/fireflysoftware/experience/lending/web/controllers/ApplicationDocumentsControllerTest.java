/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.experience.lending.web.controllers;

import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO;
import io.fireflysoftware.experience.lending.core.application.documents.services.ApplicationDocumentsService;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ApplicationDocumentsController.class,
        excludeAutoConfiguration = {
                ReactiveWebSecurityAutoConfiguration.class,
                ReactiveUserDetailsServiceAutoConfiguration.class
        })
class ApplicationDocumentsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ApplicationDocumentsService applicationDocumentsService;

    // fireflyframework-web's GlobalExceptionHandler required beans
    @MockitoBean
    private ExceptionConverterService exceptionConverterService;
    @MockitoBean
    private ErrorHandlingProperties errorHandlingProperties;
    @MockitoBean
    private ErrorResponseNegotiator errorResponseNegotiator;

    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID STORED_DOCUMENT_ID = UUID.randomUUID(), ACTION_ID=UUID.randomUUID();
    private static final String BASE_PATH = "/api/v1/experience/lending/applications/{id}/documents";

    @Test
    void listDocuments_returns200WithDocumentList() {
        var doc = ApplicationDocumentDTO.builder()
                .documentId(DOCUMENT_ID)
                .applicationId(APPLICATION_ID)
                .fileName("contract.pdf")
                .documentType("application/pdf")
                .uploadedAt(LocalDateTime.now())
                .size(2048L)
                .build();

        when(applicationDocumentsService.listDocuments(eq(APPLICATION_ID))).thenReturn(Flux.just(doc));

        webTestClient.get()
                .uri(BASE_PATH, APPLICATION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ApplicationDocumentDTO.class)
                .hasSize(1)
                .value(list -> {
                    assertThat(list.get(0).getDocumentId()).isEqualTo(DOCUMENT_ID);
                    assertThat(list.get(0).getFileName()).isEqualTo("contract.pdf");
                });
    }

    @Test
    void listDocuments_returns200WithEmptyList() {
        when(applicationDocumentsService.listDocuments(eq(APPLICATION_ID))).thenReturn(Flux.empty());

        webTestClient.get()
                .uri(BASE_PATH, APPLICATION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ApplicationDocumentDTO.class)
                .hasSize(0);
    }

    @Test
    void uploadDocument_returns201WithBody() {
        var doc = ApplicationDocumentDTO.builder()
                .documentId(DOCUMENT_ID)
                .applicationId(APPLICATION_ID)
                .fileName("payslip.pdf")
                .documentType("application/pdf")
                .uploadedAt(LocalDateTime.now())
                .size(512L).actionId(ACTION_ID).associationId(DOCUMENT_ID).storedDocumentId(STORED_DOCUMENT_ID).partyId(UUID.randomUUID()).storedAt(Instant.now()).attachedAt(Instant.now()).contentDigest("a".repeat(64)).documentIntentDigest("b".repeat(64)).associationIntentDigest("c".repeat(64)).associationState("ATTACHED").verificationStatus("PENDING").replayed(false)
                .build();

        when(applicationDocumentsService.uploadDocument(eq(APPLICATION_ID), any(UploadDocumentCommand.class)))
                .thenReturn(Mono.just(doc));

        webTestClient.post()
                .uri(BASE_PATH, APPLICATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("actionId",ACTION_ID,"fileName","payslip.pdf","documentType","PAYSLIP","content",Base64.getEncoder().encodeToString(new byte[512])))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ApplicationDocumentDTO.class)
                .value(body -> {
                    assertThat(body.getDocumentId()).isEqualTo(DOCUMENT_ID);
                    assertThat(body.getFileName()).isEqualTo("payslip.pdf");
                    assertThat(body.getSize()).isEqualTo(512L);
                    assertThat(body.getStoredDocumentId()).isEqualTo(STORED_DOCUMENT_ID).isNotEqualTo(body.getDocumentId());
                    assertThat(body.getAssociationId()).isEqualTo(DOCUMENT_ID);
                    assertThat(body.getActionId()).isEqualTo(ACTION_ID);
                });
    }

    @Test
    void uploadDocument_returns500WhenServiceFails() {
        when(applicationDocumentsService.uploadDocument(eq(APPLICATION_ID), any()))
                .thenReturn(Mono.error(new RuntimeException("upload failed")));

        webTestClient.post()
                .uri(BASE_PATH, APPLICATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("actionId",ACTION_ID,"fileName","fail.pdf","documentType","PAYSLIP","content","AQID"))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void deleteDocument_returns204() {
        when(applicationDocumentsService.deleteDocument(eq(APPLICATION_ID), eq(DOCUMENT_ID)))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri(BASE_PATH + "/{docId}", APPLICATION_ID, DOCUMENT_ID)
                .exchange()
                .expectStatus().isNoContent();
    }
}

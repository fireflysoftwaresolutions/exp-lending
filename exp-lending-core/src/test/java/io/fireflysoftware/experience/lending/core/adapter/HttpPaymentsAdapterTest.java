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


package io.fireflysoftware.experience.lending.core.adapter;

import io.fireflysoftware.experience.lending.core.product.PaymentInstruction;
import io.fireflysoftware.experience.lending.core.product.PaymentKind;
import io.fireflysoftware.experience.lending.core.product.PaymentPath;
import io.fireflysoftware.experience.lending.core.product.PaymentTry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPaymentsAdapterTest {

    @Test
    void tryMapsHubChallengeAndConfirmSendsThatValue() {
        UUID paymentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        String challengeValue = "9f3a1c7e";
        AtomicReference<String> tryBody = new AtomicReference<>();
        AtomicReference<String> confirmBody = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            String path = request.url().getPath();
            if (path.equals("/api/v1/payments/tries")) {
                MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
                return request.writeTo(mock, ExchangeStrategies.withDefaults()).then(reactor.core.publisher.Mono.defer(() -> {
                    tryBody.set(mock.getBodyAsString().block());
                    return MonoResponse.created(
                            "{\"paymentId\":\"" + paymentId + "\",\"challengeId\":\"" + challengeId
                                    + "\",\"challengeValue\":\"" + challengeValue + "\",\"amount\":25}");
                }));
            }
            if (path.equals("/api/v1/payments/" + paymentId + "/confirmations")) {
                MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
                return request.writeTo(mock, ExchangeStrategies.withDefaults()).then(reactor.core.publisher.Mono.defer(() -> {
                    confirmBody.set(mock.getBodyAsString().block());
                    return MonoResponse.ok("{\"paymentId\":\"" + paymentId + "\",\"amount\":25,\"phase\":\"CONFIRMED\"}");
                }));
            }
            return MonoResponse.status(HttpStatus.NOT_FOUND, "");
        }).build();

        HttpPaymentsAdapterImpl adapter = new HttpPaymentsAdapterImpl(client);
        PaymentInstruction instruction = new PaymentInstruction(UUID.randomUUID(), null, new BigDecimal("25"),
                PaymentKind.DISBURSEMENT, PaymentPath.OUTBOUND);

        PaymentTry tried = adapter.tryPayment(instruction).block();
        assertThat(tried.paymentId()).isEqualTo(paymentId);
        assertThat(tried.challengeId()).isEqualTo(challengeId);
        assertThat(tried.challengeValue()).isEqualTo(challengeValue);
        assertThat(tried.challengeValue()).isNotEqualTo("sandbox");
        assertThat(tryBody.get()).contains("\"scheme\":\"DOMESTIC_OUTBOUND\"");

        StepVerifier.create(adapter.confirm(paymentId, tried.challengeValue()))
                .assertNext(receipt -> {
                    assertThat(receipt.paymentId()).isEqualTo(paymentId);
                    assertThat(receipt.illustrative()).isFalse();
                })
                .verifyComplete();

        assertThat(confirmBody.get()).contains("\"response\":\"" + challengeValue + "\"");
        assertThat(confirmBody.get()).doesNotContain("sandbox");
    }

    @Test
    void internalTryOmitsChallengeWhenTheHubDoes() {
        UUID paymentId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        AtomicReference<String> tryBody = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().getPath()).isEqualTo("/api/v1/payments/tries");
            MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(mock, ExchangeStrategies.withDefaults()).then(reactor.core.publisher.Mono.defer(() -> {
                tryBody.set(mock.getBodyAsString().block());
                return MonoResponse.created("{\"paymentId\":\"" + paymentId + "\",\"amount\":10}");
            }));
        }).build();

        PaymentTry tried = new HttpPaymentsAdapterImpl(client).tryPayment(new PaymentInstruction(
                loanId, UUID.randomUUID(), new BigDecimal("10"), PaymentKind.INSTALLMENT, PaymentPath.INTERNAL)).block();

        assertThat(tried.challengeId()).isNull();
        assertThat(tried.challengeValue()).isNull();
        assertThat(tryBody.get()).contains("\"currency\":\"EUR\"");
        assertThat(tryBody.get()).contains("\"partyId\":\"10000000-0000-4000-8000-000000000001\"");
        assertThat(tryBody.get()).contains("\"contractId\":\"" + loanId + "\"");
        assertThat(tryBody.get()).contains("\"paymentMethodId\":\"10000000-0000-4000-8000-0000000000a1\"");
        assertThat(tryBody.get()).contains("\"scheme\":\"INTERNAL_TRANSFER\"");
    }

    @Test
    void confirmRejectsTheWordSandboxBeforeCallingTheHub() {
        AtomicReference<String> path = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            path.set(request.url().getPath());
            return MonoResponse.ok("{}");
        }).build();

        StepVerifier.create(new HttpPaymentsAdapterImpl(client).confirm(UUID.randomUUID(), "sandbox"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("sandbox"))
                .verify();
        assertThat(path.get()).isNull();
    }

    @Test
    void cancelPostsTheHubCancellation() {
        UUID paymentId = UUID.randomUUID();
        AtomicReference<String> path = new AtomicReference<>();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            path.set(request.url().getPath());
            return MonoResponse.ok("{\"paymentId\":\"" + paymentId + "\",\"phase\":\"CANCELLED\"}");
        }).build();

        StepVerifier.create(new HttpPaymentsAdapterImpl(client).cancel(paymentId))
                .verifyComplete();
        assertThat(path.get()).isEqualTo("/api/v1/payments/" + paymentId + "/cancellations");
    }

    private static final class MonoResponse {
        private MonoResponse() {
        }

        static reactor.core.publisher.Mono<ClientResponse> created(String json) {
            return status(HttpStatus.CREATED, json);
        }

        static reactor.core.publisher.Mono<ClientResponse> ok(String json) {
            return status(HttpStatus.OK, json);
        }

        static reactor.core.publisher.Mono<ClientResponse> status(HttpStatus status, String json) {
            return reactor.core.publisher.Mono.just(ClientResponse.create(status)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build());
        }
    }
}

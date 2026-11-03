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

import io.fireflysoftware.domain.payments.sdk.api.PaymentsApi;
import io.fireflysoftware.domain.payments.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.payments.sdk.model.Confirmation;
import io.fireflysoftware.domain.payments.sdk.model.MovementCommand;
import io.fireflysoftware.domain.payments.sdk.model.MovementResult;
import io.fireflysoftware.experience.lending.core.product.PaymentInstruction;
import io.fireflysoftware.experience.lending.core.product.PaymentKind;
import io.fireflysoftware.experience.lending.core.product.PaymentPath;
import io.fireflysoftware.experience.lending.core.product.PaymentReceipt;
import io.fireflysoftware.experience.lending.core.product.PaymentTry;
import io.fireflysoftware.experience.lending.core.product.PaymentsPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live payments port. Calls {@link PaymentsApi#tryPayment} and {@link PaymentsApi#confirmPayment}.
 * The running application selects this adapter with {@code firefly.experience.payments.adapter=hub}.
 * That is also the default, so a missing property does not fall through to the in-memory sandbox.
 * This class does not generate a challenge and does not treat the word sandbox as a valid code.
 */
@Component("httpPaymentsAdapter")
@ConditionalOnProperty(name = "firefly.experience.payments.adapter", havingValue = "hub", matchIfMissing = true)
public class HttpPaymentsAdapterImpl implements PaymentsPort {

    private static final String OUTBOUND_SCHEME = "DOMESTIC_OUTBOUND";
    private static final String INTERNAL_SCHEME = "INTERNAL_TRANSFER";
    private static final UUID SAMPLE_PARTY = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID SAMPLE_ACCOUNT = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID SAMPLE_PAYMENT_METHOD = UUID.fromString("10000000-0000-4000-8000-0000000000a1");

    private final PaymentsApi payments;
    private final ConcurrentHashMap<UUID, PaymentInstruction> started = new ConcurrentHashMap<>();

    @Autowired
    public HttpPaymentsAdapterImpl(
            @Value("${api-configuration.domain-platform.payments.base-path:http://localhost:8092}") String basePath) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(basePath);
        this.payments = new PaymentsApi(apiClient);
    }

    HttpPaymentsAdapterImpl(WebClient webClient) {
        this.payments = new PaymentsApi(new ApiClient(webClient));
    }

    @Override
    public Mono<PaymentTry> tryPayment(PaymentInstruction instruction) {
        String reference = instruction.installmentId() == null
                ? instruction.loanId().toString()
                : instruction.installmentId().toString();
        MovementCommand command = new MovementCommand()
                .scheme(MovementCommand.SchemeEnum.fromValue(
                        instruction.path() == PaymentPath.OUTBOUND ? OUTBOUND_SCHEME : INTERNAL_SCHEME))
                .idempotencyKey(instruction.loanId() + ":" + instruction.kind().name() + ":" + instruction.amount())
                .amount(instruction.amount())
                .currency("EUR")
                .partyId(SAMPLE_PARTY)
                .contractId(instruction.loanId())
                .paymentMethodId(SAMPLE_PAYMENT_METHOD)
                .sourceAccountId(SAMPLE_ACCOUNT)
                .destinationAccountId(instruction.loanId())
                .thirdPartyReference(reference);
        return payments.tryPayment(command, null)
                .onErrorMap(WebClientResponseException.class, HttpPaymentsAdapterImpl::hubFailure)
                .map(result -> {
                    UUID paymentId = result.getPaymentId();
                    started.put(paymentId, instruction);
                    return new PaymentTry(
                            paymentId,
                            instruction.kind(),
                            instruction.path(),
                            result.getChallengeId(),
                            challengeValue(result));
                });
    }

    @Override
    public Mono<PaymentReceipt> confirm(UUID paymentId, String challengeResponse) {
        if (isSandboxWord(challengeResponse)) {
            return Mono.error(new IllegalArgumentException("The word sandbox is not a valid challenge code"));
        }
        Confirmation confirmation = new Confirmation().response(challengeResponse);
        return payments.confirmPayment(paymentId, confirmation, null)
                .onErrorMap(WebClientResponseException.class, HttpPaymentsAdapterImpl::hubFailure)
                .map(result -> receipt(paymentId, result));
    }

    @Override
    public Mono<Void> cancel(UUID paymentId) {
        return payments.cancelPayment(paymentId, null)
                .onErrorMap(WebClientResponseException.class, HttpPaymentsAdapterImpl::hubFailure)
                .then();
    }

    private PaymentReceipt receipt(UUID paymentId, MovementResult result) {
        PaymentInstruction instruction = started.remove(paymentId);
        PaymentKind kind = instruction == null ? PaymentKind.DISBURSEMENT : instruction.kind();
        BigDecimal amount = instruction == null ? result.getAmount() : instruction.amount();
        UUID id = result.getPaymentId();
        return new PaymentReceipt(id == null ? paymentId : id, kind, amount, "hub", false);
    }

    private static String challengeValue(MovementResult result) {
        String value = result.getChallengeValue();
        if (value == null || value.isBlank() || isSandboxWord(value)) {
            return null;
        }
        return value;
    }

    private static boolean isSandboxWord(String value) {
        return value != null && "sandbox".equalsIgnoreCase(value.trim());
    }

    private static IllegalStateException hubFailure(WebClientResponseException error) {
        return new IllegalStateException(
                "The payments hub returned HTTP " + error.getStatusCode().value(), error);
    }
}

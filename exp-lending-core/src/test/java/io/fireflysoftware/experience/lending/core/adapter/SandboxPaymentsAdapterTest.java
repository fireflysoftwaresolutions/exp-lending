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
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxPaymentsAdapterTest {

    private final SandboxPaymentsAdapterImpl adapter = new SandboxPaymentsAdapterImpl();

    @Test
    void outboundTryReturnsChallengeAndWrongResponseDoesNotConfirm() {
        PaymentInstruction instruction = new PaymentInstruction(UUID.randomUUID(), null, new BigDecimal("25.00"),
                PaymentKind.DISBURSEMENT, PaymentPath.OUTBOUND);

        PaymentTry tried = adapter.tryPayment(instruction).block();
        assertThat(tried.challengeId()).isNotNull();
        assertThat(tried.challengeValue()).isNotBlank();
        assertThat(tried.challengeValue()).isNotEqualTo("sandbox");

        StepVerifier.create(adapter.confirm(tried.paymentId(), "sandbox"))
                .expectError(SandboxPaymentsAdapterImpl.PaymentNotConfirmedException.class)
                .verify();

        StepVerifier.create(adapter.confirm(tried.paymentId(), "wrong"))
                .expectError(SandboxPaymentsAdapterImpl.PaymentNotConfirmedException.class)
                .verify();

        StepVerifier.create(adapter.confirm(tried.paymentId(), tried.challengeValue()))
                .assertNext(receipt -> assertThat(receipt.paymentId()).isEqualTo(tried.paymentId()))
                .verifyComplete();
    }

    @Test
    void internalTryConfirmsWithoutAChallenge() {
        PaymentInstruction instruction = new PaymentInstruction(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), PaymentKind.INSTALLMENT, PaymentPath.INTERNAL);

        PaymentTry tried = adapter.tryPayment(instruction).block();
        assertThat(tried.challengeId()).isNull();
        assertThat(tried.challengeValue()).isNull();

        StepVerifier.create(adapter.confirm(tried.paymentId(), null))
                .assertNext(receipt -> {
                    assertThat(receipt.paymentId()).isEqualTo(tried.paymentId());
                    assertThat(receipt.kind()).isEqualTo(PaymentKind.INSTALLMENT);
                    assertThat(receipt.illustrative()).isTrue();
                })
                .verifyComplete();
    }
}

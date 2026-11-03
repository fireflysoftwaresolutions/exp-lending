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

import io.fireflysoftware.domain.lending.creditscoring.sdk.api.DecisionApi;
import io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionResponse;
import io.fireflysoftware.experience.lending.core.ports.DecisionOutcome;
import io.fireflysoftware.experience.lending.core.ports.DecisionRequest;
import io.fireflysoftware.experience.lending.core.ports.JourneyStep;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class SdkDecisionClientTest {

    @Test
    void decide_usesEvaluateDecision() {
        UUID partyId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        DecisionApi decisions = mock(DecisionApi.class);
        when(decisions.evaluateDecision(any())).thenReturn(Mono.just(new DecisionResponse()
                .outcome(DecisionResponse.OutcomeEnum.REFER)
                .reasonCodes(List.of("CREDIT_SCORE_BELOW_THRESHOLD"))));

        StepVerifier.create(new DomainDecisionConfiguration.SdkDecisionClientImpl(decisions).decide(
                        new DecisionRequest(partyId, productId, JourneyStep.LENDING_APPLICATION, Map.of())))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(DecisionOutcome.REFER);
                    assertThat(result.reasonCodes()).containsExactly("CREDIT_SCORE_BELOW_THRESHOLD");
                })
                .verifyComplete();
        ArgumentCaptor<io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionRequest> body =
                ArgumentCaptor.forClass(io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionRequest.class);
        verify(decisions).evaluateDecision(body.capture());
        assertThat(body.getValue().getPartyId()).isEqualTo(partyId);
        assertThat(body.getValue().getProductId()).isEqualTo(productId);
        assertThat(body.getValue().getJourneyStep().name()).isEqualTo(JourneyStep.LENDING_APPLICATION.name());
        assertThat(body.getValue().getAttributes()).isEmpty();
    }
}

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


package io.fireflysoftware.experience.lending.core.application.services;

import io.fireflysoftware.domain.common.contracts.sdk.api.KitDocumentsControllerApi;
import io.fireflysoftware.domain.common.contracts.sdk.model.RecordedId;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.LoanApplicationDTO;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.SubmitApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.commands.CreateApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.commands.UpdateApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.adapter.DomainLoanApplicationAdapterImpl;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @Mock
    private LoanOriginationApi loanOriginationApi;
    @Mock
    private KitDocumentsControllerApi kitDocumentsApi;

    private DomainLoanApplicationAdapterImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(kitDocumentsApi.issue(any(), any())).thenReturn(Mono.just(new RecordedId().id(UUID.randomUUID())));
        service = new DomainLoanApplicationAdapterImpl(loanOriginationApi, kitDocumentsApi);
    }

    @Test
    void createApplication_submitsToOriginationApi_andReturnsDetail() {
        var applicationId = UUID.randomUUID();
        var simulationId = UUID.randomUUID();
        Map<String, UUID> submitResponse = Map.of("loanApplicationId", applicationId);

        var dto = new LoanApplicationDTO()
                .loanApplicationId(applicationId)
                .loanPurpose("PERSONAL")
                .simulationId(simulationId)
                .createdAt(LocalDateTime.now());

        when(loanOriginationApi.submitApplication(any(SubmitApplicationCommand.class), any()))
                .thenReturn(Mono.just(submitResponse));
        when(loanOriginationApi.getApplication(eq(applicationId), any()))
                .thenReturn(Mono.just(dto));

        var command = new CreateApplicationCommand();
        command.setSimulationId(simulationId);
        command.setProductId(UUID.randomUUID());
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("15000"));
        command.setTerm(36);
        command.setPurpose("PERSONAL");

        StepVerifier.create(service.createApplication(command))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getApplicationId()).isEqualTo(applicationId);
                    assertThat(result.getSimulationId()).isEqualTo(simulationId);
                    assertThat(result.getRequestedAmount()).isEqualByComparingTo("15000");
                    assertThat(result.getTerm()).isEqualTo(36);
                    assertThat(result.getPurpose()).isEqualTo("PERSONAL");
                })
                .verifyComplete();
        org.mockito.Mockito.verifyNoInteractions(kitDocumentsApi);
    }

    @Test
    void createApplication_passesSimulationIdThroughToDownstreamCommand() {
        var applicationId = UUID.randomUUID();
        var simulationId = UUID.randomUUID();
        Map<String, UUID> submitResponse = Map.of("loanApplicationId", applicationId);

        var dto = new LoanApplicationDTO()
                .loanApplicationId(applicationId)
                .createdAt(LocalDateTime.now());

        when(loanOriginationApi.submitApplication(any(SubmitApplicationCommand.class), any()))
                .thenReturn(Mono.just(submitResponse));
        when(loanOriginationApi.getApplication(eq(applicationId), any()))
                .thenReturn(Mono.just(dto));

        var command = new CreateApplicationCommand();
        command.setSimulationId(simulationId);
        command.setProductId(UUID.randomUUID());
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("9000"));
        command.setTerm(24);
        command.setPurpose("CAR");

        StepVerifier.create(service.createApplication(command))
                .expectNextCount(1)
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(SubmitApplicationCommand.class);
        verify(loanOriginationApi).submitApplication(captor.capture(), any());
        var sent = captor.getValue();
        assertThat(sent.getApplication()).isNotNull();
        assertThat(sent.getApplication().getSimulationId()).isEqualTo(simulationId);
        assertThat(sent.getApplication().getLoanPurpose()).isEqualTo("CAR");
        assertThat(sent.getApplication().getApplicationDate()).isNotNull();
    }

    @Test
    void createApplication_failsWithBusinessException_whenResponseMissingApplicationId() {
        Map<String, UUID> submitResponse = Map.of();

        when(loanOriginationApi.submitApplication(any(SubmitApplicationCommand.class), any()))
                .thenReturn(Mono.just(submitResponse));

        var command = new CreateApplicationCommand();
        command.setProductId(UUID.randomUUID());
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("1000"));
        command.setTerm(12);
        command.setPurpose("HOME_IMPROVEMENT");

        StepVerifier.create(service.createApplication(command))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void createApplication_propagatesUpstreamError() {
        when(loanOriginationApi.submitApplication(any(SubmitApplicationCommand.class), any()))
                .thenReturn(Mono.error(new RuntimeException("upstream error")));

        var command = new CreateApplicationCommand();
        command.setProductId(UUID.randomUUID());
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("5000"));
        command.setTerm(18);

        StepVerifier.create(service.createApplication(command))
                .expectErrorMessage("upstream error")
                .verify();
    }

    @Test
    void createApplication_rejectsMissingPartyBeforeCallingOwners() {
        var applicationId = UUID.randomUUID();
        lenient().when(loanOriginationApi.submitApplication(any(SubmitApplicationCommand.class), any()))
                .thenReturn(Mono.just(Map.of("loanApplicationId", applicationId)));
        lenient().when(loanOriginationApi.getApplication(eq(applicationId), any()))
                .thenReturn(Mono.just(new LoanApplicationDTO().loanApplicationId(applicationId)));
        var command = new CreateApplicationCommand();
        command.setProductId(UUID.randomUUID());
        command.setSimulationId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("5000"));
        command.setTerm(18);

        StepVerifier.create(service.createApplication(command))
                .expectError(BusinessException.class)
                .verify();
        org.mockito.Mockito.verifyNoInteractions(loanOriginationApi, kitDocumentsApi);
    }

    @Test
    void createApplication_rejectsMissingProductId_withBusinessException() {
        var command = new CreateApplicationCommand();
        command.setRequestedAmount(new BigDecimal("5000"));
        command.setTerm(18);

        StepVerifier.create(service.createApplication(command))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void createApplication_rejectsNonPositiveRequestedAmount_withBusinessException() {
        var command = new CreateApplicationCommand();
        command.setProductId(UUID.randomUUID());
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(BigDecimal.ZERO);
        command.setTerm(18);

        StepVerifier.create(service.createApplication(command))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void createApplication_rejectsZeroTerm_withBusinessException() {
        var command = new CreateApplicationCommand();
        command.setProductId(UUID.randomUUID());
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("5000"));
        command.setTerm(0);

        StepVerifier.create(service.createApplication(command))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void getApplication_delegatesToOriginationApi_andMapsResult() {
        var applicationId = UUID.randomUUID();
        var dto = new LoanApplicationDTO()
                .loanApplicationId(applicationId)
                .loanPurpose("CAR")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now());

        when(loanOriginationApi.getApplication(eq(applicationId), any())).thenReturn(Mono.just(dto));

        StepVerifier.create(service.getApplication(applicationId))
                .assertNext(result -> {
                    assertThat(result.getApplicationId()).isEqualTo(applicationId);
                    assertThat(result.getPurpose()).isEqualTo("CAR");
                    assertThat(result.getCreatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void updateApplication_returnsCurrentStateWithPatchedFields() {
        var applicationId = UUID.randomUUID();
        var dto = new LoanApplicationDTO()
                .loanApplicationId(applicationId)
                .loanPurpose("RENOVATION")
                .createdAt(LocalDateTime.now());

        when(loanOriginationApi.getApplication(eq(applicationId), any())).thenReturn(Mono.just(dto));

        var command = new UpdateApplicationCommand();
        command.setRequestedAmount(new BigDecimal("20000"));
        command.setTerm(48);

        StepVerifier.create(service.updateApplication(applicationId, command))
                .assertNext(result -> {
                    assertThat(result.getApplicationId()).isEqualTo(applicationId);
                    assertThat(result.getRequestedAmount()).isEqualByComparingTo("20000");
                    assertThat(result.getTerm()).isEqualTo(48);
                    assertThat(result.getPurpose()).isEqualTo("RENOVATION");
                })
                .verifyComplete();
    }

    @Test
    void submitApplication_delegatesToApproveApplication() {
        var applicationId = UUID.randomUUID();
        when(loanOriginationApi.approveApplication(eq(applicationId), any()))
                .thenReturn(Mono.just(Map.of("status", "APPROVED")));

        StepVerifier.create(service.submitApplication(applicationId))
                .verifyComplete();
    }

    @Test
    void withdrawApplication_delegatesToWithdrawApplication() {
        var applicationId = UUID.randomUUID();
        when(loanOriginationApi.withdrawApplication(eq(applicationId), any()))
                .thenReturn(Mono.just(Map.of("status", "WITHDRAWN")));

        StepVerifier.create(service.withdrawApplication(applicationId))
                .verifyComplete();
    }

    @Test
    void getStatusHistory_readsDomainDecisions() {
        var applicationId = UUID.randomUUID();
        var page = new io.fireflysoftware.domain.lending.loan.origination.sdk.model.PaginationResponseUnderwritingDecisionDTO();
        when(loanOriginationApi.getApplicationDecisions(eq(applicationId), any())).thenReturn(Mono.just(page));

        StepVerifier.create(service.getStatusHistory(applicationId))
                .assertNext(result -> assertThat(result.getEntries()).isEmpty())
                .verifyComplete();
    }
}

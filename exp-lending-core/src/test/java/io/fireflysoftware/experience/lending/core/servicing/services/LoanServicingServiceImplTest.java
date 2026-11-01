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


package io.fireflysoftware.experience.lending.core.servicing.services;

import io.fireflysoftware.domain.lending.loan.servicing.sdk.api.LoanServicingApi;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.api.LoanServicingQueriesApi;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.experience.lending.core.servicing.commands.RequestEarlyRepaymentCommand;
import io.fireflysoftware.experience.lending.core.servicing.commands.RequestRestructuringCommand;
import io.fireflysoftware.experience.lending.core.servicing.adapter.DomainLoanServicingAdapterImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServicingServiceImplTest {

    @Mock private LoanServicingApi loanServicingApi;
    @Mock private LoanServicingQueriesApi loanServicingQueriesApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DomainLoanServicingAdapterImpl service;

    @BeforeEach
    void setUp() {
        service = new DomainLoanServicingAdapterImpl(
                loanServicingApi, loanServicingQueriesApi, objectMapper);
    }

    // --- getLoan ---

    @Test
    void getLoan_mapsServicingCaseToDetail() {
        var loanId = UUID.randomUUID();
        var maturity = LocalDate.of(2030, 1, 1);
        var responseMap = Map.of(
                "loanServicingCaseId", loanId.toString(),
                "servicingStatus", "ACTIVE",
                "principalAmount", "50000",
                "loanTerm", "60",
                "interestRate", "4.5",
                "originationDate", "2025-01-01",
                "maturityDate", maturity.toString()
        );

        when(loanServicingApi.getLoanDetails(eq(loanId.toString()), any()))
                .thenReturn(Mono.just(responseMap));

        StepVerifier.create(service.getLoan(loanId))
                .assertNext(detail -> {
                    assertThat(detail.getLoanId()).isEqualTo(loanId);
                    assertThat(detail.getOriginalAmount()).isEqualByComparingTo("50000");
                    assertThat(detail.getTerm()).isEqualTo(60);
                    assertThat(detail.getInterestRate()).isEqualByComparingTo("4.5");
                    assertThat(detail.getMaturityDate()).isEqualTo(maturity);
                })
                .verifyComplete();
    }

    @Test
    void getLoan_propagatesUpstreamError() {
        var loanId = UUID.randomUUID();
        when(loanServicingApi.getLoanDetails(eq(loanId.toString()), any()))
                .thenReturn(Mono.error(new RuntimeException("not found")));

        StepVerifier.create(service.getLoan(loanId))
                .expectErrorMessage("not found")
                .verify();
    }

    // --- getBalance ---

    @Test
    void getBalance_prefersCurrentSnapshot() {
        var loanId = UUID.randomUUID();
        var stale = new LoanBalanceDTO()
                .loanServicingCaseId(loanId)
                .principalOutstanding(new BigDecimal("9000"))
                .totalOutstanding(new BigDecimal("9200"))
                .isCurrent(false)
                .createdAt(LocalDateTime.now().minusDays(5));
        var current = new LoanBalanceDTO()
                .loanServicingCaseId(loanId)
                .principalOutstanding(new BigDecimal("8500"))
                .totalOutstanding(new BigDecimal("8700"))
                .isCurrent(true)
                .createdAt(LocalDateTime.now());

        var page = new PaginationResponseLoanBalanceDTO().content(List.of(stale, current));
        when(loanServicingQueriesApi.getLoanBalances(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getBalance(loanId))
                .assertNext(balance -> {
                    assertThat(balance.getOutstandingPrincipal()).isEqualByComparingTo("8500");
                    assertThat(balance.getTotalOutstanding()).isEqualByComparingTo("8700");
                    assertThat(balance.getCurrency()).isEqualTo("EUR");
                })
                .verifyComplete();
    }

    // --- getRepaymentSchedule ---

    @Test
    void getRepaymentSchedule_mapsToPaidStatus() {
        var loanId = UUID.randomUUID();
        var entry = new LoanRepaymentScheduleDTO()
                .installmentNumber(1)
                .dueDate(LocalDate.of(2025, 2, 1))
                .principalDue(new BigDecimal("800"))
                .interestDue(new BigDecimal("37.50"))
                .totalDue(new BigDecimal("837.50"))
                .isPaid(true);

        var page = new PaginationResponseLoanRepaymentScheduleDTO().content(List.of(entry));
        when(loanServicingQueriesApi.getLoanSchedule(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getRepaymentSchedule(loanId))
                .assertNext(e -> {
                    assertThat(e.getInstallmentNumber()).isEqualTo(1);
                    assertThat(e.getStatus()).isEqualTo("PAID");
                    assertThat(e.getTotalAmount()).isEqualByComparingTo("837.50");
                })
                .verifyComplete();
    }

    // --- getInstallments ---

    @Test
    void getInstallments_mapsOverdueStatus_whenDueDatePast() {
        var loanId = UUID.randomUUID();
        var planId = UUID.randomUUID();
        var plan = new LoanInstallmentPlanDTO()
                .loanInstallmentPlanId(planId)
                .installmentNumber(3)
                .dueDate(LocalDate.of(2024, 1, 1))  // past date -> OVERDUE
                .totalDue(new BigDecimal("450"))
                .isPaid(false);

        var page = new PaginationResponseLoanInstallmentPlanDTO().content(List.of(plan));
        when(loanServicingQueriesApi.getLoanInstallmentPlan(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getInstallments(loanId))
                .assertNext(inst -> {
                    assertThat(inst.getInstallmentId()).isEqualTo(planId);
                    assertThat(inst.getStatus()).isEqualTo("OVERDUE");
                })
                .verifyComplete();
    }

    @Test
    void getInstallment_delegatesToGetByFilter() {
        var loanId = UUID.randomUUID();
        var installmentId = UUID.randomUUID();
        var plan = new LoanInstallmentPlanDTO()
                .loanInstallmentPlanId(installmentId)
                .installmentNumber(2)
                .dueDate(LocalDate.now().plusMonths(1))
                .totalDue(new BigDecimal("500"))
                .isPaid(false);

        var page = new PaginationResponseLoanInstallmentPlanDTO().content(List.of(plan));
        when(loanServicingQueriesApi.getLoanInstallmentPlan(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getInstallment(loanId, installmentId))
                .assertNext(inst -> {
                    assertThat(inst.getInstallmentId()).isEqualTo(installmentId);
                    assertThat(inst.getStatus()).isEqualTo("SCHEDULED");
                })
                .verifyComplete();
    }

    // --- getDisbursements ---

    @Test
    void getDisbursements_mapsDisbursementStatus() {
        var loanId = UUID.randomUUID();
        var disbId = UUID.randomUUID();
        var disb = new LoanDisbursementDTO()
                .loanDisbursementId(disbId)
                .disbursementAmount(new BigDecimal("25000"))
                .disbursementStatus(LoanDisbursementDTO.DisbursementStatusEnum.COMPLETED)
                .disbursementDate(LocalDate.of(2025, 3, 1));

        var page = new PaginationResponseLoanDisbursementDTO().content(List.of(disb));
        when(loanServicingQueriesApi.getLoanDisbursements(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getDisbursements(loanId))
                .assertNext(d -> {
                    assertThat(d.getDisbursementId()).isEqualTo(disbId);
                    assertThat(d.getAmount()).isEqualByComparingTo("25000");
                    assertThat(d.getStatus()).isEqualTo("COMPLETED");
                })
                .verifyComplete();
    }

    // --- requestEarlyRepayment ---

    @Test
    void requestEarlyRepayment_callsApplyRepaymentWithThePaymentId() {
        var loanId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        when(loanServicingApi.applyRepayment(eq(loanId.toString()), any(), any()))
                .thenReturn(Mono.just(new Object()));

        var cmd = new RequestEarlyRepaymentCommand();
        cmd.setAmount(new BigDecimal("5000"));
        cmd.setType("PARTIAL");
        cmd.setTransactionId(paymentId);

        StepVerifier.create(service.requestEarlyRepayment(loanId, cmd))
                .verifyComplete();

        verify(loanServicingApi).applyRepayment(eq(loanId.toString()), argThat(body ->
                paymentId.equals(((io.fireflysoftware.domain.lending.loan.servicing.sdk.model.ApplyRepaymentCommand) body)
                        .getTransactionId())), any());
    }

    @Test
    void requestEarlyRepayment_rejectsAMissingPaymentId() {
        var cmd = new RequestEarlyRepaymentCommand();
        cmd.setAmount(new BigDecimal("5000"));
        cmd.setType("PARTIAL");

        StepVerifier.create(service.requestEarlyRepayment(UUID.randomUUID(), cmd))
                .expectError(IllegalStateException.class)
                .verify();

        verify(loanServicingApi, never()).applyRepayment(any(), any(), any());
    }

    // --- simulateEarlyRepayment ---

    @Test
    void simulateEarlyRepayment_returnsStubWithZeroPenalty() {
        var loanId = UUID.randomUUID();
        var cmd = new RequestEarlyRepaymentCommand();
        cmd.setAmount(new BigDecimal("10000"));
        cmd.setType("FULL");

        when(loanServicingQueriesApi.getLoanBalances(eq(loanId), isNull()))
                .thenReturn(Mono.just(new io.fireflysoftware.domain.lending.loan.servicing.sdk.model.PaginationResponseLoanBalanceDTO()));
        StepVerifier.create(service.simulateEarlyRepayment(loanId, cmd))
                .assertNext(sim -> assertThat(sim.getTotalAmount()).isEqualByComparingTo("10000"))
                .verifyComplete();
    }

    // --- getRateInfo ---

    @Test
    void getRateInfo_derivesRateFromServicingCase() {
        var loanId = UUID.randomUUID();
        var responseMap = Map.of("interestRate", "3.75");

        when(loanServicingApi.getLoanDetails(eq(loanId.toString()), any()))
                .thenReturn(Mono.just(responseMap));

        StepVerifier.create(service.getRateInfo(loanId))
                .assertNext(info -> {
                    assertThat(info.getCurrentRate()).isEqualByComparingTo("3.75");
                    assertThat(info.getNextReviewDate()).isNull();
                })
                .verifyComplete();
    }

    // --- getAccruals ---

    @Test
    void getAccruals_mapsAccrualFields() {
        var loanId = UUID.randomUUID();
        var accrualId = UUID.randomUUID();
        var accrual = new LoanAccrualDTO()
                .loanAccrualId(accrualId)
                .accrualAmount(new BigDecimal("12.50"))
                .accrualDate(LocalDate.of(2025, 3, 31))
                .createdAt(LocalDateTime.now());

        var page = new PaginationResponseLoanAccrualDTO().content(List.of(accrual));
        when(loanServicingQueriesApi.getLoanAccruals(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getAccruals(loanId))
                .assertNext(a -> {
                    assertThat(a.getAccrualId()).isEqualTo(accrualId);
                    assertThat(a.getAmount()).isEqualByComparingTo("12.50");
                    assertThat(a.getPeriod()).isEqualTo("2025-03-31");
                })
                .verifyComplete();
    }

    @Test
    void getDisbursementPlan_readsDomainPlan() {
        var loanId = UUID.randomUUID();
        when(loanServicingQueriesApi.getLoanDisbursementPlan(eq(loanId), isNull()))
                .thenReturn(Mono.empty());
        StepVerifier.create(service.getDisbursementPlan(loanId)).verifyComplete();
    }

    @Test
    void getEscrow_readsDomainEscrow() {
        var loanId = UUID.randomUUID();
        when(loanServicingQueriesApi.getLoanEscrow(eq(loanId), isNull())).thenReturn(Mono.empty());
        StepVerifier.create(service.getEscrow(loanId)).verifyComplete();
    }

    @Test
    void getRebates_readsDomainRebates() {
        var loanId = UUID.randomUUID();
        when(loanServicingQueriesApi.getLoanRebates(eq(loanId), isNull())).thenReturn(Mono.empty());
        StepVerifier.create(service.getRebates(loanId)).verifyComplete();
    }

    @Test
    void getNotifications_readsDomainNotifications() {
        var loanId = UUID.randomUUID();
        when(loanServicingQueriesApi.getLoanNotifications(eq(loanId), isNull())).thenReturn(Mono.empty());
        StepVerifier.create(service.getNotifications(loanId)).verifyComplete();
    }

    // --- requestRestructuring ---

    @Test
    void requestRestructuring_callsApplyRestructure() {
        var loanId = UUID.randomUUID();
        var restructuringId = UUID.randomUUID();
        var responseMap = Map.of(
                "loanRestructuringId", restructuringId.toString(),
                "status", "RESTRUCTURED",
                "reason", "Financial hardship"
        );

        when(loanServicingApi.applyRestructure(eq(loanId.toString()), any(), any()))
                .thenReturn(Mono.just(responseMap));

        var cmd = new RequestRestructuringCommand();
        cmd.setReason("Financial hardship");
        cmd.setRequestedTermChanges("Extend by 12 months");

        StepVerifier.create(service.requestRestructuring(loanId, cmd))
                .assertNext(r -> {
                    assertThat(r.getRestructuringId()).isEqualTo(restructuringId);
                    assertThat(r.getStatus()).isEqualTo("RESTRUCTURED");
                })
                .verifyComplete();
    }

    // --- getEvents ---

    @Test
    void getEvents_mapsServicingEvents() {
        var loanId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var event = new LoanServicingEventDTO()
                .loanServicingEventId(eventId)
                .eventType(LoanServicingEventDTO.EventTypeEnum.RESTRUCTURE)
                .description("Restructure applied")
                .createdAt(LocalDateTime.now());

        var page = new PaginationResponseLoanServicingEventDTO().content(List.of(event));
        when(loanServicingQueriesApi.getLoanEvents(eq(loanId), isNull()))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.getEvents(loanId))
                .assertNext(e -> {
                    assertThat(e.getEventId()).isEqualTo(eventId);
                    assertThat(e.getEventType()).isEqualTo("RESTRUCTURE");
                    assertThat(e.getDescription()).isEqualTo("Restructure applied");
                })
                .verifyComplete();
    }
}

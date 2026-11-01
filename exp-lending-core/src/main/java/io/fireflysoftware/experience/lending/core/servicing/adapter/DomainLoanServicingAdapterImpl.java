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


package io.fireflysoftware.experience.lending.core.servicing.adapter;

import io.fireflysoftware.domain.lending.loan.servicing.sdk.api.LoanServicingApi;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.api.LoanServicingQueriesApi;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.ApplyRepaymentCommand;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.ApplyRestructureCommand;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanAccrualDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanBalanceDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanDisbursementDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanInstallmentPlanDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanInstallmentRecordDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanRateChangeDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanRepaymentRecordDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanRepaymentScheduleDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanRestructuringDTO;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.LoanServicingEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.experience.lending.core.servicing.commands.RequestEarlyRepaymentCommand;
import io.fireflysoftware.experience.lending.core.servicing.commands.RequestRestructuringCommand;
import io.fireflysoftware.experience.lending.core.servicing.queries.*;
import io.fireflysoftware.experience.lending.core.servicing.ports.LoanServicingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link LoanServicingPort}, delegating to the domain Loan Servicing
 * SDK for balance, schedule, installment, disbursement, rate change, accrual, and restructuring
 * queries, and to {@code LoanServicingApi} for command operations.
 */
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "firefly.experience.lending.ports.loan-servicing", havingValue = "domain", matchIfMissing = true)
@Component("domainLoanServicingAdapter")
@RequiredArgsConstructor
public class DomainLoanServicingAdapterImpl implements LoanServicingPort {

    private final LoanServicingApi loanServicingApi;
    private final LoanServicingQueriesApi loanServicingQueriesApi;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<LoanDetailDTO> getLoan(UUID loanId) {
        log.debug("Getting loan loanId={}", loanId);
        return loanServicingApi.getLoanDetails(loanId.toString(), null)
                .map(item -> {
                    var jsonMap = objectMapper.convertValue(item, Map.class);
                    return toLoanDetail(loanId, jsonMap);
                });
    }

    @Override
    public Mono<BalanceDTO> getBalance(UUID loanId) {
        log.debug("Getting current balance loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanBalances(loanId, null)
                .map(page -> {
                    List<LoanBalanceDTO> content = page.getContent() != null ? page.getContent() : List.of();
                    // Prefer current snapshot; fall back to first entry
                    return content.stream()
                            .filter(b -> Boolean.TRUE.equals(b.getIsCurrent()))
                            .findFirst()
                            .or(() -> content.stream().findFirst())
                            .map(this::toBalance)
                            .orElse(BalanceDTO.builder().loanId(loanId).build());
                });
    }

    @Override
    public Mono<BalanceHistoryDTO> getBalanceHistory(UUID loanId) {
        log.debug("Getting balance history loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanBalances(loanId, null)
                .map(page -> {
                    List<BalanceDTO> entries = page.getContent() != null
                            ? page.getContent().stream().map(this::toBalance).toList()
                            : List.of();
                    return BalanceHistoryDTO.builder().entries(entries).build();
                });
    }

    @Override
    public Flux<ScheduleEntryDTO> getRepaymentSchedule(UUID loanId) {
        log.debug("Getting repayment schedule loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanSchedule(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toScheduleEntry);
    }

    @Override
    public Flux<InstallmentDTO> getInstallments(UUID loanId) {
        log.debug("Getting installments loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanInstallmentPlan(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toInstallment);
    }

    @Override
    public Mono<InstallmentDTO> getInstallment(UUID loanId, UUID installmentId) {
        log.debug("Getting installment loanId={} installmentId={}", loanId, installmentId);
        return loanServicingQueriesApi
                .getLoanInstallmentPlan(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .filter(p -> installmentId.equals(p.getLoanInstallmentPlanId()))
                .next()
                .map(this::toInstallment);
    }

    @Override
    public Flux<InstallmentPaymentDTO> getInstallmentPayments(UUID loanId, UUID installmentId) {
        log.debug("Getting payments for installmentId={} loanId={}", installmentId, loanId);
        return loanServicingQueriesApi
                .getLoanInstallmentRecords(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toInstallmentPayment);
    }

    @Override
    public Flux<DisbursementDTO> getDisbursements(UUID loanId) {
        log.debug("Getting disbursements loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanDisbursements(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toDisbursement);
    }

    @Override
    public Mono<DisbursementDTO> getDisbursement(UUID loanId, UUID disbursementId) {
        log.debug("Getting disbursement loanId={} disbursementId={}", loanId, disbursementId);
        return loanServicingQueriesApi
                .getLoanDisbursements(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .filter(d -> disbursementId.equals(d.getLoanDisbursementId()))
                .next()
                .map(this::toDisbursement);
    }

    @Override
    public Mono<DisbursementPlanDTO> getDisbursementPlan(UUID loanId) {
        log.debug("Getting disbursement plan loanId={}", loanId);
        return loanServicingQueriesApi.getLoanDisbursementPlan(loanId, null)
                .map(page -> DisbursementPlanDTO.builder()
                        .entries(contentOf(page).stream().map(this::toDisbursementFromObject).toList())
                        .build());
    }

    @Override
    public Flux<RepaymentDTO> getRepayments(UUID loanId) {
        log.debug("Getting repayments loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanRepayments(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toRepayment);
    }

    @Override
    public Mono<Void> requestEarlyRepayment(UUID loanId, RequestEarlyRepaymentCommand command) {
        log.debug("Requesting early repayment loanId={}", loanId);
        if (command.getTransactionId() == null) {
            return Mono.error(new IllegalStateException("A payment id is required"));
        }
        return loanServicingApi
                .applyRepayment(loanId.toString(), new ApplyRepaymentCommand()
                        .paymentAmount(command.getAmount())
                        .paymentDate(LocalDate.now())
                        .transactionId(command.getTransactionId())
                        .isPartialPayment(!"FULL".equals(command.getType())), UUID.randomUUID().toString())
                .then();
    }

    @Override
    public Mono<EarlyRepaymentSimulationDTO> simulateEarlyRepayment(UUID loanId,
                                                                     RequestEarlyRepaymentCommand command) {
        log.debug("Simulating early repayment loanId={}", loanId);
        return loanServicingQueriesApi.getLoanBalances(loanId, null)
                .map(page -> {
                    var balances = contentOf(page);
                    BigDecimal outstanding = balances.isEmpty()
                            ? null
                            : decimal(objectMapper.convertValue(balances.getFirst(), Map.class).get("totalBalance"));
                    return EarlyRepaymentSimulationDTO.builder()
                            .totalAmount(command.getAmount() != null ? command.getAmount() : outstanding)
                            .build();
                });
    }

    @Override
    public Mono<RateInfoDTO> getRateInfo(UUID loanId) {
        log.debug("Getting rate info loanId={}", loanId);
        return loanServicingApi.getLoanDetails(loanId.toString(), null)
                .map(item -> {
                    var jsonMap = objectMapper.convertValue(item, Map.class);
                    BigDecimal interestRate = jsonMap.get("interestRate") != null
                            ? new BigDecimal(jsonMap.get("interestRate").toString()) : null;
                    return RateInfoDTO.builder()
                            .currentRate(interestRate)
                            .build();
                });
    }

    @Override
    public Flux<RateChangeDTO> getRateChanges(UUID loanId) {
        log.debug("Getting rate changes loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanRateChanges(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toRateChange);
    }

    @Override
    public Flux<AccrualDTO> getAccruals(UUID loanId) {
        log.debug("Getting accruals loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanAccruals(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toAccrual);
    }

    @Override
    public Flux<EscrowDTO> getEscrow(UUID loanId) {
        log.debug("Getting escrow loanId={}", loanId);
        return loanServicingQueriesApi.getLoanEscrow(loanId, null)
                .flatMapMany(page -> Flux.fromIterable(contentOf(page)))
                .map(item -> objectMapper.convertValue(item, EscrowDTO.class));
    }

    @Override
    public Flux<RebateDTO> getRebates(UUID loanId) {
        log.debug("Getting rebates loanId={}", loanId);
        return loanServicingQueriesApi.getLoanRebates(loanId, null)
                .flatMapMany(page -> Flux.fromIterable(contentOf(page)))
                .map(item -> objectMapper.convertValue(item, RebateDTO.class));
    }

    @Override
    public Mono<RestructuringDTO> requestRestructuring(UUID loanId, RequestRestructuringCommand command) {
        log.debug("Requesting restructuring loanId={}", loanId);
        return loanServicingApi
                .applyRestructure(loanId.toString(), new ApplyRestructureCommand().reason(command.getReason()),
                        UUID.randomUUID().toString())
                .flatMap(item -> {
                    var jsonMap = objectMapper.convertValue(item, Map.class);
                    Object rawId = jsonMap.get("loanRestructuringId");
                    if (rawId == null || rawId.toString().isBlank()) {
                        return Mono.error(new IllegalStateException("Restructuring id was not returned"));
                    }
                    UUID restructuringId;
                    try {
                        restructuringId = UUID.fromString(rawId.toString());
                    } catch (IllegalArgumentException ex) {
                        return Mono.error(new IllegalStateException("Restructuring id was not returned"));
                    }
                    String reason = jsonMap.get("reason") != null
                            ? jsonMap.get("reason").toString() : command.getReason();
                    String status = jsonMap.get("status") == null ? null : jsonMap.get("status").toString();
                    return Mono.just(RestructuringDTO.builder()
                            .restructuringId(restructuringId)
                            .status(status)
                            .requestedAt(LocalDate.now().atStartOfDay())
                            .newTerms(reason)
                            .build());
                });
    }

    @Override
    public Flux<RestructuringDTO> getRestructurings(UUID loanId) {
        log.debug("Getting restructurings loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanRestructurings(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toRestructuring);
    }

    @Override
    public Flux<LoanEventDTO> getEvents(UUID loanId) {
        log.debug("Getting servicing events loanId={}", loanId);
        return loanServicingQueriesApi
                .getLoanEvents(loanId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::toEvent);
    }

    @Override
    public Flux<LoanNotificationDTO> getNotifications(UUID loanId) {
        log.debug("Getting notifications loanId={}", loanId);
        return loanServicingQueriesApi.getLoanNotifications(loanId, null)
                .flatMapMany(page -> Flux.fromIterable(contentOf(page)))
                .map(item -> objectMapper.convertValue(item, LoanNotificationDTO.class));
    }

    // --- Mappers ---

    private LoanDetailDTO toLoanDetail(UUID loanId, Map<String, Object> jsonMap) {
        String status = jsonMap.get("servicingStatus") != null
                ? jsonMap.get("servicingStatus").toString() : null;
        String productType = jsonMap.get("productCatalogId") != null
                ? jsonMap.get("productCatalogId").toString() : null;
        BigDecimal principalAmount = jsonMap.get("principalAmount") != null
                ? new BigDecimal(jsonMap.get("principalAmount").toString()) : null;
        Integer loanTerm = jsonMap.get("loanTerm") != null
                ? Integer.parseInt(jsonMap.get("loanTerm").toString()) : null;
        BigDecimal interestRate = jsonMap.get("interestRate") != null
                ? new BigDecimal(jsonMap.get("interestRate").toString()) : null;
        LocalDate originationDate = jsonMap.get("originationDate") != null
                ? LocalDate.parse(jsonMap.get("originationDate").toString()) : null;
        LocalDate maturityDate = jsonMap.get("maturityDate") != null
                ? LocalDate.parse(jsonMap.get("maturityDate").toString()) : null;
        return LoanDetailDTO.builder()
                .loanId(loanId)
                .productType(productType)
                .status(status)
                .outstandingBalance(null)
                .nextPaymentDate(null)
                .nextPaymentAmount(null)
                .originalAmount(principalAmount)
                .term(loanTerm)
                .interestRate(interestRate)
                .disbursementDate(originationDate)
                .maturityDate(maturityDate)
                .build();
    }

    private BalanceDTO toBalance(LoanBalanceDTO dto) {
        return BalanceDTO.builder()
                .loanId(dto.getLoanServicingCaseId())
                .outstandingPrincipal(dto.getPrincipalOutstanding())
                .outstandingInterest(dto.getInterestOutstanding())
                .totalOutstanding(dto.getTotalOutstanding())
                .currency("EUR") // MVP: currency not stored on LoanBalanceDTO
                .asOfDate(dto.getCreatedAt())
                .build();
    }

    private ScheduleEntryDTO toScheduleEntry(LoanRepaymentScheduleDTO dto) {
        String status = Boolean.TRUE.equals(dto.getIsPaid()) ? "PAID" : "SCHEDULED";
        return ScheduleEntryDTO.builder()
                .installmentNumber(dto.getInstallmentNumber())
                .dueDate(dto.getDueDate())
                .principal(dto.getPrincipalDue())
                .interest(dto.getInterestDue())
                .totalAmount(dto.getTotalDue())
                .status(status)
                .build();
    }

    private InstallmentDTO toInstallment(LoanInstallmentPlanDTO dto) {
        String status;
        if (Boolean.TRUE.equals(dto.getIsPaid())) {
            status = "PAID";
        } else if (dto.getDueDate() != null && dto.getDueDate().isBefore(LocalDate.now())) {
            status = "OVERDUE";
        } else {
            status = "SCHEDULED";
        }
        return InstallmentDTO.builder()
                .installmentId(dto.getLoanInstallmentPlanId())
                .installmentNumber(dto.getInstallmentNumber())
                .dueDate(dto.getDueDate())
                .amount(dto.getTotalDue())
                .status(status)
                .build();
    }

    private InstallmentPaymentDTO toInstallmentPayment(LoanInstallmentRecordDTO dto) {
        return InstallmentPaymentDTO.builder()
                .paymentId(dto.getLoanInstallmentRecordId())
                .amount(dto.getPaymentAmount())
                .paidAt(dto.getCreatedAt())
                .build();
    }

    private DisbursementDTO toDisbursement(LoanDisbursementDTO dto) {
        return DisbursementDTO.builder()
                .disbursementId(dto.getLoanDisbursementId())
                .amount(dto.getDisbursementAmount())
                .status(dto.getDisbursementStatus() != null ? dto.getDisbursementStatus().getValue() : null)
                .disbursedAt(dto.getDisbursementDate() != null ? dto.getDisbursementDate().atStartOfDay() : null)
                .build();
    }

    private RepaymentDTO toRepayment(LoanRepaymentRecordDTO dto) {
        return RepaymentDTO.builder()
                .repaymentId(dto.getLoanRepaymentRecordId())
                .amount(dto.getPaymentAmount())
                .type(Boolean.TRUE.equals(dto.getIsPartialPayment()) ? "EARLY" : "REGULAR")
                .appliedAt(dto.getPaymentDate() != null ? dto.getPaymentDate().atStartOfDay() : null)
                .build();
    }

    private RateChangeDTO toRateChange(LoanRateChangeDTO dto) {
        return RateChangeDTO.builder()
                .changeId(dto.getLoanRateChangeId())
                .previousRate(dto.getOldInterestRate())
                .newRate(dto.getNewInterestRate())
                .effectiveDate(dto.getEffectiveDate())
                .build();
    }

    private AccrualDTO toAccrual(LoanAccrualDTO dto) {
        return AccrualDTO.builder()
                .accrualId(dto.getLoanAccrualId())
                .period(dto.getAccrualDate() != null ? dto.getAccrualDate().toString() : null)
                .amount(dto.getAccrualAmount())
                .calculatedAt(dto.getCreatedAt())
                .build();
    }

    private RestructuringDTO toRestructuring(LoanRestructuringDTO dto) {
        return RestructuringDTO.builder()
                .restructuringId(dto.getLoanRestructuringId())
                .requestedAt(dto.getRestructuringDate() != null
                        ? dto.getRestructuringDate().atStartOfDay() : null)
                .newTerms(dto.getReason())
                .build();
    }

    private LoanEventDTO toEvent(LoanServicingEventDTO dto) {
        return LoanEventDTO.builder()
                .eventId(dto.getLoanServicingEventId())
                .eventType(dto.getEventType() != null ? dto.getEventType().getValue() : null)
                .description(dto.getDescription())
                .timestamp(dto.getCreatedAt())
                .build();
    }

    private List<Object> contentOf(Object page) {
        if (page == null) {
            return List.of();
        }
        Map<?, ?> map = objectMapper.convertValue(page, Map.class);
        Object content = map.get("content");
        if (content instanceof List<?> list) {
            return list.stream().map(item -> (Object) item).toList();
        }
        return List.of();
    }

    private DisbursementDTO toDisbursementFromObject(Object item) {
        return objectMapper.convertValue(item, DisbursementDTO.class);
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}

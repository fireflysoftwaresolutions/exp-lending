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


package io.fireflysoftware.experience.lending.core.product;

import io.fireflysoftware.domain.lending.loan.servicing.sdk.api.LoanServicingApi;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.CloseLoanCommand;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.EndPaymentHolidayCommand;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.GrantPaymentHolidayCommand;
import io.fireflysoftware.domain.lending.loan.servicing.sdk.model.SetFixedRateCommand;
import io.fireflysoftware.experience.lending.core.application.queries.ApplicationDetailDTO;
import io.fireflysoftware.experience.lending.core.ports.AccessAction;
import io.fireflysoftware.experience.lending.core.ports.AccessGuard;
import io.fireflysoftware.experience.lending.core.ports.AccessPort;
import io.fireflysoftware.experience.lending.core.ports.AccessRequest;
import io.fireflysoftware.experience.lending.core.ports.IdentityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Servicing a customer can do after a credit product exists.
 * Disbursement and installment payment use {@link PaymentsPort}.
 * Rate, close, and payment holiday call {@link LoanServicingApi}.
 * Agreement status, close, and cancel use the product domain when that saga exists.
 */
@Service("loanCustomerCommandService")
@RequiredArgsConstructor
public class LoanCustomerCommandServiceImpl {

    private final IdentityPort identityPort;
    private final AccessPort accessPort;
    private final LendingCatalogPort catalog;
    private final LoanServicingApi loanServicingApi;
    private final CreditProductPort creditProducts;
    private final PaymentsPort payments;
    private final ContractReadPort contracts;

    public Mono<Void> changeRate(UUID loanId, BigDecimal oldRate, BigDecimal newRate,
                                 LocalDate effectiveDate, RateChangeReason reason) {
        SetFixedRateCommand command = new SetFixedRateCommand()
                .loanServicingCaseId(loanId)
                .oldInterestRate(oldRate)
                .newInterestRate(newRate)
                .effectiveDate(effectiveDate)
                .reasonCode(SetFixedRateCommand.ReasonCodeEnum.fromValue(reason.name()));
        return loan(loanId, AccessAction.UPDATE, LoanCustomerOperation.RATE_CHANGE,
                () -> loanServicingApi.setFixedRate(loanId.toString(), command, key()).then());
    }

    public Mono<Void> closeLoan(UUID loanId, String reason) {
        CloseLoanCommand command = new CloseLoanCommand().reason(reason);
        return loan(loanId, AccessAction.CLOSE, LoanCustomerOperation.CLOSE,
                () -> loanServicingApi.closeLoan(loanId.toString(), command, key()).then());
    }

    public Mono<Void> grantPaymentHoliday(UUID loanId, String description) {
        GrantPaymentHolidayCommand command = new GrantPaymentHolidayCommand().description(description);
        return loan(loanId, AccessAction.UPDATE, LoanCustomerOperation.PAYMENT_HOLIDAY_GRANT,
                () -> loanServicingApi.grantPaymentHoliday(loanId.toString(), command, key()).then());
    }

    public Mono<Void> endPaymentHoliday(UUID loanId, String description) {
        EndPaymentHolidayCommand command = new EndPaymentHolidayCommand().description(description);
        return loan(loanId, AccessAction.UPDATE, LoanCustomerOperation.PAYMENT_HOLIDAY_END,
                () -> loanServicingApi.endPaymentHoliday(loanId.toString(), command, key()).then());
    }

    public Mono<PaymentReceipt> disburse(UUID loanId, BigDecimal amount) {
        return money(loanId, null, amount, LoanCustomerOperation.DISBURSE);
    }

    public Mono<PaymentReceipt> payInstallment(UUID loanId, UUID installmentId, BigDecimal amount) {
        return money(loanId, installmentId, amount, LoanCustomerOperation.PAY_INSTALLMENT);
    }

    public Mono<ContractView> readContract(UUID contractId) {
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(contractId, AccessAction.READ, "CONTRACT", contractId),
                () -> catalog.loanOperation(LoanCustomerOperation.READ_CONTRACT)
                        .then(contracts.get(contractId)));
    }

    public Mono<ApplicationDetailDTO> agreement(UUID productId, UUID agreementId,
                                                LoanCustomerOperation operation, Map<String, Object> body) {
        return catalog.routeFor(productId).flatMap(route ->
                AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                        AccessRequest.of(agreementId, action(operation), "APPLICATION", agreementId),
                        () -> creditProducts.command(route, agreementId, operation, body)));
    }

    private Mono<Void> loan(UUID loanId, AccessAction action, LoanCustomerOperation operation, java.util.function.Supplier<Mono<Void>> call) {
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(loanId, action, "APPLICATION", loanId),
                () -> catalog.loanOperation(operation).flatMap(path -> {
                    if ("payments".equals(path) || "contracts".equals(path)) {
                        return Mono.error(new IllegalStateException(operation.name() + " is not a loan saga"));
                    }
                    return call.get();
                }));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private Mono<PaymentReceipt> money(UUID loanId, UUID installmentId, BigDecimal amount, LoanCustomerOperation operation) {
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(loanId, AccessAction.CREATE, "APPLICATION", loanId),
                () -> catalog.loanOperation(operation).flatMap(token -> {
                    PaymentInstruction instruction = new PaymentInstruction(loanId, installmentId, amount,
                            operation == LoanCustomerOperation.DISBURSE ? PaymentKind.DISBURSEMENT : PaymentKind.INSTALLMENT);
                    return payments.tryPayment(instruction).flatMap(tried ->
                            payments.confirm(tried.paymentId(), tried.challengeValue()));
                }));
    }

    private static AccessAction action(LoanCustomerOperation operation) {
        return operation == LoanCustomerOperation.CLOSE || operation == LoanCustomerOperation.CANCEL
                ? AccessAction.CLOSE : AccessAction.UPDATE;
    }
}

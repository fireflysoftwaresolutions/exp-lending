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

import io.fireflysoftware.experience.lending.core.application.queries.ApplicationDetailDTO;
import io.fireflysoftware.experience.lending.core.product.ContractView;
import io.fireflysoftware.experience.lending.core.product.LoanCustomerCommandServiceImpl;
import io.fireflysoftware.experience.lending.core.product.LoanCustomerOperation;
import io.fireflysoftware.experience.lending.core.product.PaymentReceipt;
import io.fireflysoftware.experience.lending.core.product.RateChangeReason;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Servicing a customer performs after a credit product exists.
 * Disbursement and installment payment use the payments port.
 */
@RestController
@RequestMapping("/api/v1/experience/lending")
@RequiredArgsConstructor
public class LoanCustomerCommandController {

    private final LoanCustomerCommandServiceImpl commands;

    @PostMapping("/loans/{loanId}/rate")
    public Mono<Void> changeRate(@PathVariable UUID loanId, @RequestBody RateBody body) {
        return commands.changeRate(loanId, body.oldInterestRate(), body.newInterestRate(), body.effectiveDate(), body.reason());
    }

    @PostMapping("/loans/{loanId}/close")
    public Mono<Void> closeLoan(@PathVariable UUID loanId, @RequestBody ReasonBody body) {
        return commands.closeLoan(loanId, body.reason());
    }

    @PostMapping("/loans/{loanId}/payment-holiday")
    public Mono<Void> grantHoliday(@PathVariable UUID loanId, @RequestBody ReasonBody body) {
        return commands.grantPaymentHoliday(loanId, body.reason());
    }

    @PostMapping("/loans/{loanId}/payment-holiday/end")
    public Mono<Void> endHoliday(@PathVariable UUID loanId, @RequestBody ReasonBody body) {
        return commands.endPaymentHoliday(loanId, body.reason());
    }

    @PostMapping("/loans/{loanId}/disbursements")
    public Mono<PaymentReceipt> disburse(@PathVariable UUID loanId, @RequestBody AmountBody body) {
        return commands.disburse(loanId, body.amount());
    }

    @PostMapping("/loans/{loanId}/installments/{installmentId}/payments")
    public Mono<PaymentReceipt> payInstallment(@PathVariable UUID loanId, @PathVariable UUID installmentId,
                                                @RequestBody AmountBody body) {
        return commands.payInstallment(loanId, installmentId, body.amount());
    }

    @GetMapping("/contracts/{contractId}")
    public Mono<ContractView> readContract(@PathVariable UUID contractId) {
        return commands.readContract(contractId);
    }

    @PostMapping("/agreements/{agreementId}/commands/{operation}")
    public Mono<ApplicationDetailDTO> agreement(@PathVariable UUID agreementId,
                                                @PathVariable LoanCustomerOperation operation,
                                                @RequestParam UUID productId,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return commands.agreement(productId, agreementId, operation, body == null ? Map.of() : body);
    }

    public record RateBody(BigDecimal oldInterestRate, BigDecimal newInterestRate, LocalDate effectiveDate,
                           RateChangeReason reason) {
    }

    public record ReasonBody(String reason) {
    }

    public record AmountBody(BigDecimal amount) {
    }
}

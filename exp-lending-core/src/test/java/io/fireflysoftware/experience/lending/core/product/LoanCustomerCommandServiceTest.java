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
import io.fireflysoftware.experience.lending.core.application.commands.CreateApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.ports.LoanApplicationPort;
import io.fireflysoftware.experience.lending.core.application.queries.ApplicationDetailDTO;
import io.fireflysoftware.experience.lending.core.application.services.impl.ApplicationServiceImpl;
import io.fireflysoftware.experience.lending.core.ports.AccessDecision;
import io.fireflysoftware.experience.lending.core.ports.AccessPort;
import io.fireflysoftware.experience.lending.core.ports.CreditReportPort;
import io.fireflysoftware.experience.lending.core.ports.CreditReportView;
import io.fireflysoftware.experience.lending.core.ports.DecisionOutcome;
import io.fireflysoftware.experience.lending.core.ports.DecisionPort;
import io.fireflysoftware.experience.lending.core.ports.DecisionResult;
import io.fireflysoftware.experience.lending.core.ports.IdentityPort;
import io.fireflysoftware.experience.lending.core.ports.SubjectRef;
import io.fireflysoftware.experience.lending.core.product.PaymentPath;
import io.fireflysoftware.experience.lending.core.product.PaymentTry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanCustomerCommandServiceTest {

    @Mock private IdentityPort identityPort;
    @Mock private AccessPort accessPort;
    @Mock private LendingCatalogPort catalog;
    @Mock private LoanServicingApi loanServicingApi;
    @Mock private CreditProductPort creditProducts;
    @Mock private PaymentsPort payments;
    @Mock private ContractReadPort contracts;
    @Mock private LoanApplicationPort loanApplications;
    @Mock private CreditReportPort creditReports;
    @Mock private DecisionPort decisions;

    private LoanCustomerCommandServiceImpl servicing;
    private ApplicationServiceImpl applications;

    @BeforeEach
    void setUp() {
        servicing = new LoanCustomerCommandServiceImpl(identityPort, accessPort, catalog, loanServicingApi,
                creditProducts, payments, contracts);
        applications = new ApplicationServiceImpl(identityPort, accessPort, creditReports, decisions,
                loanApplications, catalog, creditProducts, parameters());
        org.mockito.Mockito.lenient().when(identityPort.resolve(any()))
                .thenReturn(Mono.just(new SubjectRef(IdentityPort.SAMPLE_ACTOR, "sandbox")));
        org.mockito.Mockito.lenient().when(accessPort.check(any()))
                .thenReturn(Mono.just(AccessDecision.permit("SANDBOX_GRANT")));
    }

    @Test
    void disburseUsesPaymentsPortAndNotTheLoanSaga() {
        UUID loanId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(catalog.loanOperation(LoanCustomerOperation.DISBURSE)).thenReturn(Mono.just("payments"));
        when(payments.tryPayment(any())).thenReturn(Mono.just(
                new PaymentTry(paymentId, PaymentKind.DISBURSEMENT, PaymentPath.INTERNAL, null, null)));
        when(payments.confirm(paymentId, null)).thenReturn(Mono.just(
                new PaymentReceipt(paymentId, PaymentKind.DISBURSEMENT, new BigDecimal("10"), "sandbox", true)));

        StepVerifier.create(servicing.disburse(loanId, new BigDecimal("10")))
                .expectNextCount(1)
                .verifyComplete();

        verify(payments).tryPayment(any());
        verify(payments).confirm(paymentId, null);
        verify(loanServicingApi, never()).setFixedRate(any(), any(), any());
        verify(loanServicingApi, never()).closeLoan(any(), any(), any());
    }

    @Test
    void changeRateHolidayAndCloseCallLoanServicingSdk() {
        UUID loanId = UUID.randomUUID();
        when(catalog.loanOperation(LoanCustomerOperation.RATE_CHANGE)).thenReturn(Mono.just("/api/v1/loans/{loanId}/rate"));
        when(catalog.loanOperation(LoanCustomerOperation.PAYMENT_HOLIDAY_GRANT)).thenReturn(Mono.just("/api/v1/loans/{loanId}/payment-holiday/grant"));
        when(catalog.loanOperation(LoanCustomerOperation.PAYMENT_HOLIDAY_END)).thenReturn(Mono.just("/api/v1/loans/{loanId}/payment-holiday/end"));
        when(catalog.loanOperation(LoanCustomerOperation.CLOSE)).thenReturn(Mono.just("/api/v1/loans/{loanId}/close"));
        when(loanServicingApi.setFixedRate(eq(loanId.toString()), any(), any())).thenReturn(Mono.just(loanId));
        when(loanServicingApi.grantPaymentHoliday(eq(loanId.toString()), any(), any())).thenReturn(Mono.just(loanId));
        when(loanServicingApi.endPaymentHoliday(eq(loanId.toString()), any(), any())).thenReturn(Mono.just(loanId));
        when(loanServicingApi.closeLoan(eq(loanId.toString()), any(), any())).thenReturn(Mono.just(loanId));

        StepVerifier.create(servicing.changeRate(loanId, new BigDecimal("1.00"), new BigDecimal("2.00"),
                        LocalDate.of(2026, 10, 21), RateChangeReason.RENEGOTIATION))
                .verifyComplete();
        StepVerifier.create(servicing.grantPaymentHoliday(loanId, "holiday")).verifyComplete();
        StepVerifier.create(servicing.endPaymentHoliday(loanId, "resume")).verifyComplete();
        StepVerifier.create(servicing.closeLoan(loanId, "paid")).verifyComplete();

        verify(loanServicingApi).setFixedRate(eq(loanId.toString()), any(), any());
        verify(loanServicingApi).grantPaymentHoliday(eq(loanId.toString()), any(), any());
        verify(loanServicingApi).endPaymentHoliday(eq(loanId.toString()), any(), any());
        verify(loanServicingApi).closeLoan(eq(loanId.toString()), any(), any());
        verify(payments, never()).tryPayment(any());
    }

    @Test
    void createApplicationPassesParameterValueAndRoutesMortgage() {
        UUID productId = UUID.fromString("10000000-0000-4000-8000-000000000201");
        when(creditReports.request(any())).thenReturn(Mono.just(
                new CreditReportView("sandbox", "r", 640, "ILLUSTRATIVE", true)));
        when(decisions.decide(any())).thenReturn(Mono.just(new DecisionResult(DecisionOutcome.APPROVE, List.of())));
        when(catalog.routeFor(productId)).thenReturn(Mono.just(new ProductRoute(
                CreditProductRoute.MORTGAGE, "/api/v1/mortgage-agreements", "mortgageAgreementId",
                Map.of("mortgageType", "CONVENTIONAL"), Map.of())));
        when(creditProducts.open(any(), any())).thenReturn(Mono.just(ApplicationDetailDTO.builder()
                .applicationId(UUID.randomUUID()).productType(CreditProductRoute.MORTGAGE.name()).build()));

        CreateApplicationCommand command = new CreateApplicationCommand();
        command.setProductId(productId);
        command.setPartyId(UUID.randomUUID());
        command.setRequestedAmount(new BigDecimal("1000"));
        command.setTerm(12);

        StepVerifier.create(applications.createApplication(command))
                .expectNextCount(1)
                .verifyComplete();

        verify(decisions).decide(org.mockito.ArgumentMatchers.argThat(request ->
                "701".equals(request.attributes().get("minCreditScore"))));
        verify(loanApplications, never()).createApplication(any());
        verify(creditProducts).open(any(), eq(command));
    }

    private static BusinessParameterPort parameters() {
        return key -> {
            assertThat(key).isEqualTo(ApplicationServiceImpl.MIN_CREDIT_SCORE_KEY);
            return Mono.just("701");
        };
    }
}

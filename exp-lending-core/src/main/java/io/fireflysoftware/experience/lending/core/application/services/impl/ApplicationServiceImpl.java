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


package io.fireflysoftware.experience.lending.core.application.services.impl;

import io.fireflysoftware.domain.lending.loan.origination.sdk.model.ApplicationPartyDTO;
import io.fireflysoftware.experience.lending.core.application.commands.CreateApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.commands.UpdateApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.commands.UpdateEmploymentDataCommand;
import io.fireflysoftware.experience.lending.core.application.ports.LoanApplicationPort;
import io.fireflysoftware.experience.lending.core.application.queries.ApplicationDetailDTO;
import io.fireflysoftware.experience.lending.core.application.queries.ApplicationStatusHistoryDTO;
import io.fireflysoftware.experience.lending.core.application.queries.ApplicationSummaryDTO;
import io.fireflysoftware.experience.lending.core.application.services.ApplicationService;
import io.fireflysoftware.experience.lending.core.ports.AccessAction;
import io.fireflysoftware.experience.lending.core.ports.AccessGuard;
import io.fireflysoftware.experience.lending.core.ports.IdentityPort;
import io.fireflysoftware.experience.lending.core.ports.AccessPort;
import io.fireflysoftware.experience.lending.core.ports.AccessRequest;
import io.fireflysoftware.experience.lending.core.ports.CreditReportPort;
import io.fireflysoftware.experience.lending.core.ports.CreditReportQuery;
import io.fireflysoftware.experience.lending.core.ports.DecisionPort;
import io.fireflysoftware.experience.lending.core.ports.DecisionRequest;
import io.fireflysoftware.experience.lending.core.ports.JourneyStep;
import io.fireflysoftware.experience.lending.core.ports.RiskStep;
import io.fireflysoftware.experience.lending.core.product.BusinessParameterPort;
import io.fireflysoftware.experience.lending.core.product.CreditProductPort;
import io.fireflysoftware.experience.lending.core.product.CreditProductRoute;
import io.fireflysoftware.experience.lending.core.product.LendingCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Lending application journey. Order: access, local sandbox credit report, decision service
 * ({@code POST /api/v1/decisions}, step {@code LENDING_APPLICATION}), then loan origination.
 * A {@code DECLINE} does not call a domain. The catalog chooses the domain port for the product.
 */
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final IdentityPort identityPort;
    private final AccessPort accessPort;
    private final CreditReportPort creditReportPort;
    private final DecisionPort decisionPort;
    private final LoanApplicationPort loanApplicationPort;
    private final LendingCatalogPort lendingCatalog;
    private final CreditProductPort creditProductPort;
    private final BusinessParameterPort businessParameters;

    public static final String MIN_CREDIT_SCORE_KEY = "journey.lending.min-credit-score";

    @Override
    public Mono<ApplicationDetailDTO> createApplication(CreateApplicationCommand command) {
        if (command == null || command.getPartyId() == null) {
            return Mono.error(new org.fireflyframework.web.error.exceptions.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "partyId is required"));
        }
        UUID partyId = command.getPartyId();
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(partyId, AccessAction.CREATE, "APPLICATION", null),
                () -> businessParameters.findByKey(MIN_CREDIT_SCORE_KEY).flatMap(minimum ->
                        RiskStep.afterAccess(
                                creditReportPort,
                                decisionPort,
                                new CreditReportQuery(partyId, command.getProductId(), JourneyStep.LENDING_APPLICATION, Map.of()),
                                new DecisionRequest(partyId, command.getProductId(), JourneyStep.LENDING_APPLICATION, Map.of())
                                        .withAttribute("minCreditScore", minimum),
                                () -> lendingCatalog.routeFor(command.getProductId()).flatMap(route ->
                                        route.route() == CreditProductRoute.LOAN_ORIGINATION
                                                ? loanApplicationPort.createApplication(command)
                                                : creditProductPort.open(route, command)))));
    }

    @Override
    public Mono<ApplicationDetailDTO> getApplication(UUID applicationId) {
        return read(applicationId, () -> loanApplicationPort.getApplication(applicationId));
    }

    @Override
    public Mono<ApplicationDetailDTO> updateApplication(UUID applicationId, UpdateApplicationCommand command) {
        return update(applicationId, () -> loanApplicationPort.updateApplication(applicationId, command));
    }

    @Override
    public Mono<Void> submitApplication(UUID applicationId) {
        return update(applicationId, () -> loanApplicationPort.submitApplication(applicationId));
    }

    @Override
    public Mono<Void> withdrawApplication(UUID applicationId) {
        return update(applicationId, () -> loanApplicationPort.withdrawApplication(applicationId));
    }

    @Override
    public Mono<ApplicationStatusHistoryDTO> getStatusHistory(UUID applicationId) {
        return read(applicationId, () -> loanApplicationPort.getStatusHistory(applicationId));
    }

    @Override
    public Mono<ApplicationPartyDTO> updateEmploymentData(UUID applicationId, UpdateEmploymentDataCommand command) {
        return update(applicationId, () -> loanApplicationPort.updateEmploymentData(applicationId, command));
    }

    private <T> Mono<T> read(UUID applicationId, java.util.function.Supplier<Mono<T>> call) {
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(applicationId, AccessAction.READ, "APPLICATION", applicationId), call);
    }

    private <T> Mono<T> update(UUID applicationId, java.util.function.Supplier<Mono<T>> call) {
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(applicationId, AccessAction.UPDATE, "APPLICATION", applicationId), call);
    }
}

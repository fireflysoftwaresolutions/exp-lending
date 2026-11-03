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

import io.fireflysoftware.domain.lending.assetfinance.sdk.api.AssetFinanceApi;
import io.fireflysoftware.domain.lending.assetfinance.sdk.model.AssetFinanceAgreementDTO;
import io.fireflysoftware.domain.lending.mortgage.sdk.api.MortgageAgreementsApi;
import io.fireflysoftware.domain.lending.mortgage.sdk.model.MortgageAgreementDTO;
import io.fireflysoftware.domain.lending.personalloans.sdk.api.PersonalLoansApi;
import io.fireflysoftware.domain.lending.personalloans.sdk.model.PersonalLoanAgreementDTO;
import io.fireflysoftware.domain.lending.supplychainfinance.sdk.api.SupplyChainFinanceAgreementsApi;
import io.fireflysoftware.domain.lending.supplychainfinance.sdk.model.SupplyChainFinanceAgreementDTO;
import io.fireflysoftware.experience.lending.core.application.commands.CreateApplicationCommand;
import io.fireflysoftware.experience.lending.core.application.queries.ApplicationDetailDTO;
import io.fireflysoftware.experience.lending.core.product.CreditProductPort;
import io.fireflysoftware.experience.lending.core.product.CreditProductRoute;
import io.fireflysoftware.experience.lending.core.product.LoanCustomerOperation;
import io.fireflysoftware.experience.lending.core.product.ProductRoute;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Opens and services a credit product through the domain SDK for that product kind.
 */
public final class SdkCreditProductClientImpl implements CreditProductPort {

    private final MortgageAgreementsApi mortgages;
    private final PersonalLoansApi personalLoans;
    private final AssetFinanceApi assetFinance;
    private final SupplyChainFinanceAgreementsApi supplyChain;

    public SdkCreditProductClientImpl(MortgageAgreementsApi mortgages,
                                   PersonalLoansApi personalLoans,
                                   AssetFinanceApi assetFinance,
                                   SupplyChainFinanceAgreementsApi supplyChain) {
        this.mortgages = mortgages;
        this.personalLoans = personalLoans;
        this.assetFinance = assetFinance;
        this.supplyChain = supplyChain;
    }

    @Override
    public Mono<ApplicationDetailDTO> open(ProductRoute route, CreateApplicationCommand command) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(route.attributes());
        if (command.getPartyId() != null) {
            body.putIfAbsent("applicationId", command.getPartyId());
        }
        if (command.getRequestedAmount() != null) {
            body.putIfAbsent("totalValue", command.getRequestedAmount());
            body.putIfAbsent("purchasePrice", command.getRequestedAmount());
        }
        validate(route.route(), body);
        String key = UUID.randomUUID().toString();
        return switch (route.route()) {
            case MORTGAGE -> mortgages.create(mortgage(body), key)
                    .map(dto -> detail(dto.getMortgageAgreementId(), route, value(dto.getAgreementStatus()), command));
            case PERSONAL_LOAN -> personalLoans.createAgreement(personalLoan(body), key)
                    .map(dto -> detail(dto.getPersonalLoanAgreementId(), route, value(dto.getAgreementStatus()), command));
            case ASSET_FINANCE -> assetFinance.createAgreement(asset(body), key)
                    .map(dto -> detail(dto.getAssetFinanceAgreementId(), route, value(dto.getAgreementStatus()), command));
            case SUPPLY_CHAIN_FINANCE -> supplyChain.create(supply(body), key)
                    .map(dto -> detail(dto.getId(), route, value(dto.getStatus()), command));
            case LOAN_ORIGINATION -> Mono.error(new BusinessException(HttpStatus.CONFLICT, "ROUTE_NOT_CONFIGURED",
                    "Loan origination uses its own port"));
        };
    }

    @Override
    public Mono<ApplicationDetailDTO> command(ProductRoute route, UUID agreementId,
                                              LoanCustomerOperation operation, Map<String, Object> body) {
        if (route.operation(operation) == null) {
            return Mono.error(new BusinessException(HttpStatus.CONFLICT, "JOURNEY_NOT_ENABLED",
                    operation.name() + " is not enabled for " + route.route().name()));
        }
        Map<String, Object> payload = body == null ? Map.of() : body;
        String key = UUID.randomUUID().toString();
        return switch (route.route()) {
            case MORTGAGE -> mortgageCommand(agreementId, operation, payload, key)
                    .map(dto -> detail(agreementId, route, value(dto.getAgreementStatus()), null));
            case PERSONAL_LOAN -> personalCommand(agreementId, operation, payload, key)
                    .map(dto -> detail(agreementId, route, value(dto.getAgreementStatus()), null));
            case ASSET_FINANCE -> assetCommand(agreementId, operation, payload, key)
                    .map(dto -> detail(agreementId, route, value(dto.getAgreementStatus()), null));
            case SUPPLY_CHAIN_FINANCE -> supplyCommand(agreementId, operation, payload, key)
                    .map(dto -> detail(agreementId, route, value(dto.getStatus()), null));
            case LOAN_ORIGINATION -> Mono.error(new BusinessException(HttpStatus.CONFLICT, "ROUTE_NOT_CONFIGURED",
                    "Loan origination uses its own port"));
        };
    }

    private Mono<MortgageAgreementDTO> mortgageCommand(UUID agreementId, LoanCustomerOperation operation,
                                                       Map<String, Object> body, String key) {
        return switch (operation) {
            case STATUS -> mortgages.updateStatus(agreementId, mortgageStatus(body), key);
            case CLOSE -> mortgages.close(agreementId, mortgageReason(body), key);
            case CANCEL -> mortgages.cancel(agreementId, mortgageReason(body), key);
            case UPDATE -> mortgages.update(agreementId, mortgage(body), key);
            default -> Mono.error(new BusinessException(HttpStatus.CONFLICT, "JOURNEY_NOT_ENABLED",
                    operation.name() + " is not a mortgage agreement command"));
        };
    }

    private Mono<PersonalLoanAgreementDTO> personalCommand(UUID agreementId, LoanCustomerOperation operation,
                                                           Map<String, Object> body, String key) {
        if (operation == LoanCustomerOperation.UPDATE) {
            return personalLoans.updateAgreement(agreementId, personalLoan(body), key);
        }
        return Mono.error(new BusinessException(HttpStatus.CONFLICT, "JOURNEY_NOT_ENABLED",
                operation.name() + " is not a personal-loan agreement command"));
    }

    private Mono<AssetFinanceAgreementDTO> assetCommand(UUID agreementId, LoanCustomerOperation operation,
                                                        Map<String, Object> body, String key) {
        return switch (operation) {
            case STATUS -> assetFinance.updateAgreementStatus(agreementId, assetStatus(body), key);
            case CLOSE -> assetFinance.closeAgreement(agreementId, assetReason(body), key);
            case UPDATE -> assetFinance.updateAgreement(agreementId, asset(body), key);
            default -> Mono.error(new BusinessException(HttpStatus.CONFLICT, "JOURNEY_NOT_ENABLED",
                    operation.name() + " is not an asset-finance agreement command"));
        };
    }

    private Mono<SupplyChainFinanceAgreementDTO> supplyCommand(UUID agreementId, LoanCustomerOperation operation,
                                                               Map<String, Object> body, String key) {
        return switch (operation) {
            case STATUS -> supplyChain.updateStatus(agreementId, supplyStatus(body), key);
            case CLOSE -> supplyChain.close(agreementId, supplyReason(body), key);
            case CANCEL -> supplyChain.cancel(agreementId, supplyReason(body), key);
            case UPDATE -> supplyChain.update(agreementId, supply(body), key);
            default -> Mono.error(new BusinessException(HttpStatus.CONFLICT, "JOURNEY_NOT_ENABLED",
                    operation.name() + " is not a supply-chain agreement command"));
        };
    }

    private static MortgageAgreementDTO mortgage(Map<String, Object> body) {
        MortgageAgreementDTO dto = new MortgageAgreementDTO()
                .agreementStatus(MortgageAgreementDTO.AgreementStatusEnum.fromValue(text(body.get("agreementStatus"))))
                .applicationId(uuid(body.get("applicationId")))
                .mortgageType(MortgageAgreementDTO.MortgageTypeEnum.fromValue(text(body.get("mortgageType"))))
                .rateType(MortgageAgreementDTO.RateTypeEnum.fromValue(text(body.get("rateType"))));
        BigDecimal purchasePrice = decimal(body.get("purchasePrice"));
        if (purchasePrice != null) {
            dto.purchasePrice(purchasePrice);
        }
        return dto;
    }

    private static PersonalLoanAgreementDTO personalLoan(Map<String, Object> body) {
        return new PersonalLoanAgreementDTO()
                .agreementStatus(PersonalLoanAgreementDTO.AgreementStatusEnum.fromValue(text(body.get("agreementStatus"))))
                .applicationId(uuid(body.get("applicationId")))
                .loanPurpose(PersonalLoanAgreementDTO.LoanPurposeEnum.fromValue(text(body.get("loanPurpose"))))
                .rateType(PersonalLoanAgreementDTO.RateTypeEnum.fromValue(text(body.get("rateType"))));
    }

    private static AssetFinanceAgreementDTO asset(Map<String, Object> body) {
        return new AssetFinanceAgreementDTO()
                .agreementStatus(AssetFinanceAgreementDTO.AgreementStatusEnum.fromValue(text(body.get("agreementStatus"))))
                .financeType(AssetFinanceAgreementDTO.FinanceTypeEnum.fromValue(text(body.get("financeType"))))
                .paymentFrequency(text(body.get("paymentFrequency")))
                .startDate(date(body.get("startDate")))
                .endDate(date(body.get("endDate")))
                .totalValue(decimal(body.get("totalValue")));
    }

    private static SupplyChainFinanceAgreementDTO supply(Map<String, Object> body) {
        return new SupplyChainFinanceAgreementDTO()
                .agreementNumber(text(body.get("agreementNumber")))
                .currency(text(body.get("currency")))
                .financeType(SupplyChainFinanceAgreementDTO.FinanceTypeEnum.fromValue(text(body.get("financeType"))))
                .loanServicingCaseId(uuid(body.get("loanServicingCaseId")))
                .startDate(date(body.get("startDate")))
                .status(SupplyChainFinanceAgreementDTO.StatusEnum.fromValue(text(body.get("status"))));
    }

    private static io.fireflysoftware.domain.lending.mortgage.sdk.model.StatusChangeRequest mortgageStatus(Map<String, Object> body) {
        return new io.fireflysoftware.domain.lending.mortgage.sdk.model.StatusChangeRequest()
                .status(firstText(body, "status", "agreementStatus"))
                .statusReason(firstText(body, "statusReason", "reason"));
    }

    private static io.fireflysoftware.domain.lending.mortgage.sdk.model.ReasonRequest mortgageReason(Map<String, Object> body) {
        return new io.fireflysoftware.domain.lending.mortgage.sdk.model.ReasonRequest()
                .statusReason(firstText(body, "statusReason", "reason"));
    }

    private static io.fireflysoftware.domain.lending.assetfinance.sdk.model.StatusChangeRequest assetStatus(Map<String, Object> body) {
        return new io.fireflysoftware.domain.lending.assetfinance.sdk.model.StatusChangeRequest()
                .status(firstText(body, "status", "agreementStatus"))
                .statusReason(firstText(body, "statusReason", "reason"));
    }

    private static io.fireflysoftware.domain.lending.assetfinance.sdk.model.ReasonRequest assetReason(Map<String, Object> body) {
        return new io.fireflysoftware.domain.lending.assetfinance.sdk.model.ReasonRequest()
                .statusReason(firstText(body, "statusReason", "reason"));
    }

    private static io.fireflysoftware.domain.lending.supplychainfinance.sdk.model.StatusChangeRequest supplyStatus(Map<String, Object> body) {
        return new io.fireflysoftware.domain.lending.supplychainfinance.sdk.model.StatusChangeRequest()
                .status(firstText(body, "status", "agreementStatus"))
                .statusReason(firstText(body, "statusReason", "reason"));
    }

    private static io.fireflysoftware.domain.lending.supplychainfinance.sdk.model.ReasonRequest supplyReason(Map<String, Object> body) {
        return new io.fireflysoftware.domain.lending.supplychainfinance.sdk.model.ReasonRequest()
                .statusReason(firstText(body, "statusReason", "reason"));
    }

    private static ApplicationDetailDTO detail(UUID id, ProductRoute route, String status, CreateApplicationCommand command) {
        ApplicationDetailDTO.ApplicationDetailDTOBuilder builder = ApplicationDetailDTO.builder()
                .applicationId(id)
                .productType(route.route().name())
                .status(status);
        if (command != null) {
            builder.requestedAmount(command.getRequestedAmount())
                    .term(command.getTerm())
                    .purpose(command.getPurpose());
        }
        return builder.build();
    }

    private static String value(Enum<?> status) {
        if (status == null) {
            return null;
        }
        try {
            return String.valueOf(status.getClass().getMethod("getValue").invoke(status));
        } catch (ReflectiveOperationException ex) {
            return status.name();
        }
    }

    private static void validate(CreditProductRoute route, Map<String, Object> body) {
        switch (route) {
            case MORTGAGE -> {
                require(body, "agreementStatus", "applicationId", "mortgageType", "rateType");
                MortgageAgreementDTO.MortgageTypeEnum.fromValue(text(body.get("mortgageType")));
                MortgageAgreementDTO.RateTypeEnum.fromValue(text(body.get("rateType")));
            }
            case PERSONAL_LOAN -> {
                require(body, "agreementStatus", "applicationId", "loanPurpose", "rateType");
                PersonalLoanAgreementDTO.LoanPurposeEnum.fromValue(text(body.get("loanPurpose")));
                PersonalLoanAgreementDTO.RateTypeEnum.fromValue(text(body.get("rateType")));
            }
            case ASSET_FINANCE -> {
                require(body, "agreementStatus", "endDate", "financeType", "paymentFrequency", "startDate", "totalValue");
                AssetFinanceAgreementDTO.FinanceTypeEnum.fromValue(text(body.get("financeType")));
            }
            case SUPPLY_CHAIN_FINANCE -> {
                require(body, "agreementNumber", "currency", "financeType", "loanServicingCaseId", "startDate", "status");
                SupplyChainFinanceAgreementDTO.FinanceTypeEnum.fromValue(text(body.get("financeType")));
            }
            case LOAN_ORIGINATION -> throw new BusinessException(HttpStatus.CONFLICT, "ROUTE_NOT_CONFIGURED",
                    "Loan origination uses its own port");
        }
    }

    private static void require(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.get(key) == null || String.valueOf(body.get(key)).isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", key + " is required");
            }
        }
    }

    private static UUID uuid(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        return UUID.fromString(value.toString());
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.get(key) != null && !body.get(key).toString().isBlank()) {
                return body.get(key).toString();
            }
        }
        return null;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        return new BigDecimal(value.toString());
    }

    private static LocalDate date(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }
}

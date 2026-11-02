<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Experience - Lending" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Experience - Lending

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

> Backend-for-Frontend service for the lending portal — covers loan simulation, application lifecycle, underwriting decisions, contract signing, active loan servicing, personal loans, asset finance, and collections

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Module Structure](#module-structure)
- [Functional Verticals](#functional-verticals)
- [API Endpoints](#api-endpoints)
- [Domain SDK Dependencies](#domain-sdk-dependencies)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Testing](#testing)

## Simulation availability

The two simulation routes currently return `503 LENDING_TERMS_UNAVAILABLE` after existing validation. They do not register administrative pricing or persist an unverified calculation. Eligibility remains a separate operation. Exact, authorized product terms and currency-policy references are required before numerical simulations can be enabled. See [Simulation availability](docs/simulation-availability.md) for the response contract and integration dependency.

## Overview

`exp-lending` is the experience-layer service that powers all lending-related screens across the digital channel. It covers the full credit lifecycle — from the initial product simulation and eligibility check, through multi-step application management, underwriting decision retrieval, offer selection, SCA-protected contract signing, post-disbursement loan servicing, and asset finance agreement management, down to collections case handling.

The service uses **simple stateless composition** throughout. Every endpoint delegates to one or more downstream domain SDK calls, maps the result to an experience-layer DTO, and returns. There is no `@Workflow`, no Redis, and no persistent journey state within this service. The loan application lifecycle (create, update, submit, withdraw) is managed as individual independent SDK calls to the loan origination domain service, with each state transition owned by the domain layer.

Given the breadth of the lending domain, the `-core` module is organised by functional vertical (simulation, origination, servicing, personal loans, asset finance, collections), each with its own package subtree containing service interfaces, implementations, command DTOs, and query DTOs. Eleven controllers in the `-web` module expose these verticals over REST, split by resource type to keep each controller focused and maintainable.

## Architecture

```
Frontend / Mobile App
         |
         v
exp-lending  (port 8102)
         |
         +---> domain-lending-loan-origination-sdk  (application lifecycle)
         |
         +---> domain-lending-loan-servicing-sdk    (active loans, installments, repayments,
         |                                           disbursements, escrow, accruals, rebates,
         |                                           restructuring, early repayment)
         |
         +---> domain-lending-credit-scoring-sdk    (scoring status, underwriting decision)
         |
         +---> domain-lending-asset-finance-sdk     (leasing/renting agreements, assets,
         |                                           deliveries, returns, pickups, service
         |                                           events, usage, end options)
         |
         +---> domain-lending-personal-loans-sdk    (personal loan agreement management)
         |
         +---> domain-product-catalog-sdk           (product definitions)
         |
         +---> domain-product-pricing-sdk           (loan simulation, eligibility)
         |
         +---> domain-common-contracts-sdk          (contract retrieval and signing)
         |
         +---> domain-common-sca-sdk                (SCA challenge for contract signing)
         |
         +---> domain-common-notifications-sdk      (loan-related notification history)
```

## Module Structure

| Module | Purpose |
|--------|---------|
| `exp-lending-interfaces` | Reserved for future shared contracts |
| `exp-lending-core` | Service interfaces and implementations per vertical, command and query DTOs, organised by `simulation/`, `application/`, `servicing/`, `personalloans/`, `assetfinance/`, and `collections/` sub-packages |
| `exp-lending-infra` | `ClientFactory` beans and `@ConfigurationProperties` for each downstream SDK |
| `exp-lending-web` | 11 REST controllers (one per vertical), Spring Boot application class, `application.yaml` |
| `exp-lending-sdk` | Auto-generated reactive SDK from the OpenAPI spec |

## Functional Verticals

| Vertical | Controller | Base Path | Endpoints |
|----------|-----------|-----------|-----------|
| Simulation & Eligibility | `SimulationController` | `/api/v1/experience/lending` | 3 |
| Application Lifecycle | `ApplicationController` | `/api/v1/experience/lending/applications` | 7 |
| Application Details | `ApplicationDetailsController` | `/api/v1/experience/lending/applications/{id}` | 5 |
| Application Documents | `ApplicationDocumentsController` | `/api/v1/experience/lending/applications/{id}/documents` | 4 |
| Application Parties | `ApplicationPartiesController` | `/api/v1/experience/lending/applications/{id}/parties` | 5 |
| Decision & Contract | `DecisionController` | `/api/v1/experience/lending/applications/{id}` | 8 |
| Disbursement Account | `DisbursementAccountController` | `/api/v1/experience/lending/applications/{id}` | 5 |
| Active Loans | `LoanServicingController` | `/api/v1/experience/lending/loans` | 24 |
| Personal Loans | `PersonalLoansController` | `/api/v1/experience/lending/personal-loans` | 4 |
| Asset Finance | `AssetFinanceController` | `/api/v1/experience/lending/asset-finance` | 15 |
| Collections | `CollectionsController` | `/api/v1/experience/lending/collections` | 3 |

**Total: 83 endpoints across 11 controllers.**

## API Endpoints

### Simulation & Eligibility

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/experience/lending/simulations` | Requires verified owner terms; performs no pricing write | `503 LENDING_TERMS_UNAVAILABLE` |
| `POST` | `/api/v1/experience/lending/simulations/configure` | Retains input validation; performs no calculation or persistence | `400 Bad Request` or `503 LENDING_TERMS_UNAVAILABLE` |
| `POST` | `/api/v1/experience/lending/eligibility` | Evaluate whether a party is eligible for a given product and requested amount | `200 OK` |

### Application Lifecycle

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/experience/lending/applications` | Create a new loan application | `201 Created` |
| `GET` | `/api/v1/experience/lending/applications` | List all loan applications accessible to the caller | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}` | Retrieve the full details of a loan application | `200 OK` |
| `PATCH` | `/api/v1/experience/lending/applications/{id}` | Update editable fields on a loan application | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/submission` | Submit the application for underwriting review | `202 Accepted` |
| `POST` | `/api/v1/experience/lending/applications/{id}/withdraw` | Withdraw the application at the applicant's request | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/status-history` | Return the full status transition history for the application | `200 OK` |

### Application Details

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/applications/{id}/conditions` | List all conditions associated with the application | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/tasks` | List all tasks associated with the application | `200 OK` |
| `PATCH` | `/api/v1/experience/lending/applications/{id}/tasks/{taskId}` | Mark an application task as COMPLETED | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/fees` | List all fees associated with the application | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/verifications` | List all verifications associated with the application | `200 OK` |

### Application Documents

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/applications/{id}/documents` | List all documents attached to the application | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/documents` | Attach a new document to the application | `201 Created` |
| `GET` | `/api/v1/experience/lending/applications/{id}/documents/{docId}` | Download the binary content of an application document | `200 OK` |
| `DELETE` | `/api/v1/experience/lending/applications/{id}/documents/{docId}` | Remove a document from the application | `204 No Content` |

### Application Parties

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/applications/{id}/parties` | List all parties associated with the application | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/parties` | Associate a new party (co-holder, guarantor, etc.) with the application | `201 Created` |
| `GET` | `/api/v1/experience/lending/applications/{id}/parties/{partyId}` | Retrieve details of a specific application party | `200 OK` |
| `PUT` | `/api/v1/experience/lending/applications/{id}/parties/{partyId}` | Update a party's information on the application | `200 OK` |
| `DELETE` | `/api/v1/experience/lending/applications/{id}/parties/{partyId}` | Remove a party from the application | `204 No Content` |

### Decision & Contract

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/applications/{id}/scoring-status` | Return the current credit scoring status (PENDING → IN_PROGRESS → COMPLETED) | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/decision` | Return the underwriting decision (APPROVED, REJECTED, or CONDITIONAL) with reason codes | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/offers` | List all proposed loan offers for the application | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/offers/{offerId}` | Return the full detail of a specific offer including total cost, itemised fees, and attached conditions | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/offers/{offerId}/accept` | Mark the specified offer as ACCEPTED | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/offers/{offerId}/reject` | Mark the specified offer as REJECTED with an optional reason | `200 OK` |
| `GET` | `/api/v1/experience/lending/applications/{id}/contract` | Return the loan contract associated with the application, including its current status | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/contract/sign` | Initiate SCA flow for contract signing and transition the contract to ACTIVE status | `200 OK` |

### Disbursement Account

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/applications/{id}/disbursement-account` | Return the configured disbursement account for the application | `200 OK` |
| `PUT` | `/api/v1/experience/lending/applications/{id}/disbursement-account` | Set the disbursement account (internal or external) for the application | `200 OK` |
| `POST` | `/api/v1/experience/lending/applications/{id}/external-accounts` | Register a new external bank account for disbursement | `201 Created` |
| `GET` | `/api/v1/experience/lending/applications/{id}/external-accounts` | List all external bank accounts registered for the application | `200 OK` |
| `DELETE` | `/api/v1/experience/lending/applications/{id}/external-accounts/{accId}` | Remove an external bank account from the application | `204 No Content` |

### Active Loans

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/loans` | List all active loan servicing cases accessible to the caller | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}` | Retrieve full details of an active loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/balance` | Return the current outstanding balance for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/balance/history` | Return the full balance snapshot history for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/schedule` | Return the full amortisation schedule for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/installments` | List all installment plan entries for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/installments/{instId}` | Return a single installment plan entry | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/installments/{instId}/payments` | Return payment records linked to a specific installment | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/disbursements` | List all disbursement records for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/disbursements/{dId}` | Return a single disbursement record | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/disbursement-plan` | Return the disbursement plan for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/repayments` | List all repayment records for the loan | `200 OK` |
| `POST` | `/api/v1/experience/lending/loans/{id}/early-repayment` | Register a partial or full early repayment against the loan | `202 Accepted` |
| `POST` | `/api/v1/experience/lending/loans/{id}/early-repayment/simulation` | Return a cost breakdown for a hypothetical early repayment | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/rate-info` | Return the current interest rate and type for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/rate-changes` | Return the history of interest rate changes for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/accruals` | List all interest accrual records for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/escrow` | List all escrow accounts associated with the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/rebates` | List all rebate records applied to the loan | `200 OK` |
| `POST` | `/api/v1/experience/lending/loans/{id}/restructuring` | Submit a restructuring request for the loan | `202 Accepted` |
| `GET` | `/api/v1/experience/lending/loans/{id}/restructurings` | Return the history of restructuring requests for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/documents` | List all documents attached to the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/events` | Return the servicing event log for the loan | `200 OK` |
| `GET` | `/api/v1/experience/lending/loans/{id}/notifications` | List all notifications sent in connection with the loan | `200 OK` |

### Personal Loans

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/experience/lending/personal-loans` | Create a new personal loan agreement | `201 Created` |
| `GET` | `/api/v1/experience/lending/personal-loans` | List all personal loan agreements for the current customer | `200 OK` |
| `GET` | `/api/v1/experience/lending/personal-loans/{agreementId}` | Retrieve full details of a personal loan agreement | `200 OK` / `404 Not Found` |
| `PUT` | `/api/v1/experience/lending/personal-loans/{agreementId}` | Update an existing personal loan agreement | `200 OK` / `404 Not Found` |

### Asset Finance

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/asset-finance` | List all asset finance agreements (leasing and renting) | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}` | Return full details of a single asset finance agreement | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets` | List all financed assets associated with the agreement | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}` | Return details of a single financed asset | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/deliveries` | List all delivery records for the asset | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/deliveries/{dId}` | Return details of a single delivery record | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/returns` | List all return records for the asset | `200 OK` |
| `POST` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/returns` | Initiate a return process for the asset | `201 Created` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/pickups` | List all pickup records for the asset | `200 OK` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/service-events` | List all service events (maintenance, damage, inspection) for the asset | `200 OK` |
| `POST` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/service-events` | Record a new service event for the asset | `201 Created` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/usage` | List all usage records (mileage snapshots) for the asset | `200 OK` |
| `POST` | `/api/v1/experience/lending/asset-finance/{id}/assets/{assetId}/usage` | Record a new usage snapshot (e.g. mileage reading) for the asset | `201 Created` |
| `GET` | `/api/v1/experience/lending/asset-finance/{id}/end-options` | List all end-of-term options (purchase, return, extend) for the agreement | `200 OK` |
| `POST` | `/api/v1/experience/lending/asset-finance/{id}/end-options/{optId}/exercise` | Exercise a lease-end option (e.g., purchase the asset) | `200 OK` |

### Collections

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/experience/lending/collections` | List all active collection cases accessible to the caller | `200 OK` |
| `GET` | `/api/v1/experience/lending/collections/{id}` | Retrieve full details of a collection case including its actions | `200 OK` |
| `POST` | `/api/v1/experience/lending/collections/{id}/promise-to-pay` | Record a promise-to-pay agreement against a collection case | `201 Created` |

## Domain SDK Dependencies

| SDK | ClientFactory | APIs Used | Purpose |
|-----|--------------|-----------|---------|
| `domain-lending-loan-origination-sdk` | `LoanOriginationClientFactory` | `LoanOriginationApi` | Application lifecycle — create, update, submit, withdraw, status history, details, documents, parties, disbursement |
| `domain-lending-loan-servicing-sdk` | `LoanServicingClientFactory` | `LoanServicingApi`, `LoanServicingQueriesApi` | Active loan management — balance, schedule, installments, repayments, disbursements, accruals, escrow, rebates, rate changes, restructuring, early repayment |
| `domain-lending-credit-scoring-sdk` | `CreditScoringClientFactory` | `CreditScoringApi` | Credit scoring status and underwriting decision retrieval |
| `domain-lending-asset-finance-sdk` | `AssetFinanceClientFactory` | `AssetFinanceApi` | Asset finance agreement management — agreements, assets, deliveries, returns, pickups, service events, usage, end options |
| `domain-lending-personal-loans-sdk` | `PersonalLoansClientFactory` | `PersonalLoansApi` | Personal loan agreement management — create, retrieve, update, list agreements |
| `domain-product-catalog-sdk` | `ProductCatalogClientFactory` | `ProductsApi` | Product definitions and catalog |
| `domain-product-pricing-sdk` | `ProductPricingClientFactory` | `PricingApi`, `EligibilityApi`, `FeesApi` | Loan simulation computation and product eligibility checks |
| `domain-common-contracts-sdk` | `ContractsClientFactory` | `ContractsApi`, `ContractDocumentsApi`, `ContractPartiesApi`, `ContractSignaturesApi`, `ContractTermsApi` | Loan contract retrieval and SCA-protected signing |
| `domain-common-sca-sdk` | `ScaClientFactory` | *(placeholder — SDK pending)* | Strong Customer Authentication for contract signing |
| `domain-common-notifications-sdk` | `NotificationsClientFactory` | `NotificationsApi` | Loan-related notification history on active loans |

## Configuration

```yaml
server:
  port: ${SERVER_PORT:8102}

api-configuration:
  domain-platform:
    lending-loan-origination:
      base-path: ${LOAN_ORIGINATION_URL:http://localhost:8082}
    lending-loan-servicing:
      base-path: ${LOAN_SERVICING_URL:http://localhost:8084}
    lending-credit-scoring:
      base-path: ${CREDIT_SCORING_URL:http://localhost:8042}
    lending-asset-finance:
      base-path: ${ASSET_FINANCE_URL:http://localhost:8043}
    lending-personal-loans:
      base-path: ${PERSONAL_LOANS_URL:http://localhost:8044}
    product-pricing:
      base-path: ${PRODUCT_PRICING_URL:http://localhost:8086}
    common-contracts:
      base-path: ${CONTRACTS_URL:http://localhost:8090}
    common-sca:
      base-path: ${SCA_URL:http://localhost:8041}
    common-notifications:
      base-path: ${NOTIFICATIONS_URL:http://localhost:8095}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8102` | HTTP server port |
| `LOAN_ORIGINATION_URL` | `http://localhost:8082` | Base URL for `domain-lending-loan-origination` |
| `LOAN_SERVICING_URL` | `http://localhost:8084` | Base URL for `domain-lending-loan-servicing` |
| `CREDIT_SCORING_URL` | `http://localhost:8042` | Base URL for `domain-lending-credit-scoring` |
| `ASSET_FINANCE_URL` | `http://localhost:8043` | Base URL for `domain-lending-asset-finance` |
| `PERSONAL_LOANS_URL` | `http://localhost:8044` | Base URL for `domain-lending-personal-loans` |
| `PRODUCT_PRICING_URL` | `http://localhost:8086` | Base URL for `domain-product-pricing` |
| `CONTRACTS_URL` | `http://localhost:8090` | Base URL for `domain-common-contracts` |
| `SCA_URL` | `http://localhost:8041` | Base URL for `domain-common-sca` |
| `NOTIFICATIONS_URL` | `http://localhost:8095` | Base URL for `domain-common-notifications` |

## Running Locally

Provision the technical PostgreSQL cache before starting this service. Set
`CACHE_DB_HOST`, `CACHE_DB_PORT`, `CACHE_DB_NAME`, `CACHE_DB_USERNAME`,
`CACHE_DB_PASSWORD`, and `CACHE_DB_SCHEMA`. `CACHE_DB_SSL_MODE` defaults to
`verify-full`. The cache schema and `cache_entries` table are provisioned
separately; automatic schema creation is disabled. Give this service its own
restricted cache credentials, without access to Core business databases.
Read credentials from the deployment environment rather than committing them.

```bash
# Prerequisites — ensure all downstream services are running or accessible
cd exp-lending
mvn spring-boot:run -pl exp-lending-web
```

Server starts on port `8102`. Swagger UI: [http://localhost:8102/swagger-ui.html](http://localhost:8102/swagger-ui.html)

Swagger UI and API docs are disabled in the `pro` profile.

## Testing

```bash
mvn clean verify
```

Source tests cover service implementations, WebFlux controllers, native JWT
admission, and the full original generated API. Managed draft intake requires
the matching regenerated Domain SDK; its composed source qualification builds
the owning services and genuine SDKs together. Registry release and deployment
adoption remain separate from this source qualification.

## Managed draft application intake

`POST /api/v1/experience/lending/application-intakes` creates an application in
`DRAFT` / `PENDING_DOCUMENTS` through the generated Domain SDK. A first creation
returns `201`; an exact replay returns `200` with the same application, terms,
creation time, and owner digest. Preserve the original request ID and complete
request when recovering an unavailable response. A changed request with the
same ID is a conflict, not a new application.

The error code `LENDING_INTAKE_COMMITTED_RESPONSE_UNAVAILABLE` identifies a
known committed operation whose response cannot currently be delivered.
`LENDING_INTAKE_OUTCOME_UNKNOWN` leaves the outcome unresolved. Both direct the
caller to the same request ID and exact original terms; neither authorizes a
replacement action or automatic compensation. Current authority is checked
before disclosing the owner digest.

This route uses verified native HUMAN credentials and subject-preserving token
exchange from Experience to Domain to Core. Configure the resource server,
exchange registration, and current authorization policy for the deployment.
Experience has no business repository or direct Core SDK dependency for this
operation.

Controlled integration has exercised all three production services, generated
SDK calls, concurrent replay, committed-response recovery, refused writes,
service restart, and actual Core Spring Data/PostgreSQL application, primary
borrower, audit, and outbox effects. Its issuer and policy are sandbox fixtures;
default deployment authorization, the current combined Domain release, event
delivery, and external providers require separate validation. This operation
does not approve a loan, generate a contract, disburse funds, or qualify the
complete lending lifecycle.


## Identity, access, and decisions

The legacy journey adapters below resolve the actor and check access before
calling a domain port. Managed draft intake uses the native credential and
token-exchange path described above.

1. **IdentityPort** posts to `POST /api/v1/identity/subjects` on domain-core-security-center. The default username is `sample-journey`, which resolves to actor `11111111-1111-4111-8111-111111111111`. `denied-actor` resolves to `22222222-2222-4222-8222-222222222222`. `sample-employee` resolves to `33333333-3333-4333-8333-333333333333`. Experience code does not call Keycloak. The security center property `firefly.security-center.identity.adapter` is `sandbox` by default, or `keycloak`, `cognito`, or `internal-db`.
2. **AccessPort** posts to `POST /api/v1/access/checks` with the actor, action (`READ`, `CREATE`, `UPDATE`, `CLOSE`, or `DECIDE`), resource type, and resource id. The sample actor may READ and CREATE the sample resources. The denied actor receives `ACTOR_NOT_ENTITLED`.
3. Lending application create and onboarding initiation then call **DecisionPort** at `POST /api/v1/decisions` on domain-lending-credit-scoring. A `DECLINE` does not call the domain command.
4. The domain port runs only after access is allowed.

Set `firefly.experience.access.adapter=sandbox` only when the security-center process is not running. The default calls the security center. Set `api-configuration.domain-platform.security-center.base-path` (default `http://localhost:8085`).

# Lending simulation availability

The simulation routes are unavailable until the service can obtain exact, authorized lending product terms from their actual owner. The previous simulation path registered the requested principal as administrative product pricing and interpreted its configuration value as an annual rate. The configuration path persisted a calculation without an immutable owner terms reference. Both monetary side effects have been removed.

## Current API behavior

| Route | Result | Downstream effects |
|---|---|---|
| POST /api/v1/experience/lending/simulations | 503 LENDING_TERMS_UNAVAILABLE | No administrative pricing registration, calculation or simulation persistence |
| POST /api/v1/experience/lending/simulations/configure | Existing validation (400), otherwise 503 LENDING_TERMS_UNAVAILABLE | No pricing-engine calculation or origination persistence |
| POST /api/v1/experience/lending/eligibility | Existing separate eligibility behavior | Existing eligibility SDK evaluation; it is not a quote or offer |

The JSON error uses the standard Firefly ErrorResponse contract, with status 503, code `LENDING_TERMS_UNAVAILABLE`, and message `Verified lending product terms are unavailable`. It contains no successful simulation ID, configuration ID, annual rate, installment amount or offer. Existing request shapes and historical success DTO schemas remain available to generated clients; the current implementation does not emit successful simulations.

Configuration validation retains its existing positive requested amount, term, supported product-type and cross-field requirements: PERSONAL_LOAN requires purpose; LEASING requires sector and assetType. Invalid requests still receive 400. No new unsupported-profile 422 contract is introduced by this correction. Repeating either simulation request cannot create a new pricing record or persisted simulation. Both methods use the native DisableIdempotency opt-out so a historical cached 201 cannot bypass the current unavailable response. The optional `X-Idempotency-Key` header remains documented for generated SDK compatibility, but these two methods ignore it. The eligibility route retains its existing transport behavior. There is no minute-bucket request deduplication standing in for a customer operation identity.

## Required integration before numerical results can return

The product owner must expose a protected observation of exact immutable public terms, publication/revision and digest, current permission/effectivity, actual product identity, exact currency-policy reference and financial/calendar conventions. A mutable generic configuration response or a default product selected by list order does not establish those facts. Public product definition alone does not establish operational admission; private costs must never enter customer simulation responses.

The existing pricing domain remains the calculation owner. Its generated SDK is already present, but redirecting to its legacy calculator would not supply missing immutable references or fix its currency/rounding and schedule assumptions. A later qualified implementation must bind actual owner terms, calculate the supported profile with exact currency representation, prove schedule/fee/total invariants and return explicit unavailable/unsupported outcomes for missing capabilities. It must not fabricate an accepted offer, policy, approval or financial posting.

Persisted simulations additionally require actual origination-owner retention of exact inputs, terms references and result, stable operation identity, immutable replay and current authorization. Application, simulation, product configuration, offer, contract and loan identifiers remain distinct.

This correction does not deliver a complete numerical simulation or lending journey. It prevents incorrect monetary side effects while those owner contracts and calculation gates are implemented. The source-owned OpenAPI remains the only generated SDK source; clients are not edited by hand.

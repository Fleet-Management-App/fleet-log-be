# FleetLog.app - AI Agent Development Guidelines

## 1. Product Vision & Core Philosophy
* **Core idea:** A multi-tenant B2B SaaS application for companies operating a vehicle fleet, replacing paper/Excel logbooks with a digital electronic trip logbook that includes automatic anomaly detection (odometer gaps between trips, suspicious fuel fill-ups, missing approvals).
* **Logbook, not booking:** FleetLog is a record-keeping tool (what happened / how a vehicle was used), not a reservation system. Vehicles don't form a calendar of free slots — the driver picks a vehicle from a shared pool when planning a trip; it is not a pre-fixed reservation.
* **Anomaly checks are the differentiator:** The product's main value is automatic detection of inconsistencies (odometer gap between trips, unusual fuel consumption, etc.), not just passive data recording. Every new feature should reinforce this control layer, not bypass it.
* **Approval workflow as a guarantee of data trustworthiness:** The trip plan (including the chosen vehicle) is approved by the Fleet Manager before the trip happens. Completing a trip (logging the odometer) is independent of fuel approval — fuel approval is purely voluntary and is only triggered when a receipt is added; it never blocks the "Completed" status.
* **Role-based, not one universal user:** Three clearly separated roles — **Driver** (logs trips/fuel/problem reports, has no persistently assigned vehicle), **Fleet Manager** (approves trip plans and fuel entries, manages vehicles and service/maintenance), **Accountant** (reads financial history, exports to Excel). No feature should blur this boundary of responsibilities.

## 2. Architectural Principles & MVP Logic
* **Multi-tenant from the ground up:** Each company (tenant) has isolated data — vehicles, trips, users. No data sharing between tenants, not even accidental (every query/mutation must always be scoped to tenant_id).
* **No persistent vehicle-to-driver assignment:** Vehicles are a shared pool. Picking a vehicle is part of trip planning (UC-01) and is subject to Fleet Manager approval. Do not implement any "assigned vehicle" relation on the user.
* **Two-phase trip approval:**
  1. **Plan Approved/Rejected** — the Fleet Manager approves the plan (including the chosen vehicle) BEFORE the trip happens.
  2. **Fuel Approval** — independent, optional, triggered ONLY by adding a fuel receipt. It never blocks trip completion (which happens as soon as the odometer is logged).
* **Simple deterministic logic for the MVP:** No ML models for anomaly detection at this stage. Use clear rules (e.g. odometer gap between the end of one trip and the start of the next > tolerance, default 10 km, configurable by the Fleet Manager).
* **Business Rules as the single source of truth:** All domain validation logic (BR-01 through BR-11+) is centralized in Confluence on the "04. Business Rules & Validations" page. When implementing validations, always reference the specific BR-ID — do not duplicate rules directly in code without a reference.
* **Export, not a reporting dashboard:** The Accountant primarily needs an Excel export of financial history (fuel, service) — not a complex BI dashboard. Don't build unnecessarily complex aggregation layers beyond MVP scope (Simple statistics = basic figures, not analytics).

## 3. Prompting & AI Behavior Instructions
* When designing a new feature, always ask: "Does this design violate the 'no persistent vehicle assignment' model and the two-phase approval (plan vs. fuel)?"
* Generated code must respect that Fuel Approval is independent and optional — never implement it as a gating condition for the "Completed" state.
* When writing or changing business logic, always check the current BR-IDs on the Confluence "04. Business Rules & Validations" page first — never duplicate or rewrite rules locally without a reference.
* Integration logic testing (especially calls to any future external system) is done via mocking, not by calling real services.

**Code quality policy:**
  - Follow the code style guidelines in the repository.
  - Follow the code quality guidelines in the repository.
  - Follow the naming conventions in the repository.
  - Start every method name with a verb that says what it does (`getMembers`, `createInvitation`, `findMember`), never a bare noun (`members()`, `me()`). This includes controller / API interface methods, since their names become the operationIds the FE client is generated from. Two exceptions: Spring `@Bean` methods keep noun names (`passwordEncoder()`, `jwtDecoder()`), because the method name is the bean name; MapStruct conversions use `toX()` (`toMemberResponse()`).
  - Follow the documentation guidelines in the repository.
  - Follow the testing guidelines in the repository.
  - Follow the security guidelines in the repository.
  - Follow the performance guidelines in the repository.
  - Follow the maintainability guidelines in the repository.
  - Use mappers (mapstruct) to map the data structures. When a response field is assembled from several source fields conditionally (e.g. a nested object that only exists when some flag/status is non-null, like a trip's optional `fuel`), still write the mapper as a MapStruct `@Mapper` (`abstract class`, not a hand-rolled `@Component`): map the field with `@Mapping(target = "x", expression = "java(helperMethod(source))")` calling a plain helper method defined on the same mapper class. Don't fall back to a fully hand-written mapper just because one field doesn't fit a 1:1 `@Mapping`.
  - Write meaningful tests for edge case business logic.
  - Separate the business logic from the presentation logic.
  - Use the PostgreSQL data structures.
  - Write comments for the code that is not self-explanatory.

**Integration test conventions (RestAssured + Kotest + Testcontainers):**
  - Test data creation lives in small, focused fixture objects under `src/test/kotlin/com/fleetlog/fixtures/` (one per domain area, e.g. `Accounts`, `Vehicles`, `Trips`), never in one monolithic helper object. Each exposes real HTTP-flow creators named with an explicit verb + noun (`createTrip`, `createTripId`), not terse names like `create`/`id`. Don't introduce two near-duplicate creators for the same endpoint (e.g. a bare `fleetManager()` next to `createOrganization(token, name)`) — give the one function sensible defaults (`createOrganization(token: String = signUp(), name: String = "...")`) instead.
  - A fixture creator returns the real response DTO (`Vehicles.createVehicle(...): VehicleResponse`), deserialized via `Extract { `as`(XResponse::class.java) }` — call `.as(...)` directly on the extracted response, no `.body()` first, it's redundant. Never a raw `Map<String, Any?>`/JSONPath. Assertions then read as `vehicle.status shouldBe VehicleStatus.ACTIVE`, not `vehicle["status"] shouldBe "ACTIVE"`.
  - For auth, use `Accounts.tokenFor(role, organizationOf = ...)` when the test needs to keep the caller's identity for later calls/assertions; use the `asRole(role)` `RequestSpecification` extension directly inside a `Given { }` block when the test only cares about the role (e.g. a 403 check) and never needs that identity again. Name a token variable for what it actually is in that test (e.g. a second organization's Fleet Manager in a tenant-isolation test is `secondOrgFleetManager`, not `secondTenant` — the token isn't the tenant itself).
  - Never name a fixture helper `body`, `header`, or anything else that collides with a `RequestSpecification` member method. Inside a `Given { }` block (whose implicit receiver is `RequestSpecification`), the member silently wins over a same-named top-level/extension function — no compile error, just the wrong thing becomes the request body.
  - Send real request DTOs into `Given { }`, not a hand-built `mapOf(...)`: e.g. `body(VehicleCreateRequest(...))`, not `body(mapOf("plate" to ...))`. A `mapOf(...)` (or a small local-only data class with just the fields under test, e.g. `private data class IncompleteVehicleCreateRequest(val plate: String)`) is only for a test that deliberately sends a malformed/incomplete shape (e.g. a missing-required-field 400 case) — a real DTO's constructor can't be called with a required field left out.
  - Don't write a `withJson`-style wrapper that calls `.contentType(JSON).body(x)` — call RestAssured's own `.body(x)` directly. The one real gap (RestAssured does not infer `Content-Type: application/json` for a POJO body on its own — a bare `.body(x)` without it 415s) is closed once, globally, in `BaseFleetLogBeIntegrationTest` via `RestAssured.requestSpecification = RequestSpecBuilder().setContentType(ContentType.JSON).build()`, not per call site.
  - For a list response, deserialize with `Extract { `as`(Array<XResponse>::class.java).toList() }`, then assert with the infix `list shouldHaveSize n` — not the hamcrest one-liner `Then { body("", hasSize<Any>(n)) }`, and not `jsonPath().getList("", XResponse::class.java)` (same result, but this matches the `.as(...)` style already used for a single object).
  - Never assert on the response with hamcrest's `Then { body("status", equalTo("X")) }` when the endpoint returns a typed object — `Extract { `as`(XResponse::class.java) }` into a `val` instead and assert on its typed properties (`trip.status shouldBe TripStatus.PLAN_REJECTED`, `trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING`), the same way a list response is asserted on.
  - Use the infix form for a one-argument Kotest matcher (`x shouldBe y`, `list shouldHaveSize n`), not the dot-call form (`x.shouldBe(y)`). A zero-argument matcher (`shouldBeNull()`, `shouldNotBeNull()`) can't be infix and stays a dot call.
  - `statusCode(...)` takes `HttpStatus.X.value()` (`org.springframework.http.HttpStatus`), never a bare magic number like `statusCode(200)`.
  - Format every `Given { } When { } Then { } Extract { }` chain with each block's body on its own indented line (`Given {\n    ...\n} When {\n    ...\n}`), not crammed onto one line — even when a block is a single call.
  - Format every `Given { } When { } Then { } Extract { }` chain with each block's body on its own indented line (`Given {\n    ...\n} When {\n    ...\n}`), not crammed onto one line — even when a block is a single call.


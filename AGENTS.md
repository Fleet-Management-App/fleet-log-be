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
  - Follow the documentation guidelines in the repository.
  - Follow the testing guidelines in the repository.
  - Follow the security guidelines in the repository.
  - Follow the performance guidelines in the repository.
  - Follow the maintainability guidelines in the repository.
  - Use mappers (mapstruct) to map the data structures.
  - Write meaningful tests for edge case business logic.
  - Separate the business logic from the presentation logic.
  - Use the PostgreSQL data structures.
  - Write comments for the code that is not self-explanatory.


# FleetLog — project overview

> Draft, 2026-10-18. Source: the team vision document (*Vize projektu FleetLog*) plus team decisions.
> Items marked **(?)** are open questions, not decisions.

## In one sentence

FleetLog is a simple, hosted (SaaS) fleet-management app for small and mid-sized organisations (up
to a few hundred employees). It records company vehicles, the trips employees make in them, fuel
fills, servicing and STK, and flags records that don't add up. It does no GPS tracking.

## The problem

- Fleet data is scattered: spreadsheets, paper logbooks, fuel-card statements, emails with the garage.
  Nobody has the full picture, and mistakes go unnoticed until an audit or an expired STK.
- Commercial products are either built around GPS/telematics (hardware, cost, privacy concerns), or are
  far more than a 20–100 vehicle company needs.

**Who it hurts:** drivers, who keep the logbook and report problems, and the managers responsible for
the company cars.

## Who uses it

| Role | What they do |
|---|---|
| **Driver** (borrower of a car) | Logs trips and fuel fills, reports problems with a car, sees the cars assigned to them. |
| **Fleet manager** | Administers the company account: users and roles, vehicles and their assignment, service and STK. Reviews flagged records, sees statistics. A company can have several. |
| **Accountant** | Read-only. Sees the full history, filters it by period (e.g. the last quarter) and exports it to Excel. |

## Scope — core (MVP)

1. **Vehicles** — registration plate, VIN, make/model, fuel type, current odometer, assigned driver
   or pool car, status (active / in service / retired). Expiry dates for **STK, insurance and the
   motorway vignette** are kept on the vehicle and highlighted in the app when they are about to
   expire or have expired (no email notifications for now).
2. **Trips (logbook)** — driver, vehicle, date/time, start and end odometer, from/to, purpose,
   business vs private.
3. **Fuel fills** — date, odometer, litres, price, station, receipt photo.
4. **Service & maintenance** — service records (what, when, odometer, cost, garage) and planned
   service by date or mileage.
5. **Problem reports** — a driver reports an issue with the car they borrowed (check-engine light,
   strange noise, damage); the fleet manager sees it and marks it resolved.
6. **Anomaly checks** — the system checks records automatically and warns about inconsistencies (below).
7. **Simple statistics** — cost and kilometres per vehicle, driver and month.
8. **Excel export** — mainly for the accountant: trips, fuel fills and service costs for a period.

### Anomaly checks (the differentiator)

- the odometer goes backwards, or there is a gap between one trip's end and the next trip's start
- overlapping trips for the same vehicle, or the same driver in two cars at once
- a trip logged while the vehicle is in service or its STK has expired

A flag is a warning for the fleet manager to review, not a hard block, so a driver can always save a record.

## Not in scope

- **GPS / telematics / automatic location tracking** — deliberately not part of the product.
- Native mobile apps — web only (it should still be usable on a phone).
- Payroll, travel allowances, full accounting.

## Later extensions

- **Traffic fines** — record a fine and match it to whoever was driving the car at that time.
- **TCO (total cost of ownership)** — the full cost of each car over its life: purchase price and
  depreciation, fuel, service, insurance, fines.
- **Electric vehicles** — charging records (kWh, price, home / public charger) alongside fuel fills,
  and consumption in kWh/100 km.
- **Entra ID login** — company sign-in through Microsoft Entra in addition to the built-in accounts.
- **Czech UI** — the app is internationalised from day one, but ships in English first.

### Under consideration — no promise

- **Logbook export that meets Czech tax rules (*kniha jízd*).** To count fuel as a business expense,
  a company must keep a trip log (date, route, purpose, kilometres, driver, business vs private).
  The trips module should already hold that data, so this would mostly be an export in the right
  shape. We will look into the legal requirements first, and only then decide.

## Deployment & accounts

FleetLog is **SaaS**: we deploy and run one installation, and companies sign up to it. Each company
is a separate tenant, and its data is visible only to its own users.

- **Registration:** a company registers, and the person who registers it becomes its first fleet
  manager. They invite drivers and the accountant.
- **Login:** the app's own accounts (email + password) to start with; Entra ID later (see above).

## Competitors

| Product | Weakness (per the vision document) | Our angle |
|---|---|---|
| O₂ Car Control | Dated, clumsy UI; complex, expensive API integration | Modern UI, an open REST API |
| CarWatch | No advanced analytics (TCO), weak EV support | TCO and EV support as extensions |
| Ecofleet | Overkill for small companies; slow support | Small and simple on purpose |
| MobilityManager | On-premise technical debt, slow and expensive updates | SaaS, we update it centrally |
| Fleet Service 365 | Outdated single-tenant architecture | Multi-tenant SaaS |
| AdaTrack | GPS tracking only, no fines or digital protocols | Fleet administration without GPS; traffic fines |

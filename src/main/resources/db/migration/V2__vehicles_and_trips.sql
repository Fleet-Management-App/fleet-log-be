-- A fleet vehicle, scoped to one organization (SPEC-01).
CREATE TABLE vehicle
(
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    plate               VARCHAR(20)  NOT NULL,
    vin                 VARCHAR(32)  NOT NULL,
    make                VARCHAR(100) NOT NULL,
    model               VARCHAR(100) NOT NULL,
    fuel_type           VARCHAR(16)  NOT NULL CHECK (fuel_type IN ('PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID', 'LPG')),
    odometer_km         INTEGER      NOT NULL,
    status              VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'IN_SERVICE', 'SUSPENDED')),
    stk_due_date        DATE,
    insurance_due_date  DATE,
    vignette_due_date   DATE,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_vehicle_organization_plate UNIQUE (organization_id, plate),
    CONSTRAINT ux_vehicle_organization_vin UNIQUE (organization_id, vin)
);
CREATE INDEX ix_vehicle_organization ON vehicle (organization_id);

-- A planned/driven trip against a pool vehicle (SPEC-02). Fuel fields are flat columns: a trip
-- has at most one fuel entry, and it is never queried on its own.
CREATE TABLE trip
(
    id                    UUID PRIMARY KEY,
    organization_id       UUID         NOT NULL REFERENCES organization (id),
    vehicle_id            UUID         NOT NULL REFERENCES vehicle (id),
    driver_id             UUID         NOT NULL REFERENCES app_user (id),
    date_time             TIMESTAMPTZ  NOT NULL,
    from_location         VARCHAR(200) NOT NULL,
    to_location           VARCHAR(200) NOT NULL,
    purpose               VARCHAR(500) NOT NULL,
    status                VARCHAR(16)  NOT NULL CHECK (status IN ('PLANNED', 'PLAN_APPROVED', 'PLAN_REJECTED', 'COMPLETED', 'CANCELLED')),
    rejection_reason      VARCHAR(500),
    odometer_start_km     INTEGER,
    odometer_end_km       INTEGER,
    fuel_liters           NUMERIC(10, 2),
    fuel_price            NUMERIC(10, 2),
    fuel_receipt_photo    TEXT,
    fuel_status           VARCHAR(16) CHECK (fuel_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    fuel_rejection_reason VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_trip_organization ON trip (organization_id);
CREATE INDEX ix_trip_driver ON trip (driver_id);

--liquibase formatted sql

--changeset fleetlog:004-normalised-vehicle-plate-and-vin
-- Plates and VINs are stored normalised (FLOG-51): a plate trimmed, upper-case, inner whitespace
-- collapsed to one space; a VIN upper-case with no whitespace at all. Existing rows are brought into
-- that form first.
UPDATE vehicle
SET plate = upper(regexp_replace(regexp_replace(plate, '^\s+|\s+$', '', 'g'), '\s+', ' ', 'g')),
    vin   = upper(regexp_replace(vin, '\s+', '', 'g'));

-- Spacing doesn't make a different plate ("1AB2345" is "1AB 2345"), so uniqueness ignores spaces.
-- VehicleService checks the same expression before inserting. The VIN keeps its plain unique
-- constraint: the stored value is already normalised.
ALTER TABLE vehicle
    DROP CONSTRAINT ux_vehicle_organization_plate;
CREATE UNIQUE INDEX ux_vehicle_organization_plate ON vehicle (organization_id, upper(replace(plate, ' ', '')));

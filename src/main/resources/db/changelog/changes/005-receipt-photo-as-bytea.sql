--liquibase formatted sql

--changeset fleetlog:005-receipt-photo-as-bytea
-- Fuel receipt photos move out of trip.fuel_receipt_photo (a base64 data URL in a TEXT column) into
-- their own table as raw bytes (FLOG-49). Base64 made each photo a third bigger, and Postgres can't
-- compress JPEG/PNG any further, so the bytes are stored as they are. In a table of their own, trip
-- queries (e.g. the trip list, which only needs to know whether a receipt exists) never read them.
-- One receipt per trip; it goes with its trip.
CREATE TABLE fuel_receipt
(
    trip_id      UUID PRIMARY KEY REFERENCES trip (id) ON DELETE CASCADE,
    content_type VARCHAR(100) NOT NULL,
    photo        BYTEA        NOT NULL
);

-- Existing receipts are converted when they are a well-formed base64 data URL,
-- `data:<type>/<subtype>;base64,<data>`: <data> is decoded into photo and <type>/<subtype> (lower-cased)
-- becomes content_type. Anything else is dropped rather than converted: there is nothing to decode it
-- from, and decode() would abort the whole migration on the first malformed value. That is why the
-- base64 part is matched strictly (whole groups of four, padding only at the end, not empty) before
-- decode() ever sees it. Such a trip keeps its fuel entry, which then reports hasReceipt = false.
INSERT INTO fuel_receipt (trip_id, content_type, photo)
SELECT id, lower(parts[1]), decode(parts[2], 'base64')
FROM (SELECT id,
             regexp_match(
                     fuel_receipt_photo,
                     '^data:([a-z0-9!#$&^_.+-]{1,49}/[a-z0-9!#$&^_.+-]{1,50});base64,((?:[a-z0-9+/]{4})*(?:[a-z0-9+/]{2}==|[a-z0-9+/]{3}=|[a-z0-9+/]{4}))$',
                     'i') AS parts
      FROM trip
      WHERE fuel_receipt_photo IS NOT NULL) AS receipt
WHERE parts IS NOT NULL;

ALTER TABLE trip
    DROP COLUMN fuel_receipt_photo;

package com.fleetlog.exception

/**
 * Stable, machine-readable error codes sent as the `code` property of a problem response. The FE
 * switches on these (and translates them), so a code, once shipped, is never renamed; the English
 * `detail` next to it is for humans and logs only.
 */
enum class ErrorCode {

	/** 400: bean validation failed; the `errors` property lists each field and constraint. */
	VALIDATION_FAILED,

	/** 400: the request body isn't readable JSON of the expected shape, or a path/query value has the wrong type. */
	MALFORMED_REQUEST,

	// Trips (SPEC-02)
	TRIP_NOT_FOUND,
	TRIP_NOT_PLANNED,
	TRIP_NOT_PLAN_REJECTED,
	TRIP_NOT_PLAN_APPROVED,
	ODOMETER_END_BEFORE_START,
	FUEL_ENTRY_ALREADY_EXISTS,
	FUEL_ENTRY_NOT_PENDING,
	NO_FUEL_ENTRY,
	FUEL_RECEIPT_NOT_FOUND,

	/** 400: the receipt photo isn't a base64 data URL of an accepted image type (JPEG, PNG, WebP, HEIC/HEIF). */
	RECEIPT_PHOTO_INVALID,

	/** 400: the receipt photo is larger than the limit (5 MiB decoded). */
	RECEIPT_PHOTO_TOO_LARGE,
	REJECTION_REASON_REQUIRED,

	// Vehicles (SPEC-01)
	VEHICLE_NOT_FOUND,
	VEHICLE_UNAVAILABLE,
	VEHICLE_PLATE_TAKEN,
	VEHICLE_VIN_TAKEN,

	// Accounts & organizations (SPEC-08)
	WRONG_CREDENTIALS,
	EMAIL_TAKEN,
	ALREADY_MEMBER,
	INVITATION_NOT_FOUND,
	INVITATION_NOT_PENDING,
	INVITATION_ALREADY_PENDING,
	MEMBER_NOT_FOUND,
	LAST_FLEET_MANAGER,

}

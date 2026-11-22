package com.fleetlog.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class TripFuelRequest(
	@field:NotNull @field:Positive
	val liters: BigDecimal,

	@field:NotNull @field:Positive
	val price: BigDecimal,

	/**
	 * The receipt photo as a base64 data URL (`data:image/jpeg;base64,...`). Required: a fuel entry always
	 * includes its receipt (BR-14). Accepted types: `image/jpeg`, `image/png`, `image/webp`, `image/heic`,
	 * `image/heif`; at most 5 MiB decoded. Otherwise 400 with `RECEIPT_PHOTO_INVALID` /
	 * `RECEIPT_PHOTO_TOO_LARGE` and `field: "receiptPhoto"`. Stored as raw bytes, not as this string.
	 */
	@field:NotBlank
	val receiptPhoto: String,
)

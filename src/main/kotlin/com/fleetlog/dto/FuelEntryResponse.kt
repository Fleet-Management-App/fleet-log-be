package com.fleetlog.dto

import com.fleetlog.entity.FuelStatus
import java.math.BigDecimal

data class FuelEntryResponse(
	val liters: BigDecimal,
	val price: BigDecimal,
	/** The photo itself is fetched separately (GET /api/trips/{tripId}/fuel/receipt), so lists stay small. */
	val hasReceipt: Boolean,
	val status: FuelStatus,
	/** Set when the Fleet Manager rejected the fuel entry. */
	val rejectionReason: String?,
)

package com.fleetlog.dto

import com.fleetlog.entity.FuelStatus
import java.math.BigDecimal

data class FuelEntryResponse(
	val liters: BigDecimal,
	val price: BigDecimal,
	val receiptPhotoUrl: String?,
	val status: FuelStatus,
)

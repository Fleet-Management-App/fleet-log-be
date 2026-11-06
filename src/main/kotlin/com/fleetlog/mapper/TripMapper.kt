package com.fleetlog.mapper

import com.fleetlog.dto.FuelEntryResponse
import com.fleetlog.dto.TripResponse
import com.fleetlog.entity.Trip
import org.mapstruct.Mapper
import org.mapstruct.Mapping

/**
 * The `fuel` field is assembled by [toFuelEntry] (a plain helper MapStruct calls via the `java(...)`
 * expression below) since it is built from several flat, conditionally-null columns, not a single
 * source field MapStruct's own `@Mapping` could point at.
 */
@Mapper(componentModel = "spring")
abstract class TripMapper {

	@Mapping(source = "vehicle.id", target = "vehicleId")
	@Mapping(target = "fuel", expression = "java(toFuelEntry(trip))")
	abstract fun toResponse(trip: Trip): TripResponse

	fun toFuelEntry(trip: Trip): FuelEntryResponse? {
		val status = trip.fuelStatus ?: return null
		return FuelEntryResponse(
			liters = requireNotNull(trip.fuelLiters),
			price = requireNotNull(trip.fuelPrice),
			receiptPhotoUrl = trip.fuelReceiptPhoto,
			status = status,
		)
	}

}

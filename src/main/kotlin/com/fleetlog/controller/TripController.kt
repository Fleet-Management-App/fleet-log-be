package com.fleetlog.controller

import com.fleetlog.controller.api.TripApi
import com.fleetlog.dto.TripCreateRequest
import com.fleetlog.dto.TripFuelDecisionRequest
import com.fleetlog.dto.TripFuelRequest
import com.fleetlog.dto.TripOdometerRequest
import com.fleetlog.dto.TripPlanDecisionRequest
import com.fleetlog.entity.TripStatus
import com.fleetlog.service.TripService
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class TripController(
	private val tripService: TripService,
) : TripApi {

	override fun createTrip(request: TripCreateRequest) = tripService.create(request)

	override fun getTrip(tripId: UUID) = tripService.get(tripId)

	override fun listTrips(status: TripStatus?) = tripService.list(status)

	override fun decideTripPlan(tripId: UUID, request: TripPlanDecisionRequest) = tripService.decidePlan(tripId, request)

	override fun updateTripPlan(tripId: UUID, request: TripCreateRequest) = tripService.updatePlan(tripId, request)

	override fun logOdometer(tripId: UUID, request: TripOdometerRequest) = tripService.logOdometer(tripId, request)

	override fun logFuel(tripId: UUID, request: TripFuelRequest) = tripService.logFuel(tripId, request)

	override fun decideFuel(tripId: UUID, request: TripFuelDecisionRequest) = tripService.decideFuel(tripId, request)

	override fun cancelTrip(tripId: UUID) = tripService.cancel(tripId)

}

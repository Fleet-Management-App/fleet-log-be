package com.fleetlog.controller

import com.fleetlog.controller.api.VehicleApi
import com.fleetlog.dto.VehicleCreateRequest
import com.fleetlog.service.VehicleService
import org.springframework.web.bind.annotation.RestController

@RestController
class VehicleController(
	private val vehicleService: VehicleService,
) : VehicleApi {

	override fun createVehicle(request: VehicleCreateRequest) = vehicleService.create(request)

	override fun listVehicles() = vehicleService.list()

}

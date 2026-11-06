package com.fleetlog.controller.api

import com.fleetlog.dto.VehicleCreateRequest
import com.fleetlog.dto.VehicleResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Vehicles", description = "The fleet's pool vehicles (SPEC-01)")
@RequestMapping("/api/vehicles")
interface VehicleApi {

	@Operation(summary = "Register a vehicle; Fleet Manager only")
	@ApiResponse(responseCode = "201")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun createVehicle(@Valid @RequestBody request: VehicleCreateRequest): VehicleResponse

	@Operation(summary = "List the active organization's vehicles")
	@GetMapping
	fun listVehicles(): List<VehicleResponse>

}

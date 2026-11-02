package com.fleetlog.controller.api

import com.fleetlog.dto.AuthTokenResponse
import com.fleetlog.dto.CreateOrganizationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Organizations", description = "Creating organizations")
@RequestMapping("/api/organizations")
interface OrganizationApi {

	@Operation(summary = "Create an organization; the caller becomes its first Fleet Manager and switches to it")
	@ApiResponse(responseCode = "201", description = "Token for the new organization")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun createOrganization(@Valid @RequestBody request: CreateOrganizationRequest): AuthTokenResponse

}

package com.fleetlog.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateOrganizationRequest(
	@field:NotBlank @field:Size(max = 200)
	val name: String,
)

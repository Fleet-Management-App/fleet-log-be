package com.fleetlog.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignUpRequest(
	@field:NotBlank @field:Email @field:Size(max = 320)
	val email: String,
	@field:Size(min = 8, max = 100)
	val password: String,
	@field:NotBlank @field:Size(max = 200)
	val fullName: String,
)

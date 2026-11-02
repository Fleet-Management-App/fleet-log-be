package com.fleetlog.dto

import com.fleetlog.entity.Role
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateInvitationRequest(
	@field:NotBlank @field:Email @field:Size(max = 320)
	val email: String,
	val role: Role,
)

package com.fleetlog.dto

import com.fleetlog.entity.Role
import jakarta.validation.constraints.NotEmpty

/** The member's complete new set of roles; replaces the old one. */
data class ChangeRolesRequest(
	@field:NotEmpty
	val roles: Set<Role>,
)

package com.fleetlog.controller.api

import com.fleetlog.dto.MeResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "Account", description = "The signed-in user")
@RequestMapping("/api/me")
interface AccountApi {

	@Operation(summary = "The caller, their organizations and the invitations waiting for them")
	@GetMapping
	fun me(): MeResponse

}

package com.fleetlog.controller

import com.fleetlog.controller.api.AuthApi
import com.fleetlog.dto.LoginRequest
import com.fleetlog.dto.SignUpRequest
import com.fleetlog.dto.SwitchOrganizationRequest
import com.fleetlog.security.CurrentUser
import com.fleetlog.service.AuthService
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
	private val authService: AuthService,
	private val currentUser: CurrentUser,
) : AuthApi {

	override fun signUp(request: SignUpRequest) = authService.signUp(request)

	override fun login(request: LoginRequest) = authService.login(request)

	override fun switchOrganization(request: SwitchOrganizationRequest) =
		authService.switchOrganization(currentUser.userId(), request.organizationId)

}

package com.fleetlog.account

import com.fleetlog.security.CurrentUser
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

@RestController
class AccountController(
	private val accountService: AccountService,
) : AccountApi {

	override fun me() = accountService.me()

}

package com.fleetlog.controller

import com.fleetlog.controller.api.AccountApi
import com.fleetlog.service.AccountService
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountController(
	private val accountService: AccountService,
) : AccountApi {

	override fun getMe() = accountService.getMe()

}

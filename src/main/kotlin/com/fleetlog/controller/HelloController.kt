package com.fleetlog.controller

import com.fleetlog.controller.api.HelloApi
import com.fleetlog.dto.HelloResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController : HelloApi {

	override fun getHello() = HelloResponse("Hello from FleetLog")

}

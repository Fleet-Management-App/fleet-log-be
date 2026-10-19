package com.fleetlog.hello

import org.springframework.web.bind.annotation.RestController

data class HelloResponse(val message: String)

@RestController
class HelloController : HelloApi {

	override fun hello() = HelloResponse("Hello from FleetLog")

}

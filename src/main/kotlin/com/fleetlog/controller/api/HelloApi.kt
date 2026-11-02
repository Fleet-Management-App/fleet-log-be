package com.fleetlog.controller.api

import com.fleetlog.dto.HelloResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "Hello", description = "Smoke-test endpoint")
@RequestMapping("/api/hello")
interface HelloApi {

	@Operation(summary = "Return a hello message")
	@GetMapping
	fun hello(): HelloResponse

}

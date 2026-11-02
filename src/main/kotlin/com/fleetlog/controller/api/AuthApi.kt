package com.fleetlog.controller.api

import com.fleetlog.dto.AuthTokenResponse
import com.fleetlog.dto.LoginRequest
import com.fleetlog.dto.SignUpRequest
import com.fleetlog.dto.SwitchOrganizationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Auth", description = "Sign-up, login and switching the active organization")
@RequestMapping("/api/auth")
interface AuthApi {

	@Operation(summary = "Create a global account (no organization yet)")
	@ApiResponse(responseCode = "201", description = "Account created, token without a tenant")
	@ApiResponse(responseCode = "409", description = "E-mail already used", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
	@SecurityRequirements
	@PostMapping("/sign-up")
	@ResponseStatus(HttpStatus.CREATED)
	fun signUp(@Valid @RequestBody request: SignUpRequest): AuthTokenResponse

	@Operation(summary = "Log in; the first organization the user joined becomes active")
	@ApiResponse(responseCode = "200", description = "Token issued")
	@ApiResponse(responseCode = "401", description = "Wrong e-mail or password", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
	@SecurityRequirements
	@PostMapping("/login")
	fun login(@Valid @RequestBody request: LoginRequest): AuthTokenResponse

	@Operation(summary = "Issue a token for another of the caller's organizations")
	@ApiResponse(responseCode = "200", description = "Token for the chosen organization")
	@ApiResponse(responseCode = "403", description = "Not a member of that organization", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
	@PostMapping("/switch-organization")
	fun switchOrganization(@Valid @RequestBody request: SwitchOrganizationRequest): AuthTokenResponse

}

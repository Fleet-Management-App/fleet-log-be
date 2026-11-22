package com.fleetlog.exception

import io.swagger.v3.oas.annotations.media.Schema

/**
 * OpenAPI description of the RFC 9457 body every error response carries. Documentation only: at
 * runtime [ApiExceptionHandler] returns Spring's `ProblemDetail`, whose extra properties are
 * serialized flat next to the standard ones, exactly as described here.
 */
@Schema(name = "ApiProblem", description = "Error response body (RFC 9457 problem details)")
data class ApiProblem(
	val type: String?,
	val title: String,
	val status: Int,
	@field:Schema(description = "Human-readable, English; not meant to be parsed")
	val detail: String?,
	val instance: String?,
	@field:Schema(description = "Stable machine-readable error code; absent on errors that don't have one")
	val code: ErrorCode?,
	@field:Schema(description = "The request field the error is about, when there is exactly one")
	val field: String?,
	@field:Schema(description = "Present when code is VALIDATION_FAILED: one entry per failed constraint")
	val errors: List<ApiFieldError>?,
)

@Schema(name = "ApiFieldError", description = "One failed bean-validation constraint")
data class ApiFieldError(
	@field:Schema(description = "Request field path, e.g. `receiptPhoto`")
	val field: String,
	@field:Schema(description = "The constraint's simple name, e.g. `NotBlank`, `Size`, `Positive`")
	val code: String,
)

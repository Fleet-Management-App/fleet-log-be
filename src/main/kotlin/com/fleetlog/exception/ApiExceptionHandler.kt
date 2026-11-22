package com.fleetlog.exception

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.core.JacksonException

/**
 * Maps exceptions to RFC 9457 problem responses ([ApiProblem] documents the shape). On top of the
 * standard `type`/`title`/`status`/`detail`/`instance`:
 * - `code` — a stable [ErrorCode] for the FE to switch on instead of parsing `detail`. Set by every
 *   [ApiException] that carries one, `VALIDATION_FAILED` for bean validation and `MALFORMED_REQUEST`
 *   for an unreadable body or a mistyped path/query value.
 * - `field` — the request field the error is about, if known: set by domain errors (e.g. `plate` on
 *   `VEHICLE_PLATE_TAKEN`) and by `MALFORMED_REQUEST` when Jackson can tell which property it choked on
 *   (a missing required one, or a value of the wrong type).
 * - `errors` — only with `VALIDATION_FAILED`: `[{ field, code }]`, `code` being the constraint's
 *   simple name (`NotBlank`, `Size`, `Positive`, ...).
 *
 * Access-denied errors are deliberately not handled here: Spring Security turns them into 403 itself.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

	@ExceptionHandler(BadRequestException::class)
	@ApiResponse(
		responseCode = "400",
		description = "Invalid request: VALIDATION_FAILED, MALFORMED_REQUEST or a domain rule",
		content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ApiProblem::class))],
	)
	fun handleBadRequest(e: BadRequestException): ProblemDetail = toProblemDetail(HttpStatus.BAD_REQUEST, e)

	@ExceptionHandler(NotFoundException::class)
	@ApiResponse(
		responseCode = "404",
		description = "Not found in the active organization",
		content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ApiProblem::class))],
	)
	fun handleNotFound(e: NotFoundException): ProblemDetail = toProblemDetail(HttpStatus.NOT_FOUND, e)

	@ExceptionHandler(ConflictException::class)
	@ApiResponse(
		responseCode = "409",
		description = "Conflicts with the current state",
		content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ApiProblem::class))],
	)
	fun handleConflict(e: ConflictException): ProblemDetail = toProblemDetail(HttpStatus.CONFLICT, e)

	@ExceptionHandler(BadCredentialsException::class)
	@ApiResponse(
		responseCode = "401",
		description = "Not authenticated, or wrong e-mail or password on login",
		content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ApiProblem::class))],
	)
	fun handleBadCredentials(e: BadCredentialsException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong e-mail or password")

	override fun handleMethodArgumentNotValid(
		ex: MethodArgumentNotValidException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		// The exception's own body is what the base class sends, so the properties go onto it
		ex.body.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name)
		ex.body.setProperty(ERRORS, ex.bindingResult.fieldErrors.map { ApiFieldError(it.field, it.code ?: "Invalid") })
		return super.handleMethodArgumentNotValid(ex, headers, status, request)
	}

	override fun handleHttpMessageNotReadable(
		ex: HttpMessageNotReadableException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val body = createProblemDetail(ex, status, "Failed to read request", null, null, request)
		body.setProperty(CODE, ErrorCode.MALFORMED_REQUEST.name)
		// e.g. a missing non-nullable property, or text where a number belongs: Jackson knows which one
		val path = (ex.cause as? JacksonException)?.path.orEmpty().mapNotNull { it.propertyName }
		if (path.isNotEmpty()) body.setProperty(FIELD, path.joinToString("."))
		return handleExceptionInternal(ex, body, headers, status, request)
	}

	override fun handleTypeMismatch(
		ex: TypeMismatchException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val body = createProblemDetail(ex, status, "Failed to convert '${ex.propertyName}'", null, null, request)
		body.setProperty(CODE, ErrorCode.MALFORMED_REQUEST.name)
		ex.propertyName?.let { body.setProperty(FIELD, it) }
		return handleExceptionInternal(ex, body, headers, status, request)
	}

	private fun toProblemDetail(status: HttpStatus, e: ApiException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(status, e.message).apply {
			e.code?.let { setProperty(CODE, it.name) }
			e.field?.let { setProperty(FIELD, it) }
		}

	private companion object {
		const val PROBLEM_JSON = "application/problem+json"
		const val CODE = "code"
		const val FIELD = "field"
		const val ERRORS = "errors"
	}

}

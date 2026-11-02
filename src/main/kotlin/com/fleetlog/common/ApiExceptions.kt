package com.fleetlog.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

class NotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

/**
 * Maps domain exceptions to RFC 9457 problem responses. Access-denied errors are deliberately not
 * handled here: Spring Security turns them into 403 itself.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

	@ExceptionHandler(NotFoundException::class)
	fun notFound(e: NotFoundException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)

	@ExceptionHandler(ConflictException::class)
	fun conflict(e: ConflictException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)

	@ExceptionHandler(BadCredentialsException::class)
	fun badCredentials(e: BadCredentialsException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong e-mail or password")

}

package com.fleetlog.exception

/**
 * Base of the domain exceptions [ApiExceptionHandler] turns into problem responses. [code] and
 * [field] end up as the `code` / `field` properties of the response, so the FE can react to an
 * error without parsing the English [message].
 */
abstract class ApiException(message: String, val code: ErrorCode?, val field: String?) : RuntimeException(message)

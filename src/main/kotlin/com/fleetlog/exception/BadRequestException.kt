package com.fleetlog.exception

class BadRequestException(message: String, code: ErrorCode? = null, field: String? = null) : ApiException(message, code, field)

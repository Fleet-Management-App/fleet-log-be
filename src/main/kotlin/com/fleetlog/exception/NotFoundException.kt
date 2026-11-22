package com.fleetlog.exception

class NotFoundException(message: String, code: ErrorCode? = null, field: String? = null) : ApiException(message, code, field)

package com.fleetlog.exception

class ConflictException(message: String, code: ErrorCode? = null, field: String? = null) : ApiException(message, code, field)

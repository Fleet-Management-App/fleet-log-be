package com.fleetlog.util

/** E-mails are compared case-insensitively, so they are stored in one canonical form. */
fun normalizeEmail(email: String) = email.trim().lowercase()

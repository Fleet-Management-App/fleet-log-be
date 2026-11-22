package com.fleetlog.util

private val WHITESPACE = Regex("\\s+")

/** Plates are stored trimmed, upper-case, with inner whitespace collapsed to one space: "1ab  2345" -> "1AB 2345". */
fun normalizePlate(plate: String) = plate.trim().replace(WHITESPACE, " ").uppercase()

/** VINs are stored upper-case with all whitespace removed. */
fun normalizeVin(vin: String) = vin.replace(WHITESPACE, "").uppercase()

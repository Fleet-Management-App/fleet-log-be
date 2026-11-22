package com.fleetlog.util

import java.util.Base64

private const val DATA_PREFIX = "data:"
private const val BASE64_SUFFIX = ";base64"

/** A base64 data URL taken apart (RFC 2397): `data:<mediaType>;base64,<base64>`. Nothing is decoded yet. */
class Base64DataUrl(
	/** Lower-cased, without parameters, e.g. `image/png`. */
	val mediaType: String,
	val base64: String,
) {

	/** The exact number of bytes [base64] decodes to, if it is valid base64, computed without decoding it. */
	fun getDecodedSize(): Long = base64.length.toLong() * 3 / 4 - base64.takeLastWhile { it == '=' }.length

}

/**
 * Splits a `data:<type>/<subtype>;base64,<data>` URL into its media type and base64 part, or returns
 * null when [value] isn't one. Deliberately strict: media type parameters (`;charset=...`) and
 * non-base64 data URLs are not accepted. Whether the base64 part actually decodes isn't checked here.
 */
fun parseBase64DataUrl(value: String): Base64DataUrl? {
	if (!value.startsWith(DATA_PREFIX, ignoreCase = true)) return null
	val comma = value.indexOf(',')
	if (comma < 0) return null
	val header = value.substring(DATA_PREFIX.length, comma)
	if (!header.endsWith(BASE64_SUFFIX, ignoreCase = true)) return null
	val mediaType = header.dropLast(BASE64_SUFFIX.length).lowercase()
	if (mediaType.isEmpty() || ';' in mediaType) return null
	return Base64DataUrl(mediaType, value.substring(comma + 1))
}

/** The inverse of [parseBase64DataUrl]: `data:<mediaType>;base64,<bytes in base64>`. */
fun toBase64DataUrl(mediaType: String, bytes: ByteArray): String =
	"$DATA_PREFIX$mediaType$BASE64_SUFFIX," + Base64.getEncoder().encodeToString(bytes)

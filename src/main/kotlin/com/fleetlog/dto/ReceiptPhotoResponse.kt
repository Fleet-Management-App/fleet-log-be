package com.fleetlog.dto

data class ReceiptPhotoResponse(
	/** The receipt photo as a base64 data URL, rebuilt from the stored image type and bytes. */
	val receiptPhoto: String,
)

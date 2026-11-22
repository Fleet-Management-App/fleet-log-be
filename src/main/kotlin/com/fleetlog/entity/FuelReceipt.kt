package com.fleetlog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * The photo of a trip's fuel receipt (BR-14), kept apart from [Trip] so loading trips never loads the
 * image. One per trip, keyed by the trip's id; tenant scoping comes from the trip, which is always
 * looked up in the tenant first.
 */
@Entity
@Table(name = "fuel_receipt")
class FuelReceipt(
	@Id
	@Column(name = "trip_id")
	val tripId: UUID,

	/** The image's media type, e.g. `image/jpeg`. */
	@Column(name = "content_type", nullable = false)
	val contentType: String,

	// A plain ByteArray is mapped to PostgreSQL `bytea`; @Lob would map it to a large object (`oid`) instead
	@Column(nullable = false)
	val photo: ByteArray,
)

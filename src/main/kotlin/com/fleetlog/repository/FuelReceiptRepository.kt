package com.fleetlog.repository

import com.fleetlog.entity.FuelReceipt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FuelReceiptRepository : JpaRepository<FuelReceipt, UUID> {

	/**
	 * Stores the trip's receipt, replacing any earlier one (a resubmitted fuel entry, BR-07). One upsert
	 * instead of `save()`: with an assigned id, `save()` merges, which would first load the old photo.
	 */
	@Modifying
	@Query(
		nativeQuery = true,
		value = """
		INSERT INTO fuel_receipt (trip_id, content_type, photo) VALUES (:tripId, :contentType, :photo)
		ON CONFLICT (trip_id) DO UPDATE SET content_type = EXCLUDED.content_type, photo = EXCLUDED.photo
		""",
	)
	fun saveOrReplace(tripId: UUID, contentType: String, photo: ByteArray)

}

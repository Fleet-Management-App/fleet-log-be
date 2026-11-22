package com.fleetlog.db

import com.fleetlog.BaseFleetLogBeIntegrationTest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.UUID

/**
 * The 005 changeset moves receipts out of `trip.fuel_receipt_photo`. When the app starts, Liquibase runs
 * it on an empty trip table, so that proves only that it applies. This builds the schema up to 004 in
 * a scratch database (in the shared test container), inserts receipts the old way, then runs 005.
 */
class ReceiptPhotoMigrationTest : DescribeSpec({

	describe("005-receipt-photo-as-bytea") {
		it("moves base64 data-URL receipts into fuel_receipt as bytes and drops what can't be converted") {
			val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
			val oldReceipts = mapOf(
				"png" to "data:image/png;base64,$png",
				"upperCaseJpeg" to "data:IMAGE/JPEG;BASE64,/9j/4A==",
				"pdf" to "data:application/pdf;base64,JVBERi0=", // any type was accepted before; it's still convertible
				"url" to "https://example.com/receipt.png",
				"badBase64" to "data:image/png;base64,not*base64",
				"empty" to "data:image/png;base64,",
				"notBase64" to "data:image/png,rawdata",
				"none" to null,
			)

			withScratchDatabase { db ->
				applyChanges(db) { it < "005" }
				val tripIds = oldReceipts.mapValues { (_, receipt) -> insertTripWithFuel(db, receipt) }

				applyChanges(db) { it.startsWith("005") }

				val receipts = db.query("SELECT trip_id, content_type, photo FROM fuel_receipt") {
					it.getObject("trip_id", UUID::class.java) to (it.getString("content_type") to it.getBytes("photo"))
				}.toMap()
				receipts.keys shouldBe setOf(tripIds.getValue("png"), tripIds.getValue("upperCaseJpeg"), tripIds.getValue("pdf"))
				receipts.getValue(tripIds.getValue("png")).first shouldBe "image/png"
				receipts.getValue(tripIds.getValue("png")).second shouldBe Base64.getDecoder().decode(png)
				receipts.getValue(tripIds.getValue("upperCaseJpeg")).first shouldBe "image/jpeg"
				receipts.getValue(tripIds.getValue("upperCaseJpeg")).second shouldBe Base64.getDecoder().decode("/9j/4A==")
				receipts.getValue(tripIds.getValue("pdf")).first shouldBe "application/pdf"

				// The unconvertible receipts are gone, but their fuel entries stay
				db.query("SELECT fuel_status FROM trip") { it.getString(1) } shouldContainExactly List(oldReceipts.size) { "PENDING" }
				db.query("SELECT column_name FROM information_schema.columns WHERE table_name = 'trip'") { it.getString(1) } shouldNotContain
					"fuel_receipt_photo"
			}
		}
	}

})

/** Runs [block] against a new, empty database, dropped again afterwards. */
private fun withScratchDatabase(block: (Connection) -> Unit) {
	val postgres = BaseFleetLogBeIntegrationTest.postgres
	val name = "migration_test_" + UUID.randomUUID().toString().replace("-", "")
	DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { admin ->
		admin.createStatement().use { it.execute("CREATE DATABASE $name") }
		try {
			val url = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/$name"
			DriverManager.getConnection(url, postgres.username, postgres.password).use(block)
		} finally {
			admin.createStatement().use { it.execute("DROP DATABASE $name WITH (FORCE)") }
		}
	}
}

/** Executes the changelog's SQL files whose names match [include], in order, as Liquibase would. */
private fun applyChanges(db: Connection, include: (String) -> Boolean) {
	PathMatchingResourcePatternResolver().getResources("classpath:db/changelog/changes/*.sql")
		.filter { include(it.filename!!) }
		.sortedBy { it.filename }
		.forEach { file -> db.createStatement().use { it.execute(file.getContentAsString(Charsets.UTF_8)) } }
}

/** A plan-approved trip with a pending fuel entry whose receipt is stored as before 005. */
private fun insertTripWithFuel(db: Connection, receiptPhoto: String?): UUID {
	val organizationId = UUID.randomUUID()
	val userId = UUID.randomUUID()
	val vehicleId = UUID.randomUUID()
	val tripId = UUID.randomUUID()
	db.update("INSERT INTO organization (id, name, created_at) VALUES (?, 'Org', now())", organizationId)
	db.update("INSERT INTO app_user (id, email, password_hash, full_name, created_at) VALUES (?, ?, 'x', 'Driver', now())", userId, "$userId@example.com")
	db.update(
		"""
		INSERT INTO vehicle (id, organization_id, plate, vin, make, model, fuel_type, odometer_km, status, created_at)
		VALUES (?, ?, 'P1', 'VIN1', 'Skoda', 'Octavia', 'DIESEL', 0, 'ACTIVE', now())
		""",
		vehicleId, organizationId,
	)
	db.update(
		"""
		INSERT INTO trip (id, organization_id, vehicle_id, driver_id, date_time, from_location, to_location, purpose, status,
		                  fuel_liters, fuel_price, fuel_receipt_photo, fuel_status, created_at)
		VALUES (?, ?, ?, ?, now(), 'Prague', 'Brno', 'Delivery', 'PLAN_APPROVED', 40, 55, ?, 'PENDING', now())
		""",
		tripId, organizationId, vehicleId, userId, receiptPhoto,
	)
	return tripId
}

private fun Connection.update(sql: String, vararg params: Any?) {
	prepareStatement(sql).use { statement ->
		params.forEachIndexed { i, param -> statement.setObject(i + 1, param) }
		statement.executeUpdate()
	}
}

private fun <T> Connection.query(sql: String, map: (java.sql.ResultSet) -> T): List<T> =
	createStatement().use { statement ->
		statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(map(rows)) } }
	}

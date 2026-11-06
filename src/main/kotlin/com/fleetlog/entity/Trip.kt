package com.fleetlog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A planned/driven trip (SPEC-02). Fuel fields live flat on the trip: there is always at most one
 * fuel entry per trip, and it is never queried on its own, so a separate table would add nothing.
 */
@Entity
@Table(name = "trip")
class Trip(
	@Id
	val id: UUID = UUID.randomUUID(),

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id")
	val organization: Organization,

	// var: a rejected plan can be edited, incl. picking a different vehicle (BR-07)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id")
	var vehicle: Vehicle,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	val driver: AppUser,

	@Column(name = "date_time", nullable = false)
	var dateTime: Instant,

	@Column(name = "from_location", nullable = false)
	var fromLocation: String,

	@Column(name = "to_location", nullable = false)
	var toLocation: String,

	@Column(nullable = false)
	var purpose: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: TripStatus = TripStatus.PLANNED,

	@Column(name = "rejection_reason")
	var rejectionReason: String? = null,

	@Column(name = "odometer_start_km")
	var odometerStartKm: Int? = null,

	@Column(name = "odometer_end_km")
	var odometerEndKm: Int? = null,

	@Column(name = "fuel_liters")
	var fuelLiters: BigDecimal? = null,

	@Column(name = "fuel_price")
	var fuelPrice: BigDecimal? = null,

	@Column(name = "fuel_receipt_photo")
	var fuelReceiptPhoto: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "fuel_status")
	var fuelStatus: FuelStatus? = null,

	@Column(name = "fuel_rejection_reason")
	var fuelRejectionReason: String? = null,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

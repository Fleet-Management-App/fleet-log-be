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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A fleet vehicle, scoped to one organization (SPEC-01). Plate and VIN are unique per tenant. */
@Entity
@Table(name = "vehicle")
class Vehicle(
	@Id
	val id: UUID = UUID.randomUUID(),

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id")
	val organization: Organization,

	@Column(nullable = false)
	var plate: String,

	@Column(nullable = false)
	var vin: String,

	@Column(nullable = false)
	var make: String,

	@Column(nullable = false)
	var model: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "fuel_type", nullable = false)
	var fuelType: VehicleFuelType,

	@Column(name = "odometer_km", nullable = false)
	var odometerKm: Int,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: VehicleStatus = VehicleStatus.ACTIVE,

	@Column(name = "stk_due_date")
	var stkDueDate: LocalDate? = null,

	@Column(name = "insurance_due_date")
	var insuranceDueDate: LocalDate? = null,

	@Column(name = "vignette_due_date")
	var vignetteDueDate: LocalDate? = null,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

package com.fleetlog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A tenant: every piece of fleet data belongs to exactly one organization. */
@Entity
@Table(name = "organization")
class Organization(
	@Id
	val id: UUID = UUID.randomUUID(),

	@Column(nullable = false)
	var name: String,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

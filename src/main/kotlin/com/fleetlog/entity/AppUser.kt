package com.fleetlog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A global account: one per person, not tied to any organization.
 * Access to organizations comes from [com.fleetlog.entity.Membership].
 */
@Entity
@Table(name = "app_user")
class AppUser(
	@Id
	val id: UUID = UUID.randomUUID(),

	/** Always stored lower-case, see [normalizeEmail]. */
	@Column(nullable = false)
	var email: String,

	@Column(name = "password_hash", nullable = false)
	var passwordHash: String,

	@Column(name = "full_name", nullable = false)
	var fullName: String,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

package com.fleetlog.account

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A global account: one per person, not tied to any organization.
 * Access to organizations comes from [com.fleetlog.organization.Membership].
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

/** E-mails are compared case-insensitively, so they are stored in one canonical form. */
fun normalizeEmail(email: String) = email.trim().lowercase()

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
import java.util.UUID

/** A user's access to one organization, with the role they have there. */
@Entity
@Table(name = "membership")
class Membership(
	@Id
	val id: UUID = UUID.randomUUID(),

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	val user: AppUser,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id")
	val organization: Organization,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var role: Role,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

package com.fleetlog.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A user's access to one organization, with the roles they have there. */
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

	/** At least one; a person can hold several roles here (e.g. a Fleet Manager who also drives). */
	@ElementCollection
	@CollectionTable(name = "membership_role", joinColumns = [JoinColumn(name = "membership_id")])
	@Column(name = "role", nullable = false)
	@Enumerated(EnumType.STRING)
	@OrderBy
	val roles: MutableSet<Role>,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

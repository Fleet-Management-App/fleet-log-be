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

/**
 * An invitation to join an organization, addressed to an e-mail. The person may not have an
 * account yet; the invitation waits for them and shows up once they sign up with that e-mail.
 */
@Entity
@Table(name = "invitation")
class Invitation(
	@Id
	val id: UUID = UUID.randomUUID(),

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id")
	val organization: Organization,

	/** Always stored lower-case. */
	@Column(nullable = false)
	val email: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	val role: Role,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: InvitationStatus = InvitationStatus.PENDING,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invited_by")
	val invitedBy: AppUser,

	@Column(name = "created_at", nullable = false)
	val createdAt: Instant = Instant.now(),
)

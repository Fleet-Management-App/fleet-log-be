package com.fleetlog.repository

import com.fleetlog.entity.Invitation
import com.fleetlog.entity.InvitationStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvitationRepository : JpaRepository<Invitation, UUID> {

	@EntityGraph(attributePaths = ["organization", "invitedBy"])
	fun findAllByEmailAndStatusOrderByCreatedAt(email: String, status: InvitationStatus): List<Invitation>

	@EntityGraph(attributePaths = ["invitedBy"])
	fun findAllByOrganizationIdAndStatusOrderByCreatedAt(organizationId: UUID, status: InvitationStatus): List<Invitation>

	fun existsByOrganizationIdAndEmailAndStatus(organizationId: UUID, email: String, status: InvitationStatus): Boolean

	fun findByIdAndOrganizationId(id: UUID, organizationId: UUID): Invitation?

	@EntityGraph(attributePaths = ["organization"])
	fun findWithOrganizationById(id: UUID): Invitation?

}

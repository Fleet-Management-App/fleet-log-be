package com.fleetlog.repository

import com.fleetlog.entity.Membership
import com.fleetlog.entity.Role
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface MembershipRepository : JpaRepository<Membership, UUID> {

	@EntityGraph(attributePaths = ["organization"])
	fun findAllByUserIdOrderByCreatedAt(userId: UUID): List<Membership>

	@EntityGraph(attributePaths = ["organization", "user"])
	fun findByUserIdAndOrganizationId(userId: UUID, organizationId: UUID): Membership?

	@EntityGraph(attributePaths = ["user"])
	fun findAllByOrganizationIdOrderByCreatedAt(organizationId: UUID): List<Membership>

	fun existsByUserIdAndOrganizationId(userId: UUID, organizationId: UUID): Boolean

	fun countByOrganizationIdAndRole(organizationId: UUID, role: Role): Long

	@Query("select m.role from Membership m where m.user.id = :userId and m.organization.id = :organizationId")
	fun findRole(userId: UUID, organizationId: UUID): Role?

}

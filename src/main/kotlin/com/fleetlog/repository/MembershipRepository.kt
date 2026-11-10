package com.fleetlog.repository

import com.fleetlog.entity.Membership
import com.fleetlog.entity.Role
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface MembershipRepository : JpaRepository<Membership, UUID> {

	@EntityGraph(attributePaths = ["organization", "roles"])
	fun findAllByUserIdOrderByCreatedAt(userId: UUID): List<Membership>

	@EntityGraph(attributePaths = ["organization", "user", "roles"])
	fun findByUserIdAndOrganizationId(userId: UUID, organizationId: UUID): Membership?

	@EntityGraph(attributePaths = ["user", "roles"])
	fun findAllByOrganizationIdOrderByCreatedAt(organizationId: UUID): List<Membership>

	fun existsByUserIdAndOrganizationId(userId: UUID, organizationId: UUID): Boolean

	@Query("select count(m) from Membership m where m.organization.id = :organizationId and :role member of m.roles")
	fun countByOrganizationIdAndRole(organizationId: UUID, role: Role): Long

	/** The user's roles in the organization; empty when they are not a member. */
	@Query("select r from Membership m join m.roles r where m.user.id = :userId and m.organization.id = :organizationId")
	fun findRoles(userId: UUID, organizationId: UUID): List<Role>

}

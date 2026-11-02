package com.fleetlog.organization

import com.fleetlog.account.AppUserRepository
import com.fleetlog.account.normalizeEmail
import com.fleetlog.common.ConflictException
import com.fleetlog.common.NotFoundException
import com.fleetlog.security.AuthTokenResponse
import com.fleetlog.security.CurrentUser
import com.fleetlog.security.TokenService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Creating organizations and managing the people of the active one (SPEC-08). */
@Service
@Transactional
class OrganizationService(
	private val currentUser: CurrentUser,
	private val userRepository: AppUserRepository,
	private val organizationRepository: OrganizationRepository,
	private val membershipRepository: MembershipRepository,
	private val invitationRepository: InvitationRepository,
	private val organizationMapper: OrganizationMapper,
	private val tokenService: TokenService,
) {

	/** Any signed-in user can create an organization; they become its first Fleet Manager and switch to it. */
	fun create(request: CreateOrganizationRequest): AuthTokenResponse {
		val user = userRepository.findById(currentUser.userId()).orElseThrow { NotFoundException("Account not found") }
		val organization = organizationRepository.save(Organization(name = request.name.trim()))
		val membership = membershipRepository.save(Membership(user = user, organization = organization, role = Role.FLEET_MANAGER))
		return tokenService.issue(user, membership)
	}

	@Transactional(readOnly = true)
	fun members(): List<MemberResponse> =
		membershipRepository.findAllByOrganizationIdOrderByCreatedAt(currentUser.tenantId())
			.map(organizationMapper::toMemberResponse)

	fun changeRole(userId: UUID, request: ChangeRoleRequest): MemberResponse {
		val membership = findMember(userId)
		if (request.role != Role.FLEET_MANAGER) ensureNotLastFleetManager(membership)
		membership.role = request.role
		return organizationMapper.toMemberResponse(membership)
	}

	/** Removes access only; the account and everything the person recorded in the logbook stay. */
	fun removeMember(userId: UUID) {
		val membership = findMember(userId)
		ensureNotLastFleetManager(membership)
		membershipRepository.delete(membership)
	}

	fun invite(request: CreateInvitationRequest): InvitationResponse {
		val tenantId = currentUser.tenantId()
		val email = normalizeEmail(request.email)
		val existingUser = userRepository.findByEmail(email)
		if (existingUser != null && membershipRepository.existsByUserIdAndOrganizationId(existingUser.id, tenantId)) {
			throw ConflictException("This person is already a member of the organization")
		}
		if (invitationRepository.existsByOrganizationIdAndEmailAndStatus(tenantId, email, InvitationStatus.PENDING)) {
			throw ConflictException("This e-mail already has a pending invitation")
		}
		val invitation = invitationRepository.save(
			Invitation(
				organization = organizationRepository.getReferenceById(tenantId),
				email = email,
				role = request.role,
				invitedBy = userRepository.getReferenceById(currentUser.userId()),
			),
		)
		return organizationMapper.toInvitationResponse(invitation)
	}

	@Transactional(readOnly = true)
	fun pendingInvitations(): List<InvitationResponse> =
		invitationRepository.findAllByOrganizationIdAndStatusOrderByCreatedAt(currentUser.tenantId(), InvitationStatus.PENDING)
			.map(organizationMapper::toInvitationResponse)

	fun revokeInvitation(invitationId: UUID) {
		val invitation = invitationRepository.findByIdAndOrganizationId(invitationId, currentUser.tenantId())
			?: throw NotFoundException("Invitation not found")
		if (invitation.status != InvitationStatus.PENDING) throw ConflictException("Only a pending invitation can be revoked")
		invitation.status = InvitationStatus.REVOKED
	}

	private fun findMember(userId: UUID): Membership =
		membershipRepository.findByUserIdAndOrganizationId(userId, currentUser.tenantId())
			?: throw NotFoundException("Member not found")

	// Not in the Business Rules page yet (gap to flag): without a Fleet Manager nobody could manage the organization
	private fun ensureNotLastFleetManager(membership: Membership) {
		if (membership.role == Role.FLEET_MANAGER &&
			membershipRepository.countByOrganizationIdAndRole(membership.organization.id, Role.FLEET_MANAGER) <= 1
		) {
			throw ConflictException("The organization needs at least one Fleet Manager")
		}
	}

}

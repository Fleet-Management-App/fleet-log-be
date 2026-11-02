package com.fleetlog.organization

import com.fleetlog.account.AppUser
import com.fleetlog.account.AppUserRepository
import com.fleetlog.common.ConflictException
import com.fleetlog.common.NotFoundException
import com.fleetlog.security.AuthTokenResponse
import com.fleetlog.security.CurrentUser
import com.fleetlog.security.TokenService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** The invited person's side: accepting or declining an invitation addressed to their e-mail. */
@Service
@Transactional
class InvitationService(
	private val currentUser: CurrentUser,
	private val userRepository: AppUserRepository,
	private val membershipRepository: MembershipRepository,
	private val invitationRepository: InvitationRepository,
	private val tokenService: TokenService,
) {

	/** Joins the organization and switches the caller to it. */
	fun accept(invitationId: UUID): AuthTokenResponse {
		val (user, invitation) = findOwnPendingInvitation(invitationId)
		if (membershipRepository.existsByUserIdAndOrganizationId(user.id, invitation.organization.id)) {
			throw ConflictException("You are already a member of this organization")
		}
		invitation.status = InvitationStatus.ACCEPTED
		val membership = membershipRepository.save(
			Membership(user = user, organization = invitation.organization, role = invitation.role),
		)
		return tokenService.issue(user, membership)
	}

	fun decline(invitationId: UUID) {
		findOwnPendingInvitation(invitationId).second.status = InvitationStatus.DECLINED
	}

	private fun findOwnPendingInvitation(invitationId: UUID): Pair<AppUser, Invitation> {
		val user = userRepository.findById(currentUser.userId()).orElseThrow { NotFoundException("Account not found") }
		// Someone else's invitation answers 404 like a missing one, so ids can't be probed
		val invitation = invitationRepository.findWithOrganizationById(invitationId)
			?.takeIf { it.email == user.email }
			?: throw NotFoundException("Invitation not found")
		if (invitation.status != InvitationStatus.PENDING) throw ConflictException("This invitation is no longer pending")
		return user to invitation
	}

}

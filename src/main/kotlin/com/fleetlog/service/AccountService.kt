package com.fleetlog.service

import com.fleetlog.dto.MeResponse
import com.fleetlog.entity.InvitationStatus
import com.fleetlog.exception.NotFoundException
import com.fleetlog.mapper.OrganizationMapper
import com.fleetlog.repository.AppUserRepository
import com.fleetlog.repository.InvitationRepository
import com.fleetlog.repository.MembershipRepository
import com.fleetlog.security.CurrentUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AccountService(
	private val currentUser: CurrentUser,
	private val userRepository: AppUserRepository,
	private val membershipRepository: MembershipRepository,
	private val invitationRepository: InvitationRepository,
	private val organizationMapper: OrganizationMapper,
) {

	fun me(): MeResponse {
		val principal = currentUser.principal()
		val user = userRepository.findById(principal.userId).orElseThrow { NotFoundException("Account not found") }
		val memberships = membershipRepository.findAllByUserIdOrderByCreatedAt(user.id)
		val invitations = invitationRepository.findAllByEmailAndStatusOrderByCreatedAt(user.email, InvitationStatus.PENDING)
		return MeResponse(
			id = user.id,
			email = user.email,
			fullName = user.fullName,
			activeOrganization = memberships.find { it.organization.id == principal.tenantId }
				?.let(organizationMapper::toMembershipResponse),
			memberships = memberships.map(organizationMapper::toMembershipResponse),
			pendingInvitations = invitations.map(organizationMapper::toIncomingInvitationResponse),
		)
	}

}

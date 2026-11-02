package com.fleetlog.organization

import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class OrganizationController(
	private val organizationService: OrganizationService,
) : OrganizationApi {

	override fun createOrganization(request: CreateOrganizationRequest) = organizationService.create(request)

}

// A separate controller: Spring takes only one class-level @RequestMapping per controller
@RestController
class OrganizationMembersController(
	private val organizationService: OrganizationService,
) : OrganizationMembersApi {

	override fun listMembers() = organizationService.members()

	override fun changeMemberRole(userId: UUID, request: ChangeRoleRequest) = organizationService.changeRole(userId, request)

	override fun removeMember(userId: UUID) = organizationService.removeMember(userId)

	override fun listInvitations() = organizationService.pendingInvitations()

	override fun createInvitation(request: CreateInvitationRequest) = organizationService.invite(request)

	override fun revokeInvitation(invitationId: UUID) = organizationService.revokeInvitation(invitationId)

}

@RestController
class InvitationController(
	private val invitationService: InvitationService,
) : InvitationApi {

	override fun acceptInvitation(invitationId: UUID) = invitationService.accept(invitationId)

	override fun declineInvitation(invitationId: UUID) = invitationService.decline(invitationId)

}

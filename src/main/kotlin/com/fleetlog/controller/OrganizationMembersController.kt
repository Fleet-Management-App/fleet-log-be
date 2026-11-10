package com.fleetlog.controller

import com.fleetlog.controller.api.OrganizationMembersApi
import com.fleetlog.dto.ChangeRolesRequest
import com.fleetlog.dto.CreateInvitationRequest
import com.fleetlog.service.OrganizationService
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// A separate controller: Spring takes only one class-level @RequestMapping per controller
@RestController
class OrganizationMembersController(
	private val organizationService: OrganizationService,
) : OrganizationMembersApi {

	override fun listMembers() = organizationService.getMembers()

	override fun changeMemberRoles(userId: UUID, request: ChangeRolesRequest) = organizationService.changeRoles(userId, request)

	override fun removeMember(userId: UUID) = organizationService.removeMember(userId)

	override fun listInvitations() = organizationService.getPendingInvitations()

	override fun createInvitation(request: CreateInvitationRequest) = organizationService.invite(request)

	override fun revokeInvitation(invitationId: UUID) = organizationService.revokeInvitation(invitationId)

}

package com.fleetlog.organization

import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface OrganizationMapper {

	@Mapping(source = "organization.id", target = "organizationId")
	@Mapping(source = "organization.name", target = "organizationName")
	fun toMembershipResponse(membership: Membership): MembershipResponse

	@Mapping(source = "user.id", target = "userId")
	@Mapping(source = "user.fullName", target = "fullName")
	@Mapping(source = "user.email", target = "email")
	@Mapping(source = "createdAt", target = "joinedAt")
	fun toMemberResponse(membership: Membership): MemberResponse

	@Mapping(source = "invitedBy.fullName", target = "invitedByName")
	fun toInvitationResponse(invitation: Invitation): InvitationResponse

	@Mapping(source = "organization.id", target = "organizationId")
	@Mapping(source = "organization.name", target = "organizationName")
	@Mapping(source = "invitedBy.fullName", target = "invitedByName")
	fun toIncomingInvitationResponse(invitation: Invitation): IncomingInvitationResponse

}

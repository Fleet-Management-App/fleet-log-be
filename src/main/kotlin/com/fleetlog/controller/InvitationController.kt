package com.fleetlog.controller

import com.fleetlog.controller.api.InvitationApi
import com.fleetlog.service.InvitationService
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class InvitationController(
	private val invitationService: InvitationService,
) : InvitationApi {

	override fun acceptInvitation(invitationId: UUID) = invitationService.accept(invitationId)

	override fun declineInvitation(invitationId: UUID) = invitationService.decline(invitationId)

}

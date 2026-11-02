package com.fleetlog.controller.api

import com.fleetlog.dto.AuthTokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@Tag(name = "Invitations", description = "Invitations addressed to the caller")
@RequestMapping("/api/invitations")
interface InvitationApi {

	@Operation(summary = "Accept an invitation; the caller joins the organization and switches to it")
	@PostMapping("/{invitationId}/accept")
	fun acceptInvitation(@PathVariable invitationId: UUID): AuthTokenResponse

	@Operation(summary = "Decline an invitation")
	@PostMapping("/{invitationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun declineInvitation(@PathVariable invitationId: UUID)

}

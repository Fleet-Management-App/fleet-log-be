package com.fleetlog.controller.api

import com.fleetlog.dto.ChangeRoleRequest
import com.fleetlog.dto.CreateInvitationRequest
import com.fleetlog.dto.InvitationResponse
import com.fleetlog.dto.MemberResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@Tag(name = "Organization members", description = "People of the active organization (Fleet Manager only)")
@RequestMapping("/api/organization")
interface OrganizationMembersApi {

	@Operation(summary = "List the members of the active organization")
	@GetMapping("/members")
	fun listMembers(): List<MemberResponse>

	@Operation(summary = "Change a member's role")
	@ApiResponse(responseCode = "409", description = "Would leave the organization without a Fleet Manager", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
	@PatchMapping("/members/{userId}")
	fun changeMemberRole(@PathVariable userId: UUID, @Valid @RequestBody request: ChangeRoleRequest): MemberResponse

	@Operation(summary = "Remove a member from the organization (their account stays)")
	@ApiResponse(responseCode = "409", description = "Would leave the organization without a Fleet Manager", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
	@DeleteMapping("/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun removeMember(@PathVariable userId: UUID)

	@Operation(summary = "List the pending invitations of the active organization")
	@GetMapping("/invitations")
	fun listInvitations(): List<InvitationResponse>

	@Operation(summary = "Invite a person by e-mail")
	@ApiResponse(responseCode = "409", description = "Already a member, or already invited", content = [Content(schema = Schema(implementation = ProblemDetail::class))])
	@PostMapping("/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	fun createInvitation(@Valid @RequestBody request: CreateInvitationRequest): InvitationResponse

	@Operation(summary = "Revoke a pending invitation")
	@DeleteMapping("/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun revokeInvitation(@PathVariable invitationId: UUID)

}

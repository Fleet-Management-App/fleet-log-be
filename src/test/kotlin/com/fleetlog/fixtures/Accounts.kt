package com.fleetlog.fixtures

import com.fleetlog.dto.CreateInvitationRequest
import com.fleetlog.dto.CreateOrganizationRequest
import com.fleetlog.dto.LoginRequest
import com.fleetlog.dto.MeResponse
import com.fleetlog.dto.SignUpRequest
import com.fleetlog.entity.Role
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.specification.RequestSpecification
import java.util.UUID

/** Creates test accounts and organization memberships through the real HTTP auth/organization flows. */
object Accounts {

	const val PASSWORD = "password123"

	/** A fresh e-mail per call: all specs share one database. */
	fun uniqueEmail(prefix: String = "user") = "$prefix-${UUID.randomUUID()}@test.fleetlog.app"

	fun signUp(email: String = uniqueEmail(), fullName: String = "Test User"): String =
		Given { body(SignUpRequest(email, PASSWORD, fullName)) } When {
			post("/api/auth/sign-up")
		} Then {
			statusCode(201)
		} Extract { path("accessToken") }

	fun login(email: String): String =
		Given { body(LoginRequest(email, PASSWORD)) } When {
			post("/api/auth/login")
		} Then {
			statusCode(200)
		} Extract { path("accessToken") }

	/** Creates an organization; [token] becomes its first Fleet Manager. [token] defaults to a fresh account. */
	fun createOrganization(token: String = signUp(), name: String = "Org ${UUID.randomUUID()}"): String =
		Given { withBearer(token).body(CreateOrganizationRequest(name)) } When {
			post("/api/organizations")
		} Then {
			statusCode(201)
		} Extract { path("accessToken") }

	fun invite(fleetManagerToken: String, role: Role, email: String = uniqueEmail(role.name.lowercase())): String =
		Given { withBearer(fleetManagerToken).body(CreateInvitationRequest(email, role)) } When {
			post("/api/organization/invitations")
		} Then {
			statusCode(201)
		} Extract { path("id") }

	fun accept(token: String, invitationId: String): String =
		Given { withBearer(token) } When {
			post("/api/invitations/$invitationId/accept")
		} Then {
			statusCode(200)
		} Extract { path("accessToken") }

	/** A new account joining [fleetManagerToken]'s organization with [role]. */
	fun memberOf(fleetManagerToken: String, role: Role, email: String = uniqueEmail(role.name.lowercase())): String =
		accept(signUp(email), invite(fleetManagerToken, role, email))

	/**
	 * A caller with [role] — in a brand-new organization of their own, unless [organizationOf] (any
	 * existing member's token) is given, in which case they join that organization instead.
	 */
	fun tokenFor(role: Role, organizationOf: String? = null): String {
		if (organizationOf == null && role == Role.FLEET_MANAGER) return createOrganization()
		return memberOf(organizationOf ?: createOrganization(), role)
	}

	fun getMe(token: String): MeResponse =
		Given { withBearer(token) } When {
			get("/api/me")
		} Then {
			statusCode(200)
		} Extract { `as`(MeResponse::class.java) }

	fun getUserId(token: String): String = getMe(token).id.toString()

	fun getActiveOrganizationId(token: String): String =
		requireNotNull(getMe(token).activeOrganization).organizationId.toString()

}

/** Usable directly inside a `Given { }` block, when the test only cares about the role, not the caller's identity. */
fun RequestSpecification.asRole(role: Role, organizationOf: String? = null): RequestSpecification =
	withBearer(Accounts.tokenFor(role, organizationOf))

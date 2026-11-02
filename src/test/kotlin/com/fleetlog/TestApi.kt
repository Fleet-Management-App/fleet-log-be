package com.fleetlog

import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.specification.RequestSpecification
import java.util.UUID

/** Small helpers for the account flows, so specs read as scenarios. */
object TestApi {

	const val PASSWORD = "password123"

	/** A fresh e-mail per call: all specs share one database. */
	fun uniqueEmail(prefix: String = "user") = "$prefix-${UUID.randomUUID()}@test.fleetlog.app"

	fun RequestSpecification.bearer(token: String): RequestSpecification = header("Authorization", "Bearer $token")

	fun RequestSpecification.json(body: Any): RequestSpecification = contentType(ContentType.JSON).body(body)

	fun signUp(email: String = uniqueEmail(), fullName: String = "Test User"): String =
		Given { json(mapOf("email" to email, "password" to PASSWORD, "fullName" to fullName)) } When {
			post("/api/auth/sign-up")
		} Then {
			statusCode(201)
		} Extract { path("accessToken") }

	fun login(email: String): String =
		Given { json(mapOf("email" to email, "password" to PASSWORD)) } When {
			post("/api/auth/login")
		} Then {
			statusCode(200)
		} Extract { path("accessToken") }

	/** Signs up and creates an organization; returns the Fleet Manager's token for it. */
	fun fleetManagerOfNewOrganization(name: String = "Org ${UUID.randomUUID()}"): String =
		createOrganization(signUp(), name)

	fun createOrganization(token: String, name: String): String =
		Given { bearer(token).json(mapOf("name" to name)) } When {
			post("/api/organizations")
		} Then {
			statusCode(201)
		} Extract { path("accessToken") }

	fun invite(fleetManagerToken: String, email: String, role: String): String =
		Given { bearer(fleetManagerToken).json(mapOf("email" to email, "role" to role)) } When {
			post("/api/organization/invitations")
		} Then {
			statusCode(201)
		} Extract { path("id") }

	fun accept(token: String, invitationId: String): String =
		Given { bearer(token) } When {
			post("/api/invitations/$invitationId/accept")
		} Then {
			statusCode(200)
		} Extract { path("accessToken") }

	/** A new account that joined the Fleet Manager's organization with [role]; returns its token. */
	fun memberOf(fleetManagerToken: String, role: String, email: String = uniqueEmail(role.lowercase())): String {
		val invitationId = invite(fleetManagerToken, email, role)
		return accept(signUp(email), invitationId)
	}

	fun me(token: String): Map<String, Any?> =
		Given { bearer(token) } When {
			get("/api/me")
		} Then {
			statusCode(200)
		} Extract { body().jsonPath().getMap("") }

	fun userId(token: String): String = me(token)["id"] as String

	fun activeOrganizationId(token: String): String =
		@Suppress("UNCHECKED_CAST")
		(me(token)["activeOrganization"] as Map<String, Any?>)["organizationId"] as String

}

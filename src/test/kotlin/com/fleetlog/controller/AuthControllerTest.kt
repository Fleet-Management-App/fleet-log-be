package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.TestApi
import com.fleetlog.TestApi.PASSWORD
import com.fleetlog.TestApi.withBearer
import com.fleetlog.TestApi.withJson
import com.fleetlog.TestApi.generateUniqueEmail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import java.util.UUID

class AuthControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/auth/sign-up") {
		it("creates an account without an organization") {
			val email = generateUniqueEmail()
			val me = TestApi.getMe(TestApi.signUp(email, "Jana Nováková"))

			me["email"] shouldBe email
			me["fullName"] shouldBe "Jana Nováková"
			me["activeOrganization"].shouldBeNull()
			(me["memberships"] as List<*>).shouldHaveSize(0)
		}

		it("refuses an e-mail that is already used, whatever its letter case") {
			val email = generateUniqueEmail()
			TestApi.signUp(email)

			Given { withJson(mapOf("email" to email.uppercase(), "password" to PASSWORD, "fullName" to "Copy")) } When {
				post("/api/auth/sign-up")
			} Then {
				statusCode(409)
			}
		}

		it("refuses a password shorter than 8 characters") {
			Given { withJson(mapOf("email" to generateUniqueEmail(), "password" to "short", "fullName" to "X")) } When {
				post("/api/auth/sign-up")
			} Then {
				statusCode(400)
			}
		}
	}

	describe("POST /api/auth/login") {
		it("returns a token for the right password") {
			val email = generateUniqueEmail()
			TestApi.signUp(email)

			TestApi.getMe(TestApi.login(email))["email"] shouldBe email
		}

		it("returns 401 for a wrong password") {
			val email = generateUniqueEmail()
			TestApi.signUp(email)

			Given { withJson(mapOf("email" to email, "password" to "wrong-password")) } When {
				post("/api/auth/login")
			} Then {
				statusCode(401)
			}
		}

		it("returns 401 for an unknown e-mail") {
			Given { withJson(mapOf("email" to generateUniqueEmail(), "password" to PASSWORD)) } When {
				post("/api/auth/login")
			} Then {
				statusCode(401)
			}
		}

		it("makes the first organization the user joined active") {
			val email = generateUniqueEmail()
			val first = TestApi.createOrganization(TestApi.signUp(email), "First")
			TestApi.createOrganization(first, "Second")

			TestApi.getActiveOrganizationId(TestApi.login(email)) shouldBe TestApi.getActiveOrganizationId(first)
		}
	}

	describe("a protected endpoint") {
		it("returns 401 without a token") {
			When { get("/api/me") } Then { statusCode(401) }
		}

		it("returns 401 for a forged token") {
			Given { withBearer("not.a.jwt") } When { get("/api/me") } Then { statusCode(401) }
		}
	}

	describe("POST /api/auth/switch-organization") {
		it("switches to another organization of the caller") {
			val first = TestApi.createFleetManagerOfNewOrganization()
			val second = TestApi.createOrganization(first, "Second")
			val secondId = TestApi.getActiveOrganizationId(second)

			val switched: String = Given { withBearer(first).withJson(mapOf("organizationId" to secondId)) } When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(200)
			} Extract { path("accessToken") }

			TestApi.getActiveOrganizationId(switched) shouldBe secondId
		}

		it("returns 403 for an organization the caller doesn't belong to") {
			val stranger = TestApi.signUp()
			val otherOrganizationId = TestApi.getActiveOrganizationId(TestApi.createFleetManagerOfNewOrganization())

			Given { withBearer(stranger).withJson(mapOf("organizationId" to otherOrganizationId)) } When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(403)
			}
		}

		it("returns 403 for an organization that doesn't exist") {
			Given { withBearer(TestApi.signUp()).withJson(mapOf("organizationId" to UUID.randomUUID())) } When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(403)
			}
		}
	}

})

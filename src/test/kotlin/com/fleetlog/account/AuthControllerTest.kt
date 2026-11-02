package com.fleetlog.account

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.TestApi
import com.fleetlog.TestApi.PASSWORD
import com.fleetlog.TestApi.bearer
import com.fleetlog.TestApi.json
import com.fleetlog.TestApi.uniqueEmail
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
			val email = uniqueEmail()
			val me = TestApi.me(TestApi.signUp(email, "Jana Nováková"))

			me["email"] shouldBe email
			me["fullName"] shouldBe "Jana Nováková"
			me["activeOrganization"].shouldBeNull()
			(me["memberships"] as List<*>).shouldHaveSize(0)
		}

		it("refuses an e-mail that is already used, whatever its letter case") {
			val email = uniqueEmail()
			TestApi.signUp(email)

			Given { json(mapOf("email" to email.uppercase(), "password" to PASSWORD, "fullName" to "Copy")) } When {
				post("/api/auth/sign-up")
			} Then {
				statusCode(409)
			}
		}

		it("refuses a password shorter than 8 characters") {
			Given { json(mapOf("email" to uniqueEmail(), "password" to "short", "fullName" to "X")) } When {
				post("/api/auth/sign-up")
			} Then {
				statusCode(400)
			}
		}
	}

	describe("POST /api/auth/login") {
		it("returns a token for the right password") {
			val email = uniqueEmail()
			TestApi.signUp(email)

			TestApi.me(TestApi.login(email))["email"] shouldBe email
		}

		it("returns 401 for a wrong password") {
			val email = uniqueEmail()
			TestApi.signUp(email)

			Given { json(mapOf("email" to email, "password" to "wrong-password")) } When {
				post("/api/auth/login")
			} Then {
				statusCode(401)
			}
		}

		it("returns 401 for an unknown e-mail") {
			Given { json(mapOf("email" to uniqueEmail(), "password" to PASSWORD)) } When {
				post("/api/auth/login")
			} Then {
				statusCode(401)
			}
		}

		it("makes the first organization the user joined active") {
			val email = uniqueEmail()
			val first = TestApi.createOrganization(TestApi.signUp(email), "First")
			TestApi.createOrganization(first, "Second")

			TestApi.activeOrganizationId(TestApi.login(email)) shouldBe TestApi.activeOrganizationId(first)
		}
	}

	describe("a protected endpoint") {
		it("returns 401 without a token") {
			When { get("/api/me") } Then { statusCode(401) }
		}

		it("returns 401 for a forged token") {
			Given { bearer("not.a.jwt") } When { get("/api/me") } Then { statusCode(401) }
		}
	}

	describe("POST /api/auth/switch-organization") {
		it("switches to another organization of the caller") {
			val first = TestApi.fleetManagerOfNewOrganization()
			val second = TestApi.createOrganization(first, "Second")
			val secondId = TestApi.activeOrganizationId(second)

			val switched: String = Given { bearer(first).json(mapOf("organizationId" to secondId)) } When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(200)
			} Extract { path("accessToken") }

			TestApi.activeOrganizationId(switched) shouldBe secondId
		}

		it("returns 403 for an organization the caller doesn't belong to") {
			val stranger = TestApi.signUp()
			val otherOrganizationId = TestApi.activeOrganizationId(TestApi.fleetManagerOfNewOrganization())

			Given { bearer(stranger).json(mapOf("organizationId" to otherOrganizationId)) } When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(403)
			}
		}

		it("returns 403 for an organization that doesn't exist") {
			Given { bearer(TestApi.signUp()).json(mapOf("organizationId" to UUID.randomUUID())) } When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(403)
			}
		}
	}

})

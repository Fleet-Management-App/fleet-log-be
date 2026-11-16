package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.dto.LoginRequest
import com.fleetlog.dto.SignUpRequest
import com.fleetlog.dto.SwitchOrganizationRequest
import com.fleetlog.fixtures.Accounts
import com.fleetlog.fixtures.Accounts.PASSWORD
import com.fleetlog.fixtures.Accounts.uniqueEmail
import com.fleetlog.fixtures.withBearer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.springframework.http.HttpStatus
import java.util.UUID

class AuthControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/auth/sign-up") {
		it("creates an account without an organization") {
			val email = uniqueEmail()
			val me = Accounts.getMe(Accounts.signUp(email, "Jana Nováková"))

			me.email shouldBe email
			me.fullName shouldBe "Jana Nováková"
			me.activeOrganization shouldBe null
			me.memberships shouldHaveSize 0
		}

		it("refuses an e-mail that is already used, whatever its letter case") {
			val email = uniqueEmail()
			Accounts.signUp(email)

			Given {
				body(SignUpRequest(email.uppercase(), PASSWORD, "Copy"))
			} When {
				post("/api/auth/sign-up")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("refuses a password shorter than 8 characters") {
			Given {
				body(SignUpRequest(uniqueEmail(), "short", "X"))
			} When {
				post("/api/auth/sign-up")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
			}
		}
	}

	describe("POST /api/auth/login") {
		it("returns a token for the right password") {
			val email = uniqueEmail()
			Accounts.signUp(email)

			Accounts.getMe(Accounts.login(email)).email shouldBe email
		}

		it("returns 401 for a wrong password") {
			val email = uniqueEmail()
			Accounts.signUp(email)

			Given {
				body(LoginRequest(email, "wrong-password"))
			} When {
				post("/api/auth/login")
			} Then {
				statusCode(HttpStatus.UNAUTHORIZED.value())
			}
		}

		it("returns 401 for an unknown e-mail") {
			Given {
				body(LoginRequest(uniqueEmail(), PASSWORD))
			} When {
				post("/api/auth/login")
			} Then {
				statusCode(HttpStatus.UNAUTHORIZED.value())
			}
		}

		it("makes the first organization the user joined active") {
			val email = uniqueEmail()
			val first = Accounts.createOrganization(Accounts.signUp(email), "First")
			Accounts.createOrganization(first, "Second")

			Accounts.getActiveOrganizationId(Accounts.login(email)) shouldBe Accounts.getActiveOrganizationId(first)
		}
	}

	describe("a protected endpoint") {
		it("returns 401 without a token") {
			When {
				get("/api/me")
			} Then {
				statusCode(HttpStatus.UNAUTHORIZED.value())
			}
		}

		it("returns 401 for a forged token") {
			Given {
				withBearer("not.a.jwt")
			} When {
				get("/api/me")
			} Then {
				statusCode(HttpStatus.UNAUTHORIZED.value())
			}
		}
	}

	describe("POST /api/auth/switch-organization") {
		it("switches to another organization of the caller") {
			val first = Accounts.createOrganization()
			val second = Accounts.createOrganization(first, "Second")
			val secondId = Accounts.getActiveOrganizationId(second)

			val switched: String = Given {
				withBearer(first).body(SwitchOrganizationRequest(UUID.fromString(secondId)))
			} When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				path("accessToken")
			}

			Accounts.getActiveOrganizationId(switched) shouldBe secondId
		}

		it("returns 403 for an organization the caller doesn't belong to") {
			val stranger = Accounts.signUp()
			val otherOrganizationId = Accounts.getActiveOrganizationId(Accounts.createOrganization())

			Given {
				withBearer(stranger).body(SwitchOrganizationRequest(UUID.fromString(otherOrganizationId)))
			} When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("returns 403 for an organization that doesn't exist") {
			Given {
				withBearer(Accounts.signUp()).body(SwitchOrganizationRequest(UUID.randomUUID()))
			} When {
				post("/api/auth/switch-organization")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}
	}

})

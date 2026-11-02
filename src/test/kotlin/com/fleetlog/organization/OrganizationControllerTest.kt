package com.fleetlog.organization

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.TestApi
import com.fleetlog.TestApi.bearer
import com.fleetlog.TestApi.json
import com.fleetlog.TestApi.uniqueEmail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.Matchers.hasSize

class OrganizationControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/organizations") {
		it("makes the creator the first Fleet Manager and switches them to it") {
			val token = TestApi.fleetManagerOfNewOrganization("Demo Fleet")

			@Suppress("UNCHECKED_CAST")
			val active = TestApi.me(token)["activeOrganization"] as Map<String, Any?>
			active["organizationName"] shouldBe "Demo Fleet"
			active["role"] shouldBe "FLEET_MANAGER"
		}
	}

	describe("role checks on /api/organization/**") {
		it("return 403 for a token without an organization") {
			Given { bearer(TestApi.signUp()) } When { get("/api/organization/members") } Then { statusCode(403) }
		}

		it("return 403 for a Driver") {
			val driver = TestApi.memberOf(TestApi.fleetManagerOfNewOrganization(), "DRIVER")

			Given { bearer(driver) } When { get("/api/organization/members") } Then { statusCode(403) }
		}

		it("return 403 for an Accountant") {
			val accountant = TestApi.memberOf(TestApi.fleetManagerOfNewOrganization(), "ACCOUNTANT")

			Given { bearer(accountant) } When { get("/api/organization/members") } Then { statusCode(403) }
		}
	}

	describe("invitations") {
		it("lets an invited person sign up later, accept and join with the invited role") {
			val fleetManager = TestApi.fleetManagerOfNewOrganization()
			val email = uniqueEmail("driver")
			val invitationId = TestApi.invite(fleetManager, email, "DRIVER")

			val newcomer = TestApi.signUp(email)
			(TestApi.me(newcomer)["pendingInvitations"] as List<*>).shouldHaveSize(1)

			val joined = TestApi.accept(newcomer, invitationId)
			@Suppress("UNCHECKED_CAST")
			(TestApi.me(joined)["activeOrganization"] as Map<String, Any?>)["role"] shouldBe "DRIVER"
			Given { bearer(fleetManager) } When { get("/api/organization/members") } Then { body("", hasSize<Any>(2)) }
		}

		it("refuses to invite someone who is already a member") {
			val fleetManager = TestApi.fleetManagerOfNewOrganization()
			val email = uniqueEmail()
			TestApi.memberOf(fleetManager, "DRIVER", email)

			Given { bearer(fleetManager).json(mapOf("email" to email, "role" to "ACCOUNTANT")) } When {
				post("/api/organization/invitations")
			} Then {
				statusCode(409)
			}
		}

		it("hides an invitation from anyone but the invited e-mail") {
			val invitationId = TestApi.invite(TestApi.fleetManagerOfNewOrganization(), uniqueEmail(), "DRIVER")

			Given { bearer(TestApi.signUp()) } When { post("/api/invitations/$invitationId/accept") } Then { statusCode(404) }
		}

		it("can't be accepted once revoked") {
			val fleetManager = TestApi.fleetManagerOfNewOrganization()
			val email = uniqueEmail()
			val invitationId = TestApi.invite(fleetManager, email, "DRIVER")
			Given { bearer(fleetManager) } When { delete("/api/organization/invitations/$invitationId") } Then { statusCode(204) }

			Given { bearer(TestApi.signUp(email)) } When { post("/api/invitations/$invitationId/accept") } Then { statusCode(409) }
		}
	}

	describe("tenant isolation") {
		it("doesn't let a Fleet Manager touch another organization's member") {
			val otherFleetManager = TestApi.fleetManagerOfNewOrganization()
			val otherDriverId = TestApi.userId(TestApi.memberOf(otherFleetManager, "DRIVER"))

			Given { bearer(TestApi.fleetManagerOfNewOrganization()).json(mapOf("role" to "FLEET_MANAGER")) } When {
				patch("/api/organization/members/$otherDriverId")
			} Then {
				statusCode(404)
			}
		}

		it("doesn't let a Fleet Manager revoke another organization's invitation") {
			val invitationId = TestApi.invite(TestApi.fleetManagerOfNewOrganization(), uniqueEmail(), "DRIVER")

			Given { bearer(TestApi.fleetManagerOfNewOrganization()) } When {
				delete("/api/organization/invitations/$invitationId")
			} Then {
				statusCode(404)
			}
		}

		it("lists only the active organization's members") {
			TestApi.memberOf(TestApi.fleetManagerOfNewOrganization(), "DRIVER")

			Given { bearer(TestApi.fleetManagerOfNewOrganization()) } When { get("/api/organization/members") } Then {
				body("", hasSize<Any>(1))
			}
		}
	}

	describe("changing roles and removing members") {
		it("applies a role change to the member's existing token at once") {
			val fleetManager = TestApi.fleetManagerOfNewOrganization()
			val driver = TestApi.memberOf(fleetManager, "DRIVER")
			Given { bearer(driver) } When { get("/api/organization/members") } Then { statusCode(403) }

			Given { bearer(fleetManager).json(mapOf("role" to "FLEET_MANAGER")) } When {
				patch("/api/organization/members/${TestApi.userId(driver)}")
			} Then {
				statusCode(200)
			}

			Given { bearer(driver) } When { get("/api/organization/members") } Then { statusCode(200) }
		}

		it("refuses to demote the last Fleet Manager") {
			val fleetManager = TestApi.fleetManagerOfNewOrganization()

			Given { bearer(fleetManager).json(mapOf("role" to "DRIVER")) } When {
				patch("/api/organization/members/${TestApi.userId(fleetManager)}")
			} Then {
				statusCode(409)
			}
		}

		it("removes a member's access but keeps their account") {
			val fleetManager = TestApi.fleetManagerOfNewOrganization()
			val driver = TestApi.memberOf(fleetManager, "DRIVER")

			Given { bearer(fleetManager) } When {
				delete("/api/organization/members/${TestApi.userId(driver)}")
			} Then {
				statusCode(204)
			}

			val me = TestApi.me(driver)
			me["activeOrganization"] shouldBe null
			(me["memberships"] as List<*>).shouldHaveSize(0)
		}
	}

})

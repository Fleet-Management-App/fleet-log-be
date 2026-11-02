package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.TestApi
import com.fleetlog.TestApi.withBearer
import com.fleetlog.TestApi.withJson
import com.fleetlog.TestApi.generateUniqueEmail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.Matchers.hasSize

class OrganizationControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/organizations") {
		it("makes the creator the first Fleet Manager and switches them to it") {
			val token = TestApi.createFleetManagerOfNewOrganization("Demo Fleet")

			@Suppress("UNCHECKED_CAST")
			val active = TestApi.getMe(token)["activeOrganization"] as Map<String, Any?>
			active["organizationName"] shouldBe "Demo Fleet"
			active["role"] shouldBe "FLEET_MANAGER"
		}
	}

	describe("role checks on /api/organization/**") {
		it("return 403 for a token without an organization") {
			Given { withBearer(TestApi.signUp()) } When { get("/api/organization/members") } Then { statusCode(403) }
		}

		it("return 403 for a Driver") {
			val driver = TestApi.createMemberOf(TestApi.createFleetManagerOfNewOrganization(), "DRIVER")

			Given { withBearer(driver) } When { get("/api/organization/members") } Then { statusCode(403) }
		}

		it("return 403 for an Accountant") {
			val accountant = TestApi.createMemberOf(TestApi.createFleetManagerOfNewOrganization(), "ACCOUNTANT")

			Given { withBearer(accountant) } When { get("/api/organization/members") } Then { statusCode(403) }
		}
	}

	describe("invitations") {
		it("lets an invited person sign up later, accept and join with the invited role") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val email = generateUniqueEmail("driver")
			val invitationId = TestApi.invite(fleetManager, email, "DRIVER")

			val newcomer = TestApi.signUp(email)
			(TestApi.getMe(newcomer)["pendingInvitations"] as List<*>).shouldHaveSize(1)

			val joined = TestApi.accept(newcomer, invitationId)
			@Suppress("UNCHECKED_CAST")
			(TestApi.getMe(joined)["activeOrganization"] as Map<String, Any?>)["role"] shouldBe "DRIVER"
			Given { withBearer(fleetManager) } When { get("/api/organization/members") } Then { body("", hasSize<Any>(2)) }
		}

		it("refuses to invite someone who is already a member") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val email = generateUniqueEmail()
			TestApi.createMemberOf(fleetManager, "DRIVER", email)

			Given { withBearer(fleetManager).withJson(mapOf("email" to email, "role" to "ACCOUNTANT")) } When {
				post("/api/organization/invitations")
			} Then {
				statusCode(409)
			}
		}

		it("hides an invitation from anyone but the invited e-mail") {
			val invitationId = TestApi.invite(TestApi.createFleetManagerOfNewOrganization(), generateUniqueEmail(), "DRIVER")

			Given { withBearer(TestApi.signUp()) } When { post("/api/invitations/$invitationId/accept") } Then { statusCode(404) }
		}

		it("can't be accepted once revoked") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val email = generateUniqueEmail()
			val invitationId = TestApi.invite(fleetManager, email, "DRIVER")
			Given { withBearer(fleetManager) } When { delete("/api/organization/invitations/$invitationId") } Then { statusCode(204) }

			Given { withBearer(TestApi.signUp(email)) } When { post("/api/invitations/$invitationId/accept") } Then { statusCode(409) }
		}
	}

	describe("tenant isolation") {
		it("doesn't let a Fleet Manager touch another organization's member") {
			val otherFleetManager = TestApi.createFleetManagerOfNewOrganization()
			val otherDriverId = TestApi.getUserId(TestApi.createMemberOf(otherFleetManager, "DRIVER"))

			Given { withBearer(TestApi.createFleetManagerOfNewOrganization()).withJson(mapOf("role" to "FLEET_MANAGER")) } When {
				patch("/api/organization/members/$otherDriverId")
			} Then {
				statusCode(404)
			}
		}

		it("doesn't let a Fleet Manager revoke another organization's invitation") {
			val invitationId = TestApi.invite(TestApi.createFleetManagerOfNewOrganization(), generateUniqueEmail(), "DRIVER")

			Given { withBearer(TestApi.createFleetManagerOfNewOrganization()) } When {
				delete("/api/organization/invitations/$invitationId")
			} Then {
				statusCode(404)
			}
		}

		it("lists only the active organization's members") {
			TestApi.createMemberOf(TestApi.createFleetManagerOfNewOrganization(), "DRIVER")

			Given { withBearer(TestApi.createFleetManagerOfNewOrganization()) } When { get("/api/organization/members") } Then {
				body("", hasSize<Any>(1))
			}
		}
	}

	describe("changing roles and removing members") {
		it("applies a role change to the member's existing token at once") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val driver = TestApi.createMemberOf(fleetManager, "DRIVER")
			Given { withBearer(driver) } When { get("/api/organization/members") } Then { statusCode(403) }

			Given { withBearer(fleetManager).withJson(mapOf("role" to "FLEET_MANAGER")) } When {
				patch("/api/organization/members/${TestApi.getUserId(driver)}")
			} Then {
				statusCode(200)
			}

			Given { withBearer(driver) } When { get("/api/organization/members") } Then { statusCode(200) }
		}

		it("refuses to demote the last Fleet Manager") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()

			Given { withBearer(fleetManager).withJson(mapOf("role" to "DRIVER")) } When {
				patch("/api/organization/members/${TestApi.getUserId(fleetManager)}")
			} Then {
				statusCode(409)
			}
		}

		it("removes a member's access but keeps their account") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val driver = TestApi.createMemberOf(fleetManager, "DRIVER")

			Given { withBearer(fleetManager) } When {
				delete("/api/organization/members/${TestApi.getUserId(driver)}")
			} Then {
				statusCode(204)
			}

			val me = TestApi.getMe(driver)
			me["activeOrganization"] shouldBe null
			(me["memberships"] as List<*>).shouldHaveSize(0)
		}
	}

})

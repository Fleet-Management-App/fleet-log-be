package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.TestApi
import com.fleetlog.TestApi.withBearer
import com.fleetlog.TestApi.withJson
import com.fleetlog.TestApi.generateUniqueEmail
import io.kotest.matchers.collections.shouldContainExactly
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
			active["roles"] shouldBe listOf("FLEET_MANAGER")
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

		it("let in a member who is a Driver and a Fleet Manager at once") {
			val driverAndFleetManager = TestApi.createMemberOf(TestApi.createFleetManagerOfNewOrganization(), "DRIVER", "FLEET_MANAGER")

			TestApi.getActiveRoles(driverAndFleetManager) shouldContainExactly listOf("DRIVER", "FLEET_MANAGER")
			Given { withBearer(driverAndFleetManager) } When { get("/api/organization/members") } Then { statusCode(200) }
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
			TestApi.getActiveRoles(joined) shouldBe listOf("DRIVER")
			Given { withBearer(fleetManager) } When { get("/api/organization/members") } Then { body("", hasSize<Any>(2)) }
		}

		it("refuses to invite someone who is already a member") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val email = generateUniqueEmail()
			TestApi.createMemberOf(fleetManager, "DRIVER", email = email)

			Given { withBearer(fleetManager).withJson(mapOf("email" to email, "roles" to listOf("ACCOUNTANT"))) } When {
				post("/api/organization/invitations")
			} Then {
				statusCode(409)
			}
		}

		it("refuses an invitation without a role") {
			Given { withBearer(TestApi.createFleetManagerOfNewOrganization()).withJson(mapOf("email" to generateUniqueEmail(), "roles" to emptyList<String>())) } When {
				post("/api/organization/invitations")
			} Then {
				statusCode(400)
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

			TestApi.changeRoles(TestApi.createFleetManagerOfNewOrganization(), otherDriverId, "FLEET_MANAGER") Then { statusCode(404) }
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

			TestApi.changeRoles(fleetManager, TestApi.getUserId(driver), "DRIVER", "FLEET_MANAGER") Then {
				statusCode(200)
				body("roles", hasSize<Any>(2))
			}

			Given { withBearer(driver) } When { get("/api/organization/members") } Then { statusCode(200) }
			TestApi.getActiveRoles(driver) shouldContainExactly listOf("DRIVER", "FLEET_MANAGER")
		}

		it("refuses to take Fleet Manager away from the last one, even if they keep another role") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()

			TestApi.changeRoles(fleetManager, TestApi.getUserId(fleetManager), "DRIVER") Then { statusCode(409) }
		}

		it("lets a Fleet Manager step down to Driver once someone else is a Fleet Manager") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			TestApi.createMemberOf(fleetManager, "FLEET_MANAGER")

			TestApi.changeRoles(fleetManager, TestApi.getUserId(fleetManager), "DRIVER") Then { statusCode(200) }
			TestApi.getActiveRoles(fleetManager) shouldBe listOf("DRIVER")
		}

		it("refuses an empty set of roles") {
			val fleetManager = TestApi.createFleetManagerOfNewOrganization()
			val driverId = TestApi.getUserId(TestApi.createMemberOf(fleetManager, "DRIVER"))

			TestApi.changeRoles(fleetManager, driverId) Then { statusCode(400) }
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

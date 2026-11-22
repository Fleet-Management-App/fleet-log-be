package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.dto.CreateInvitationRequest
import com.fleetlog.dto.MemberResponse
import com.fleetlog.entity.Role
import com.fleetlog.fixtures.Accounts
import com.fleetlog.fixtures.Accounts.uniqueEmail
import com.fleetlog.fixtures.asRole
import com.fleetlog.fixtures.withBearer
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.springframework.http.HttpStatus

class OrganizationControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/organizations") {
		it("makes the creator the first Fleet Manager and switches them to it") {
			val token = Accounts.createOrganization(name = "Demo Fleet")

			val active = Accounts.getMe(token).activeOrganization.shouldNotBeNull()
			active.organizationName shouldBe "Demo Fleet"
			active.roles shouldBe setOf(Role.FLEET_MANAGER)
		}
	}

	describe("role checks on /api/organization/**") {
		it("return 403 for a token without an organization") {
			Given {
				withBearer(Accounts.signUp())
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("return 403 for a Driver") {
			Given {
				asRole(Role.DRIVER)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("return 403 for an Accountant") {
			Given {
				asRole(Role.ACCOUNTANT)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("let in a member who is a Driver and a Fleet Manager at once") {
			val driverAndFleetManager = Accounts.memberOf(Accounts.createOrganization(), setOf(Role.DRIVER, Role.FLEET_MANAGER))

			Accounts.getActiveRoles(driverAndFleetManager) shouldContainExactlyInAnyOrder listOf(Role.DRIVER, Role.FLEET_MANAGER)
			Given {
				withBearer(driverAndFleetManager)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
		}
	}

	describe("invitations") {
		it("lets an invited person sign up later, accept and join with the invited role") {
			val fleetManager = Accounts.createOrganization()
			val email = uniqueEmail("driver")
			val invitationId = Accounts.invite(fleetManager, Role.DRIVER, email)

			val newcomer = Accounts.signUp(email)
			Accounts.getMe(newcomer).pendingInvitations shouldHaveSize 1

			val joined = Accounts.accept(newcomer, invitationId)
			Accounts.getActiveRoles(joined) shouldBe setOf(Role.DRIVER)

			val members = Given {
				withBearer(fleetManager)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<MemberResponse>::class.java).toList()
			}
			members shouldHaveSize 2
		}

		it("refuses to invite someone who is already a member") {
			val fleetManager = Accounts.createOrganization()
			val email = uniqueEmail()
			Accounts.memberOf(fleetManager, Role.DRIVER, email)

			Given {
				withBearer(fleetManager).body(CreateInvitationRequest(email, setOf(Role.ACCOUNTANT)))
			} When {
				post("/api/organization/invitations")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("refuses an invitation without a role") {
			Given {
				withBearer(Accounts.createOrganization()).body(CreateInvitationRequest(uniqueEmail(), emptySet()))
			} When {
				post("/api/organization/invitations")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
			}
		}

		it("hides an invitation from anyone but the invited e-mail") {
			val invitationId = Accounts.invite(Accounts.createOrganization(), Role.DRIVER)

			Given {
				withBearer(Accounts.signUp())
			} When {
				post("/api/invitations/$invitationId/accept")
			} Then {
				statusCode(HttpStatus.NOT_FOUND.value())
			}
		}

		it("can't be accepted once revoked") {
			val fleetManager = Accounts.createOrganization()
			val email = uniqueEmail()
			val invitationId = Accounts.invite(fleetManager, Role.DRIVER, email)
			Given {
				withBearer(fleetManager)
			} When {
				delete("/api/organization/invitations/$invitationId")
			} Then {
				statusCode(HttpStatus.NO_CONTENT.value())
			}

			Given {
				withBearer(Accounts.signUp(email))
			} When {
				post("/api/invitations/$invitationId/accept")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}
	}

	describe("tenant isolation") {
		it("doesn't let a Fleet Manager touch another organization's member") {
			val otherFleetManager = Accounts.createOrganization()
			val otherDriverId = Accounts.getUserId(Accounts.memberOf(otherFleetManager, Role.DRIVER))

			Accounts.changeRoles(Accounts.createOrganization(), otherDriverId, setOf(Role.FLEET_MANAGER))
				.statusCode(HttpStatus.NOT_FOUND.value())
		}

		it("doesn't let a Fleet Manager revoke another organization's invitation") {
			val invitationId = Accounts.invite(Accounts.createOrganization(), Role.DRIVER)

			Given {
				withBearer(Accounts.createOrganization())
			} When {
				delete("/api/organization/invitations/$invitationId")
			} Then {
				statusCode(HttpStatus.NOT_FOUND.value())
			}
		}

		it("lists only the active organization's members") {
			Accounts.memberOf(Accounts.createOrganization(), Role.DRIVER)

			val members = Given {
				withBearer(Accounts.createOrganization())
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<MemberResponse>::class.java).toList()
			}
			members shouldHaveSize 1
		}
	}

	describe("changing roles and removing members") {
		it("applies a role change to the member's existing token at once") {
			val fleetManager = Accounts.createOrganization()
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)
			Given {
				withBearer(driver)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}

			val changed = Accounts.changeRoles(fleetManager, Accounts.getUserId(driver), setOf(Role.DRIVER, Role.FLEET_MANAGER))
				.statusCode(HttpStatus.OK.value())
				.extract().`as`(MemberResponse::class.java)
			changed.roles shouldBe setOf(Role.DRIVER, Role.FLEET_MANAGER)

			Given {
				withBearer(driver)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
		}

		it("refuses to take Fleet Manager away from the last one, even if they keep another role") {
			val fleetManager = Accounts.createOrganization()

			Accounts.changeRoles(fleetManager, Accounts.getUserId(fleetManager), setOf(Role.DRIVER))
				.statusCode(HttpStatus.CONFLICT.value())
				.body("code", equalTo("LAST_FLEET_MANAGER"))
		}

		it("lets a Fleet Manager step down to Driver once someone else is a Fleet Manager") {
			val fleetManager = Accounts.createOrganization()
			Accounts.memberOf(fleetManager, Role.FLEET_MANAGER)

			Accounts.changeRoles(fleetManager, Accounts.getUserId(fleetManager), setOf(Role.DRIVER))
				.statusCode(HttpStatus.OK.value())
			Accounts.getActiveRoles(fleetManager) shouldBe setOf(Role.DRIVER)
		}

		it("refuses an empty set of roles") {
			val fleetManager = Accounts.createOrganization()
			val driverId = Accounts.getUserId(Accounts.memberOf(fleetManager, Role.DRIVER))

			Accounts.changeRoles(fleetManager, driverId, emptySet())
				.statusCode(HttpStatus.BAD_REQUEST.value())
		}

		it("removes a member's access but keeps their account") {
			val fleetManager = Accounts.createOrganization()
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)

			Given {
				withBearer(fleetManager)
			} When {
				delete("/api/organization/members/${Accounts.getUserId(driver)}")
			} Then {
				statusCode(HttpStatus.NO_CONTENT.value())
			}

			val me = Accounts.getMe(driver)
			me.activeOrganization.shouldBeNull()
			me.memberships shouldHaveSize 0
		}
	}

})

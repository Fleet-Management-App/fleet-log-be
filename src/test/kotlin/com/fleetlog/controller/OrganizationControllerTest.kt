package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.dto.ChangeRoleRequest
import com.fleetlog.dto.CreateInvitationRequest
import com.fleetlog.dto.MemberResponse
import com.fleetlog.entity.Role
import com.fleetlog.fixtures.Accounts
import com.fleetlog.fixtures.Accounts.uniqueEmail
import com.fleetlog.fixtures.asRole
import com.fleetlog.fixtures.withBearer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.springframework.http.HttpStatus

class OrganizationControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/organizations") {
		it("makes the creator the first Fleet Manager and switches them to it") {
			val token = Accounts.createOrganization(name = "Demo Fleet")

			val active = Accounts.getMe(token).activeOrganization.shouldNotBeNull()
			active.organizationName shouldBe "Demo Fleet"
			active.role shouldBe Role.FLEET_MANAGER
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
	}

	describe("invitations") {
		it("lets an invited person sign up later, accept and join with the invited role") {
			val fleetManager = Accounts.createOrganization()
			val email = uniqueEmail("driver")
			val invitationId = Accounts.invite(fleetManager, Role.DRIVER, email)

			val newcomer = Accounts.signUp(email)
			Accounts.getMe(newcomer).pendingInvitations shouldHaveSize 1

			val joined = Accounts.accept(newcomer, invitationId)
			Accounts.getMe(joined).activeOrganization.shouldNotBeNull().role shouldBe Role.DRIVER

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
				withBearer(fleetManager).body(CreateInvitationRequest(email, Role.ACCOUNTANT))
			} When {
				post("/api/organization/invitations")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
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

			Given {
				withBearer(Accounts.createOrganization()).body(ChangeRoleRequest(Role.FLEET_MANAGER))
			} When {
				patch("/api/organization/members/$otherDriverId")
			} Then {
				statusCode(HttpStatus.NOT_FOUND.value())
			}
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

			Given {
				withBearer(fleetManager).body(ChangeRoleRequest(Role.FLEET_MANAGER))
			} When {
				patch("/api/organization/members/${Accounts.getUserId(driver)}")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			Given {
				withBearer(driver)
			} When {
				get("/api/organization/members")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
		}

		it("refuses to demote the last Fleet Manager") {
			val fleetManager = Accounts.createOrganization()

			Given {
				withBearer(fleetManager).body(ChangeRoleRequest(Role.DRIVER))
			} When {
				patch("/api/organization/members/${Accounts.getUserId(fleetManager)}")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
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

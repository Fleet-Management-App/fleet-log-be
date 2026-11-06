package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.dto.TripFuelDecisionRequest
import com.fleetlog.dto.TripFuelRequest
import com.fleetlog.dto.TripOdometerRequest
import com.fleetlog.dto.TripPlanDecisionRequest
import com.fleetlog.dto.TripResponse
import com.fleetlog.entity.FuelStatus
import com.fleetlog.entity.Role
import com.fleetlog.entity.TripStatus
import com.fleetlog.fixtures.Accounts
import com.fleetlog.fixtures.Trips
import com.fleetlog.fixtures.Vehicles
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
import java.math.BigDecimal

class TripControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/trips") {
		it("starts a trip in PLANNED status") {
			val fleetManager = Accounts.createOrganization()
			Trips.createTrip(fleetManager, Vehicles.createVehicleId(fleetManager)).status shouldBe TripStatus.PLANNED
		}

		it("returns 400 when the vehicle's STK has already expired (BR-01)") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager, stkDueDate = "2000-01-01")

			Given {
				withBearer(fleetManager).body(Trips.tripBody(vehicleId))
			} When {
				post("/api/trips")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
			}
		}

		it("returns 404 for a vehicle from another tenant") {
			val otherTenantVehicleId = Vehicles.createVehicleId(Accounts.createOrganization())

			Given {
				asRole(Role.FLEET_MANAGER).body(Trips.tripBody(otherTenantVehicleId))
			} When {
				post("/api/trips")
			} Then {
				statusCode(HttpStatus.NOT_FOUND.value())
			}
		}

		it("returns 403 for an Accountant") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager)
			val accountant = Accounts.memberOf(fleetManager, Role.ACCOUNTANT)

			Given {
				withBearer(accountant).body(Trips.tripBody(vehicleId))
			} When {
				post("/api/trips")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}
	}

	describe("PATCH /api/trips/{id}/plan-approval") {
		it("returns 403 for a Driver") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)

			Given {
				withBearer(driver).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("approves the plan and returns 409 if decided again") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			val trip = Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.status shouldBe TripStatus.PLAN_APPROVED

			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("rejects the plan with a reason") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			val trip = Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = false, rejectionReason = "Vehicle double-booked"))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.status shouldBe TripStatus.PLAN_REJECTED
			trip.rejectionReason shouldBe "Vehicle double-booked"
		}
	}

	describe("PATCH /api/trips/{id}/plan") {
		fun rejectedTrip(fleetManager: String, vehicleId: String): String {
			val tripId = Trips.createTripId(fleetManager, vehicleId)
			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = false, rejectionReason = "Pick another vehicle"))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
			return tripId
		}

		it("edits and resubmits a plan-rejected trip, returning it to PLANNED (BR-07)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = rejectedTrip(fleetManager, Vehicles.createVehicleId(fleetManager))
			val newVehicleId = Vehicles.createVehicleId(fleetManager)

			val trip = Given {
				withBearer(fleetManager).body(Trips.tripBody(newVehicleId))
			} When {
				patch("/api/trips/$tripId/plan")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.status shouldBe TripStatus.PLANNED
			trip.rejectionReason.shouldBeNull()
			trip.vehicleId.toString() shouldBe newVehicleId

			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
		}

		it("returns 409 for a trip that isn't plan-rejected") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager)
			val tripId = Trips.createTripId(fleetManager, vehicleId)

			Given {
				withBearer(fleetManager).body(Trips.tripBody(vehicleId))
			} When {
				patch("/api/trips/$tripId/plan")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}
	}

	describe("PATCH /api/trips/{id}/odometer") {
		it("returns 409 before the plan is approved (BR-03)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			Given {
				withBearer(fleetManager).body(TripOdometerRequest(odometerStartKm = 100, odometerEndKm = 150))
			} When {
				patch("/api/trips/$tripId/odometer")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("returns 400 when end is before start (BR-04)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			Given {
				withBearer(fleetManager).body(TripOdometerRequest(odometerStartKm = 150, odometerEndKm = 100))
			} When {
				patch("/api/trips/$tripId/odometer")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
			}
		}

		it("completes the trip once logged (BR-05), with no fuel entry required") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			val trip = Given {
				withBearer(fleetManager).body(TripOdometerRequest(odometerStartKm = 100, odometerEndKm = 150))
			} When {
				patch("/api/trips/$tripId/odometer")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.status shouldBe TripStatus.COMPLETED
			trip.fuel.shouldBeNull()
		}
	}

	describe("fuel logging and approval") {
		fun approvedAndCompletedTrip(fleetManager: String): String {
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
			Given {
				withBearer(fleetManager).body(TripOdometerRequest(odometerStartKm = 0, odometerEndKm = 50))
			} When {
				patch("/api/trips/$tripId/odometer")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
			return tripId
		}

		it("logs fuel as PENDING without affecting the already-COMPLETED trip status (BR-06)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)

			val trip = Given {
				withBearer(fleetManager).body(TripFuelRequest(liters = BigDecimal("40.5"), price = BigDecimal("55.2"), receiptPhoto = "base64data"))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.status shouldBe TripStatus.COMPLETED
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING
		}

		it("returns 409 for a second fuel entry while the first is pending") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)
			Given {
				withBearer(fleetManager).body(TripFuelRequest(liters = BigDecimal("40.5"), price = BigDecimal("55.2")))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			Given {
				withBearer(fleetManager).body(TripFuelRequest(liters = BigDecimal("10.0"), price = BigDecimal("20.0")))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("returns 409 for fuel-approval with no fuel entry") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)

			Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("returns 403 for a Driver deciding fuel approval") {
			val fleetManager = Accounts.createOrganization()
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)
			val tripId = approvedAndCompletedTrip(fleetManager)
			Given {
				withBearer(fleetManager).body(TripFuelRequest(liters = BigDecimal("40.5"), price = BigDecimal("55.2")))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			Given {
				withBearer(driver).body(TripFuelDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("lets the rejected fuel entry be resubmitted through the same endpoint (BR-07)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)
			Given {
				withBearer(fleetManager).body(TripFuelRequest(liters = BigDecimal("40.5"), price = BigDecimal("55.2")))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
			Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = false, rejectionReason = "Price too high"))
			} When {
				patch("/api/trips/$tripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			val trip = Given {
				withBearer(fleetManager).body(TripFuelRequest(liters = BigDecimal("38.0"), price = BigDecimal("50.0")))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING
			trip.status shouldBe TripStatus.COMPLETED
		}
	}

	describe("PATCH /api/trips/{id}/cancel") {
		it("cancels a still-planned trip") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			Given {
				withBearer(fleetManager)
			} When {
				patch("/api/trips/$tripId/cancel")
			} Then {
				statusCode(HttpStatus.NO_CONTENT.value())
			}
		}

		it("returns 409 once the plan has been approved") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			Given {
				withBearer(fleetManager)
			} When {
				patch("/api/trips/$tripId/cancel")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}
	}

	describe("visibility") {
		it("hides a Driver's trip from another Driver (403), but a Fleet Manager sees it") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager)
			val owner = Accounts.memberOf(fleetManager, Role.DRIVER)
			val otherDriver = Accounts.memberOf(fleetManager, Role.DRIVER)
			val tripId = Trips.createTripId(owner, vehicleId)

			Given {
				withBearer(otherDriver)
			} When {
				get("/api/trips/$tripId")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}

			Given {
				withBearer(fleetManager)
			} When {
				get("/api/trips/$tripId")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
		}

		it("lists only the Driver's own trips, but all trips for a Fleet Manager") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager)
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)
			Trips.createTrip(driver, vehicleId)
			Trips.createTrip(fleetManager, vehicleId)

			val driverTrips = Given {
				withBearer(driver)
			} When {
				get("/api/trips")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<TripResponse>::class.java).toList()
			}
			driverTrips shouldHaveSize 1

			val allTrips = Given {
				withBearer(fleetManager)
			} When {
				get("/api/trips")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<TripResponse>::class.java).toList()
			}
			allTrips shouldHaveSize 2
		}

		it("returns 403 for an Accountant listing or reading trips") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			val accountant = Accounts.memberOf(fleetManager, Role.ACCOUNTANT)

			Given {
				withBearer(accountant)
			} When {
				get("/api/trips")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}

			Given {
				withBearer(accountant)
			} When {
				get("/api/trips/$tripId")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}
	}

})

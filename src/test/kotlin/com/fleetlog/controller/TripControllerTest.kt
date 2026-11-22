package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.dto.TripFuelDecisionRequest
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
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.springframework.http.HttpStatus
import java.util.UUID

class TripControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/trips") {
		it("starts a trip in PLANNED status") {
			val fleetManager = Accounts.createOrganization()
			Trips.createTrip(fleetManager, Vehicles.createVehicleId(fleetManager)).status shouldBe TripStatus.PLANNED
		}

		it("names the driver, so the Fleet Manager's approval queue can show who planned it") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager)
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)

			val trip = Trips.createTrip(driver, vehicleId)
			trip.driverId.toString() shouldBe Accounts.getUserId(driver)
			trip.driverName shouldBe Accounts.getMe(driver).fullName
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
				body("code", equalTo("VEHICLE_UNAVAILABLE"))
				body("field", equalTo("vehicleId"))
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
				body("code", equalTo("VEHICLE_NOT_FOUND"))
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
				body("code", equalTo("TRIP_NOT_PLANNED"))
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

		it("returns 400 for rejecting without a reason, leaving the plan PLANNED") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			for (reason in listOf(null, "")) {
				Given {
					withBearer(fleetManager).body(TripPlanDecisionRequest(approved = false, rejectionReason = reason))
				} When {
					patch("/api/trips/$tripId/plan-approval")
				} Then {
					statusCode(HttpStatus.BAD_REQUEST.value())
					body("code", equalTo("REJECTION_REASON_REQUIRED"))
					body("field", equalTo("rejectionReason"))
				}
			}

			Trips.approvePlan(fleetManager, tripId).status shouldBe TripStatus.PLAN_APPROVED
		}

		it("clears any reason sent along with an approval") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			val trip = Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true, rejectionReason = "ignored"))
			} When {
				patch("/api/trips/$tripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.rejectionReason.shouldBeNull()
		}

		it("returns 400 for approving a plan whose vehicle was suspended, or its STK ran out, after planning (BR-01)") {
			val fleetManager = Accounts.createOrganization()
			val suspendedVehicleId = Vehicles.createVehicleId(fleetManager)
			val stkExpiredVehicleId = Vehicles.createVehicleId(fleetManager)
			val suspendedTripId = Trips.createTripId(fleetManager, suspendedVehicleId)
			val stkExpiredTripId = Trips.createTripId(fleetManager, stkExpiredVehicleId)
			// No API changes a vehicle's status or STK yet
			jdbc.update("UPDATE vehicle SET status = 'SUSPENDED' WHERE id = ?", UUID.fromString(suspendedVehicleId))
			jdbc.update("UPDATE vehicle SET stk_due_date = DATE '2000-01-01' WHERE id = ?", UUID.fromString(stkExpiredVehicleId))

			for (tripId in listOf(suspendedTripId, stkExpiredTripId)) {
				Given {
					withBearer(fleetManager).body(TripPlanDecisionRequest(approved = true))
				} When {
					patch("/api/trips/$tripId/plan-approval")
				} Then {
					statusCode(HttpStatus.BAD_REQUEST.value())
					body("code", equalTo("VEHICLE_UNAVAILABLE"))
				}
			}

			// Rejecting it is still possible, so the driver can pick another vehicle (BR-07)
			Given {
				withBearer(fleetManager).body(TripPlanDecisionRequest(approved = false, rejectionReason = "Vehicle is suspended"))
			} When {
				patch("/api/trips/$suspendedTripId/plan-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
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
				body("code", equalTo("TRIP_NOT_PLAN_REJECTED"))
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
				body("code", equalTo("TRIP_NOT_PLAN_APPROVED"))
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
				body("code", equalTo("ODOMETER_END_BEFORE_START"))
				body("field", equalTo("odometerEndKm"))
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

		it("moves the vehicle's odometer forward to the trip's end reading, never back") {
			val fleetManager = Accounts.createOrganization()
			val vehicle = Vehicles.createVehicle(fleetManager)
			val vehicleId = vehicle.id.toString()
			val laterTripId = Trips.createTripId(fleetManager, vehicleId)
			val earlierTripId = Trips.createTripId(fleetManager, vehicleId)
			Trips.approvePlan(fleetManager, laterTripId)
			Trips.approvePlan(fleetManager, earlierTripId)

			Trips.logOdometer(fleetManager, laterTripId, startKm = vehicle.odometerKm, endKm = vehicle.odometerKm + 150)
			Vehicles.getVehicle(fleetManager, vehicleId).odometerKm shouldBe vehicle.odometerKm + 150

			// Logged late, out of order: its lower reading must not wind the vehicle back
			Trips.logOdometer(fleetManager, earlierTripId, startKm = vehicle.odometerKm - 100, endKm = vehicle.odometerKm)
			Vehicles.getVehicle(fleetManager, vehicleId).odometerKm shouldBe vehicle.odometerKm + 150
		}
	}

	describe("fuel logging and approval") {
		fun approvedAndCompletedTrip(fleetManager: String): String {
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Trips.approvePlan(fleetManager, tripId)
			Trips.logOdometer(fleetManager, tripId, startKm = 0, endKm = 50)
			return tripId
		}

		it("logs fuel as PENDING without affecting the already-COMPLETED trip status (BR-06)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)

			val trip = Trips.logFuel(fleetManager, tripId)
			trip.status shouldBe TripStatus.COMPLETED
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING
			trip.fuel.shouldNotBeNull().hasReceipt shouldBe true
		}

		it("logs fuel on a plan-approved trip whose odometer isn't logged yet") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Trips.approvePlan(fleetManager, tripId)

			val trip = Trips.logFuel(fleetManager, tripId)
			trip.status shouldBe TripStatus.PLAN_APPROVED
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING
		}

		it("returns 409 for fuel on a trip whose plan isn't approved (BR-03)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			Given {
				withBearer(fleetManager).body(Trips.fuelBody())
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
				body("code", equalTo("TRIP_NOT_PLAN_APPROVED"))
			}
		}

		it("returns 409 for fuel on a cancelled trip") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Given {
				withBearer(fleetManager)
			} When {
				patch("/api/trips/$tripId/cancel")
			} Then {
				statusCode(HttpStatus.NO_CONTENT.value())
			}

			Given {
				withBearer(fleetManager).body(Trips.fuelBody())
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
				body("code", equalTo("TRIP_NOT_PLAN_APPROVED"))
			}
		}

		it("returns 400 VALIDATION_FAILED for a blank receipt (BR-06)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)

			Given {
				withBearer(fleetManager).body(Trips.fuelBody(receiptPhoto = " "))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
				body("code", equalTo("VALIDATION_FAILED"))
				body("errors.field", contains("receiptPhoto"))
				body("errors.code", contains("NotBlank"))
			}
		}

		it("returns 400 naming receiptPhoto when there is no receipt at all (BR-06)") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)

			Given {
				withBearer(fleetManager).body("""{"liters": 40.5, "price": 55.2}""")
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
				body("code", equalTo("MALFORMED_REQUEST"))
				body("field", equalTo("receiptPhoto"))
			}
		}

		it("returns 409 for a second fuel entry while the first is pending") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)
			Trips.logFuel(fleetManager, tripId)

			Given {
				withBearer(fleetManager).body(Trips.fuelBody(liters = "10.0", price = "20.0"))
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
				body("code", equalTo("FUEL_ENTRY_ALREADY_EXISTS"))
			}
		}

		it("returns 409 for overwriting an approved fuel entry") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)
			Trips.logFuel(fleetManager, tripId)
			Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$tripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			Given {
				withBearer(fleetManager).body(Trips.fuelBody())
			} When {
				post("/api/trips/$tripId/fuel")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
				body("code", equalTo("FUEL_ENTRY_ALREADY_EXISTS"))
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
				body("code", equalTo("NO_FUEL_ENTRY"))
			}
		}

		it("returns 409 for deciding a fuel entry that is already approved or rejected") {
			val fleetManager = Accounts.createOrganization()
			val approvedTripId = approvedAndCompletedTrip(fleetManager)
			val rejectedTripId = approvedAndCompletedTrip(fleetManager)
			Trips.logFuel(fleetManager, approvedTripId)
			Trips.logFuel(fleetManager, rejectedTripId)
			Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = true))
			} When {
				patch("/api/trips/$approvedTripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}
			Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = false, rejectionReason = "Receipt unreadable"))
			} When {
				patch("/api/trips/$rejectedTripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			}

			for (tripId in listOf(approvedTripId, rejectedTripId)) {
				Given {
					withBearer(fleetManager).body(TripFuelDecisionRequest(approved = false, rejectionReason = "Changed my mind"))
				} When {
					patch("/api/trips/$tripId/fuel-approval")
				} Then {
					statusCode(HttpStatus.CONFLICT.value())
					body("code", equalTo("FUEL_ENTRY_NOT_PENDING"))
				}
			}
		}

		it("returns 400 for rejecting a fuel entry without a reason, leaving it PENDING") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)
			Trips.logFuel(fleetManager, tripId)

			for (reason in listOf(null, "   ")) {
				Given {
					withBearer(fleetManager).body(TripFuelDecisionRequest(approved = false, rejectionReason = reason))
				} When {
					patch("/api/trips/$tripId/fuel-approval")
				} Then {
					statusCode(HttpStatus.BAD_REQUEST.value())
					body("code", equalTo("REJECTION_REASON_REQUIRED"))
					body("field", equalTo("rejectionReason"))
				}
			}

			val trip = Given {
				withBearer(fleetManager)
			} When {
				get("/api/trips/$tripId")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING
		}

		it("clears any reason sent along with an approval") {
			val fleetManager = Accounts.createOrganization()
			val tripId = approvedAndCompletedTrip(fleetManager)
			Trips.logFuel(fleetManager, tripId)

			val trip = Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = true, rejectionReason = "ignored"))
			} When {
				patch("/api/trips/$tripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.APPROVED
			trip.fuel.shouldNotBeNull().rejectionReason.shouldBeNull()
		}

		it("returns 403 for a Driver deciding fuel approval") {
			val fleetManager = Accounts.createOrganization()
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)
			val tripId = approvedAndCompletedTrip(fleetManager)
			Trips.logFuel(fleetManager, tripId)

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
			Trips.logFuel(fleetManager, tripId)
			val rejected = Given {
				withBearer(fleetManager).body(TripFuelDecisionRequest(approved = false, rejectionReason = "Price too high"))
			} When {
				patch("/api/trips/$tripId/fuel-approval")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(TripResponse::class.java)
			}
			rejected.fuel.shouldNotBeNull().rejectionReason shouldBe "Price too high"

			val trip = Trips.logFuel(fleetManager, tripId, Trips.fuelBody(liters = "38.0", price = "50.0"))
			trip.fuel.shouldNotBeNull().status shouldBe FuelStatus.PENDING
			trip.fuel.shouldNotBeNull().rejectionReason.shouldBeNull()
			trip.status shouldBe TripStatus.COMPLETED
		}
	}

	describe("GET /api/trips/{id}/fuel/receipt") {
		it("returns the receipt to the trip's driver and to a Fleet Manager, while trip responses only flag it") {
			val fleetManager = Accounts.createOrganization()
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)
			val tripId = Trips.createTripId(driver, Vehicles.createVehicleId(fleetManager))
			Trips.approvePlan(fleetManager, tripId)
			Trips.logFuel(driver, tripId)

			for (token in listOf(driver, fleetManager)) {
				Given {
					withBearer(token)
				} When {
					get("/api/trips/$tripId/fuel/receipt")
				} Then {
					statusCode(HttpStatus.OK.value())
					body("receiptPhoto", equalTo(Trips.RECEIPT_PHOTO))
				}
			}

			Given {
				withBearer(fleetManager)
			} When {
				get("/api/trips")
			} Then {
				statusCode(HttpStatus.OK.value())
				body("[0].fuel.hasReceipt", equalTo(true))
				body(not(containsString(Trips.RECEIPT_PHOTO)))
			}
		}

		it("returns 403 for another Driver and for an Accountant") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))
			Trips.approvePlan(fleetManager, tripId)
			Trips.logFuel(fleetManager, tripId)

			for (role in listOf(Role.DRIVER, Role.ACCOUNTANT)) {
				Given {
					withBearer(Accounts.memberOf(fleetManager, role))
				} When {
					get("/api/trips/$tripId/fuel/receipt")
				} Then {
					statusCode(HttpStatus.FORBIDDEN.value())
				}
			}
		}

		it("returns 404 FUEL_RECEIPT_NOT_FOUND for a trip without a fuel entry") {
			val fleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(fleetManager, Vehicles.createVehicleId(fleetManager))

			Given {
				withBearer(fleetManager)
			} When {
				get("/api/trips/$tripId/fuel/receipt")
			} Then {
				statusCode(HttpStatus.NOT_FOUND.value())
				body("code", equalTo("FUEL_RECEIPT_NOT_FOUND"))
			}
		}

		it("returns 404 TRIP_NOT_FOUND for another tenant's trip") {
			val otherFleetManager = Accounts.createOrganization()
			val tripId = Trips.createTripId(otherFleetManager, Vehicles.createVehicleId(otherFleetManager))

			Given {
				asRole(Role.FLEET_MANAGER)
			} When {
				get("/api/trips/$tripId/fuel/receipt")
			} Then {
				statusCode(HttpStatus.NOT_FOUND.value())
				body("code", equalTo("TRIP_NOT_FOUND"))
			}
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
				body("code", equalTo("TRIP_NOT_PLANNED"))
			}
		}
	}

	describe("GET /api/trips/{id}") {
		it("returns 400 MALFORMED_REQUEST naming the path variable when the id isn't a UUID") {
			Given {
				asRole(Role.FLEET_MANAGER)
			} When {
				get("/api/trips/not-a-uuid")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
				body("code", equalTo("MALFORMED_REQUEST"))
				body("field", equalTo("tripId"))
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

		it("lists every trip for a member who is a Driver and a Fleet Manager at once") {
			val fleetManager = Accounts.createOrganization()
			val vehicleId = Vehicles.createVehicleId(fleetManager)
			val driverAndFleetManager = Accounts.memberOf(fleetManager, setOf(Role.DRIVER, Role.FLEET_MANAGER))
			Trips.createTrip(fleetManager, vehicleId)
			Trips.createTrip(driverAndFleetManager, vehicleId)

			val trips = Given {
				withBearer(driverAndFleetManager)
			} When {
				get("/api/trips")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<TripResponse>::class.java).toList()
			}
			trips shouldHaveSize 2
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

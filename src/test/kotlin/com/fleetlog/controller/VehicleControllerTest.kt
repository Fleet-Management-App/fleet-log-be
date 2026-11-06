package com.fleetlog.controller

import com.fleetlog.BaseFleetLogBeIntegrationTest
import com.fleetlog.dto.VehicleResponse
import com.fleetlog.entity.Role
import com.fleetlog.entity.VehicleStatus
import com.fleetlog.fixtures.Accounts
import com.fleetlog.fixtures.Vehicles
import com.fleetlog.fixtures.asRole
import com.fleetlog.fixtures.withBearer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.springframework.http.HttpStatus

/** Deliberately missing every field but `plate`, to exercise the 400 path — `VehicleCreateRequest` has none optional. */
private data class IncompleteVehicleCreateRequest(val plate: String)

class VehicleControllerTest : BaseFleetLogBeIntegrationTest({

	describe("POST /api/vehicles") {
		it("lets a Fleet Manager register a vehicle, defaulting status to ACTIVE, and lists it back") {
			val fleetManager = Accounts.createOrganization()
			val plate = Vehicles.uniquePlate()

			val vehicle = Vehicles.createVehicle(fleetManager, plate = plate)
			vehicle.status shouldBe VehicleStatus.ACTIVE
			vehicle.plate shouldBe plate

			val vehicles = Given {
				withBearer(fleetManager)
			} When {
				get("/api/vehicles")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<VehicleResponse>::class.java).toList()
			}
			vehicles shouldHaveSize 1
		}

		it("returns 403 for a Driver") {
			Given {
				asRole(Role.DRIVER).body(Vehicles.vehicleRequest())
			} When {
				post("/api/vehicles")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("returns 403 for an Accountant") {
			Given {
				asRole(Role.ACCOUNTANT).body(Vehicles.vehicleRequest())
			} When {
				post("/api/vehicles")
			} Then {
				statusCode(HttpStatus.FORBIDDEN.value())
			}
		}

		it("returns 400 for a missing field") {
			Given {
				asRole(Role.FLEET_MANAGER).body(IncompleteVehicleCreateRequest(Vehicles.uniquePlate()))
			} When {
				post("/api/vehicles")
			} Then {
				statusCode(HttpStatus.BAD_REQUEST.value())
			}
		}

		it("returns 409 for a plate already used in the same tenant") {
			val fleetManager = Accounts.createOrganization()
			val plate = Vehicles.uniquePlate()
			Vehicles.createVehicle(fleetManager, plate = plate)

			Given {
				withBearer(fleetManager).body(Vehicles.vehicleRequest(plate = plate))
			} When {
				post("/api/vehicles")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("returns 409 for a VIN already used in the same tenant") {
			val fleetManager = Accounts.createOrganization()
			val vin = Vehicles.uniqueVin()
			Vehicles.createVehicle(fleetManager, vin = vin)

			Given {
				withBearer(fleetManager).body(Vehicles.vehicleRequest(vin = vin))
			} When {
				post("/api/vehicles")
			} Then {
				statusCode(HttpStatus.CONFLICT.value())
			}
		}

		it("allows the same plate in a different tenant") {
			val plate = Vehicles.uniquePlate()
			Vehicles.createVehicle(Accounts.createOrganization(), plate = plate)

			Given {
				asRole(Role.FLEET_MANAGER).body(Vehicles.vehicleRequest(plate = plate))
			} When {
				post("/api/vehicles")
			} Then {
				statusCode(HttpStatus.CREATED.value())
			}
		}
	}

	describe("GET /api/vehicles") {
		it("returns an empty list for a tenant with no vehicles") {
			val vehicles = Given {
				asRole(Role.FLEET_MANAGER)
			} When {
				get("/api/vehicles")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<VehicleResponse>::class.java).toList()
			}
			vehicles shouldHaveSize 0
		}

		it("is visible to a Driver, not just a Fleet Manager") {
			val fleetManager = Accounts.createOrganization()
			Vehicles.createVehicle(fleetManager)
			val driver = Accounts.memberOf(fleetManager, Role.DRIVER)

			val vehicles = Given {
				withBearer(driver)
			} When {
				get("/api/vehicles")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<VehicleResponse>::class.java).toList()
			}
			vehicles shouldHaveSize 1
		}

		it("only lists the caller's own tenant's vehicles") {
			Vehicles.createVehicle(Accounts.createOrganization())
			val secondOrgFleetManager = Accounts.createOrganization()
			Vehicles.createVehicle(secondOrgFleetManager)
			Vehicles.createVehicle(secondOrgFleetManager)

			val vehicles = Given {
				withBearer(secondOrgFleetManager)
			} When {
				get("/api/vehicles")
			} Then {
				statusCode(HttpStatus.OK.value())
			} Extract {
				`as`(Array<VehicleResponse>::class.java).toList()
			}
			vehicles shouldHaveSize 2
		}
	}

})

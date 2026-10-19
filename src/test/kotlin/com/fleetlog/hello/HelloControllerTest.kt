package com.fleetlog.hello

import com.fleetlog.BaseFleetLogBeIntegrationTest
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo

class HelloControllerTest : BaseFleetLogBeIntegrationTest({

	describe("GET /api/hello") {
		it("returns the hello message") {
			Given {
				this
			} When {
				get("/api/hello")
			} Then {
				statusCode(200)
				body("message", equalTo("Hello from FleetLog"))
			}
		}
	}

})

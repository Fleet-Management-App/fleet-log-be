package com.fleetlog.config

import com.fleetlog.BaseFleetLogBeIntegrationTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.path.json.JsonPath
import org.springframework.http.HttpStatus

/** The FE's API client is generated from this document, so its error shapes are part of the contract. */
class OpenApiDocsTest : BaseFleetLogBeIntegrationTest({

	fun apiDocs(): JsonPath = Given { this } When {
		get("/v3/api-docs")
	} Then {
		statusCode(HttpStatus.OK.value())
	} Extract { jsonPath() }

	describe("GET /v3/api-docs") {
		it("describes the error body with its code, field and errors properties") {
			val docs = apiDocs()

			docs.getMap<String, Any>("components.schemas.ApiProblem.properties").keys shouldContainAll listOf("code", "field", "errors")
			docs.getList<String>("components.schemas.ApiProblem.properties.code.enum") shouldContain "TRIP_NOT_PLAN_APPROVED"
			docs.getList<String>("components.schemas.ApiFieldError.required") shouldContainAll listOf("field", "code")
		}

		it("documents a domain endpoint's 409 with that error body") {
			val schema = apiDocs().getMap<String, String>(
				"paths.'/api/trips/{tripId}/fuel'.post.responses.'409'.content.'application/problem+json'.schema",
			)

			schema["\$ref"] shouldBe "#/components/schemas/ApiProblem"
		}
	}

})

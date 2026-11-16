package com.fleetlog

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseFleetLogBeIntegrationTest(body: BaseFleetLogBeIntegrationTest.() -> Unit = {}) : DescribeSpec() {

	@LocalServerPort
	private var port: Int = 0

	init {
		extension(SpringExtension())
		// Every spec's requests default to JSON, so a test can just call `body(someObject)`
		// without a per-call contentType/wrapper: RestAssured doesn't infer it on its own.
		RestAssured.requestSpecification = RequestSpecBuilder().setContentType(ContentType.JSON).build()
		beforeTest { RestAssured.port = port }
		body()
	}

	companion object {

		// One container for the whole test run, shared by every integration spec
		private val postgres = PostgreSQLContainer("postgres:18-alpine").apply { start() }

		@JvmStatic
		@DynamicPropertySource
		fun datasourceProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url", postgres::getJdbcUrl)
			registry.add("spring.datasource.username", postgres::getUsername)
			registry.add("spring.datasource.password", postgres::getPassword)
		}

	}

}

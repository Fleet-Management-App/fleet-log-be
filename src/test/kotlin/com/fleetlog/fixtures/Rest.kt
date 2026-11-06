package com.fleetlog.fixtures

import io.restassured.specification.RequestSpecification

fun RequestSpecification.withBearer(token: String): RequestSpecification = header("Authorization", "Bearer $token")

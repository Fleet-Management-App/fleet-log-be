package com.fleetlog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FleetLogBeApplication

fun main(args: Array<String>) {
	runApplication<FleetLogBeApplication>(*args)
}

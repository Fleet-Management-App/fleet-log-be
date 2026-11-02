package com.fleetlog.controller

import com.fleetlog.controller.api.OrganizationApi
import com.fleetlog.dto.CreateOrganizationRequest
import com.fleetlog.service.OrganizationService
import org.springframework.web.bind.annotation.RestController

@RestController
class OrganizationController(
	private val organizationService: OrganizationService,
) : OrganizationApi {

	override fun createOrganization(request: CreateOrganizationRequest) = organizationService.create(request)

}

package com.fleetlog.repository

import com.fleetlog.entity.Organization
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationRepository : JpaRepository<Organization, UUID>

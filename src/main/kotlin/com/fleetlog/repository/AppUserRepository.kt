package com.fleetlog.repository

import com.fleetlog.entity.AppUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUser, UUID> {

	fun findByEmail(email: String): AppUser?

	fun existsByEmail(email: String): Boolean

}

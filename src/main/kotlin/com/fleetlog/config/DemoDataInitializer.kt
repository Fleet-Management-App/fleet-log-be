package com.fleetlog.config

import com.fleetlog.entity.AppUser
import com.fleetlog.entity.Invitation
import com.fleetlog.entity.Membership
import com.fleetlog.entity.Organization
import com.fleetlog.entity.Role
import com.fleetlog.repository.AppUserRepository
import com.fleetlog.repository.InvitationRepository
import com.fleetlog.repository.MembershipRepository
import com.fleetlog.repository.OrganizationRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Demo accounts for presenting and testing the app, in every environment (DEMO_DATA_ENABLED, default true;
 * documented on Confluence "Infrastructure, CI/CD & Deployment"). All passwords are [DEMO_PASSWORD].
 *
 * - "Demo Fleet s.r.o.": jana (Fleet Manager), petr (Driver), eva (Accountant)
 * - "Horská doprava a.s.": tomas (Fleet Manager who also drives) and jana as a Driver, to show that
 *   roles belong to the membership, not to the account; petr has a pending invitation there
 *
 * Runs once: skipped when the demo Fleet Manager already exists.
 */
@Component
@ConditionalOnProperty(name = ["fleetlog.demo-data.enabled"], havingValue = "true")
class DemoDataInitializer(
	private val userRepository: AppUserRepository,
	private val organizationRepository: OrganizationRepository,
	private val membershipRepository: MembershipRepository,
	private val invitationRepository: InvitationRepository,
	private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

	@Transactional
	override fun run(args: ApplicationArguments) {
		if (userRepository.existsByEmail("jana@demo.fleetlog.app")) return

		val passwordHash = requireNotNull(passwordEncoder.encode(DEMO_PASSWORD))
		fun createUser(login: String, name: String) =
			userRepository.save(AppUser(email = "$login@demo.fleetlog.app", passwordHash = passwordHash, fullName = name))

		val jana = createUser("jana", "Jana Nováková")
		val petr = createUser("petr", "Petr Svoboda")
		val eva = createUser("eva", "Eva Dvořáková")
		val tomas = createUser("tomas", "Tomáš Král")

		val demoFleet = organizationRepository.save(Organization(name = "Demo Fleet s.r.o."))
		val horska = organizationRepository.save(Organization(name = "Horská doprava a.s."))

		membershipRepository.saveAll(
			listOf(
				Membership(user = jana, organization = demoFleet, roles = mutableSetOf(Role.FLEET_MANAGER)),
				Membership(user = petr, organization = demoFleet, roles = mutableSetOf(Role.DRIVER)),
				Membership(user = eva, organization = demoFleet, roles = mutableSetOf(Role.ACCOUNTANT)),
				Membership(user = tomas, organization = horska, roles = mutableSetOf(Role.FLEET_MANAGER, Role.DRIVER)),
				Membership(user = jana, organization = horska, roles = mutableSetOf(Role.DRIVER)),
			),
		)
		invitationRepository.save(Invitation(organization = horska, email = petr.email, roles = setOf(Role.DRIVER), invitedBy = tomas))

		log.info("Demo data created; log in as jana|petr|eva|tomas@demo.fleetlog.app with password '{}'", DEMO_PASSWORD)
	}

	companion object {
		const val DEMO_PASSWORD = "demo1234"
		private val log = LoggerFactory.getLogger(DemoDataInitializer::class.java)
	}

}

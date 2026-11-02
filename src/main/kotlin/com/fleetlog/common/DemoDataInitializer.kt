package com.fleetlog.common

import com.fleetlog.account.AppUser
import com.fleetlog.account.AppUserRepository
import com.fleetlog.organization.Invitation
import com.fleetlog.organization.InvitationRepository
import com.fleetlog.organization.Membership
import com.fleetlog.organization.MembershipRepository
import com.fleetlog.organization.Organization
import com.fleetlog.organization.OrganizationRepository
import com.fleetlog.organization.Role
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Demo accounts for presenting the app (DEMO_DATA_ENABLED=true). All passwords are [DEMO_PASSWORD].
 *
 * - "Demo Fleet s.r.o.": jana (Fleet Manager), petr (Driver), eva (Accountant)
 * - "Horská doprava a.s.": tomas (Fleet Manager) and jana as a Driver, to show that a role belongs
 *   to the membership, not to the account; petr has a pending invitation there
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
		fun user(login: String, name: String) =
			userRepository.save(AppUser(email = "$login@demo.fleetlog.app", passwordHash = passwordHash, fullName = name))

		val jana = user("jana", "Jana Nováková")
		val petr = user("petr", "Petr Svoboda")
		val eva = user("eva", "Eva Dvořáková")
		val tomas = user("tomas", "Tomáš Král")

		val demoFleet = organizationRepository.save(Organization(name = "Demo Fleet s.r.o."))
		val horska = organizationRepository.save(Organization(name = "Horská doprava a.s."))

		membershipRepository.saveAll(
			listOf(
				Membership(user = jana, organization = demoFleet, role = Role.FLEET_MANAGER),
				Membership(user = petr, organization = demoFleet, role = Role.DRIVER),
				Membership(user = eva, organization = demoFleet, role = Role.ACCOUNTANT),
				Membership(user = tomas, organization = horska, role = Role.FLEET_MANAGER),
				Membership(user = jana, organization = horska, role = Role.DRIVER),
			),
		)
		invitationRepository.save(Invitation(organization = horska, email = petr.email, role = Role.DRIVER, invitedBy = tomas))

		log.info("Demo data created; log in as jana|petr|eva|tomas@demo.fleetlog.app with password '{}'", DEMO_PASSWORD)
	}

	companion object {
		const val DEMO_PASSWORD = "demo1234"
		private val log = LoggerFactory.getLogger(DemoDataInitializer::class.java)
	}

}

package com.fleetlog.account

import com.fleetlog.common.ConflictException
import com.fleetlog.organization.MembershipRepository
import com.fleetlog.security.AuthTokenResponse
import com.fleetlog.security.TokenService
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class AuthService(
	private val userRepository: AppUserRepository,
	private val membershipRepository: MembershipRepository,
	private val passwordEncoder: PasswordEncoder,
	private val tokenService: TokenService,
) {

	/** Creates a global account. It has no organization yet, so the token carries no tenant. */
	fun signUp(request: SignUpRequest): AuthTokenResponse {
		val email = normalizeEmail(request.email)
		if (userRepository.existsByEmail(email)) throw ConflictException("An account with this e-mail already exists")
		val user = userRepository.save(
			AppUser(
				email = email,
				passwordHash = requireNotNull(passwordEncoder.encode(request.password)),
				fullName = request.fullName.trim(),
			),
		)
		return tokenService.issue(user, null)
	}

	@Transactional(readOnly = true)
	fun login(request: LoginRequest): AuthTokenResponse {
		val user = userRepository.findByEmail(normalizeEmail(request.email))
			?.takeIf { passwordEncoder.matches(request.password, it.passwordHash) }
			?: throw BadCredentialsException("Wrong e-mail or password")
		// The first organization the user joined becomes active; they can switch afterwards
		val membership = membershipRepository.findAllByUserIdOrderByCreatedAt(user.id).firstOrNull()
		return tokenService.issue(user, membership)
	}

	/** Issues a token for another of the caller's organizations; refused for one they don't belong to. */
	@Transactional(readOnly = true)
	fun switchOrganization(userId: UUID, organizationId: UUID): AuthTokenResponse {
		val membership = membershipRepository.findByUserIdAndOrganizationId(userId, organizationId)
			?: throw AccessDeniedException("Not a member of this organization")
		return tokenService.issue(membership.user, membership)
	}

}

package com.fleetlog.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
	private val properties: SecurityProperties,
) {

	@Bean
	fun securityFilterChain(http: HttpSecurity, jwtConverter: FleetLogJwtConverter): SecurityFilterChain {
		http
			.csrf { it.disable() } // stateless bearer-token API, no cookies
			.cors { }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				it
					.requestMatchers(HttpMethod.POST, "/api/auth/sign-up", "/api/auth/login").permitAll()
					.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
					.requestMatchers("/api/hello").permitAll()
					// Managing the active organization's people is for Fleet Managers only (SPEC-08)
					.requestMatchers("/api/organization/**").hasRole("FLEET_MANAGER")
					.anyRequest().authenticated()
			}
			.oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtConverter) } }
		return http.build()
	}

	@Bean
	fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(signingKey()))

	@Bean
	fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(signingKey())
		.macAlgorithm(MacAlgorithm.HS256)
		.build()
		.apply { setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER)) }

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	@Bean
	fun corsConfigurationSource(): CorsConfigurationSource {
		val configuration = CorsConfiguration().apply {
			allowedOrigins = properties.corsAllowedOrigins
			allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			allowedHeaders = listOf("*")
		}
		return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
	}

	private fun signingKey(): SecretKey = SecretKeySpec(properties.jwtSecret.toByteArray(), "HmacSHA256")

}

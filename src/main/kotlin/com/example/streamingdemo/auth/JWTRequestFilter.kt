package com.example.streamingdemo.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date

@Component
class JwtRequestFilter(
  private val jwtService: JwtService,
  private val encoder: PasswordEncoder
) : OncePerRequestFilter() {

  /*
   * **IMPORTANT**
   * This makes sure that the context is available after the async dispatch
   */
  private val repo = RequestAttributeSecurityContextRepository()

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    chain: FilterChain,
  ) {
    val bearer = request.getHeader("Authorization")
    if (bearer?.startsWith("Bearer ") == true) {
      val claims = jwtService.claims(bearer.substring(7))
      if (
        null == SecurityContextHolder.getContext().authentication
        && claims.subject == "admin"
        && claims.expiration.after(Date())
      ) {
        val admin = User(
          "admin",
          encoder.encode("admin"),
          listOf(SimpleGrantedAuthority("ADMIN"))
        )
        val context = SecurityContextHolder.createEmptyContext().apply {
          authentication = UsernamePasswordAuthenticationToken(
            admin,
            null,
            admin.authorities
          ).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
          }
        }
        // Everything above this point is your regular auth code

        SecurityContextHolder.setContext(context)
        // **IMPORTANT**
        // This makes sure that the context is available after the async dispatch
        repo.saveContext(context, request, response)
      }
    }
    chain.doFilter(request, response)
  }
}

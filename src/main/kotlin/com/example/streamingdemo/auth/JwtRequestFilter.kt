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
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date

@Component
class JwtRequestFilter(
  private val jwtService: JwtService,
  private val encoder: PasswordEncoder
) : OncePerRequestFilter() {

  /*
   * **IMPORTANT:**
   * Default this is `true,
   * and it will result Server Sent Events to result in Access Denied when finished.
   */
  override fun shouldNotFilterAsyncDispatch(): Boolean =
    false

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
        val context = SecurityContextHolder.getContext().apply {
          authentication = UsernamePasswordAuthenticationToken(
            admin,
            null,
            admin.authorities
          ).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
          }
        }
        SecurityContextHolder.setContext(context)
      }
    }
    chain.doFilter(request, response)
  }
}

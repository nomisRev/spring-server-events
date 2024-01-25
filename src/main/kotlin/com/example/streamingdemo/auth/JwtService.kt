package com.example.streamingdemo.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date

@Service
class JwtService(
  @Value("\${jwt.secret}")
  private val secret: String,
  @Value("\${jwt.expiresAfter}")
  private val expiresAfter: Long,
) {
  private val key = Keys.hmacShaKeyFor(secret.toByteArray())

  fun generateToken(subject: String): String =
    Jwts.builder()
      .setSubject(subject)
      .setIssuedAt(Date())
      .setExpiration(Date(System.currentTimeMillis() + expiresAfter))
      .signWith(key, SignatureAlgorithm.HS512)
      .compact()

  fun claims(token: String): Claims =
    Jwts.parserBuilder()
      .setSigningKey(key)
      .build()
      .parseClaimsJws(token)
      .body
}

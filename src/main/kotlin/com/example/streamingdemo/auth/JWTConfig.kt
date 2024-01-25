package com.example.streamingdemo.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class JWTConfig {
  @Bean
  fun securityFilterChain(
    http: HttpSecurity,
    authenticationProvider: AuthenticationProvider,
    filter: JwtRequestFilter,
  ): SecurityFilterChain =
    http.authorizeHttpRequests { auth ->
      auth
        .requestMatchers("token").permitAll()
        .anyRequest().authenticated()
    }
      .cors { it.disable() }
      .csrf { it.disable() }
      .addFilterBefore(filter, UsernamePasswordAuthenticationFilter::class.java)
      .authenticationProvider(authenticationProvider)
      .build()

  @Bean
  fun encoder(): PasswordEncoder = BCryptPasswordEncoder()

  @Bean
  fun authenticationProvider(encoder: PasswordEncoder): AuthenticationProvider =
    DaoAuthenticationProvider().apply {
      setUserDetailsService {
        User(
          "admin",
          encoder.encode("admin"),
          listOf(SimpleGrantedAuthority("ADMIN"))
        )
      }
      setPasswordEncoder(encoder)
    }

  @Bean
  fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
    config.authenticationManager

  @Bean
  fun asyncDispatcher(taskExecutor: AsyncTaskExecutor): CoroutineDispatcher =
    taskExecutor.asCoroutineDispatcher()
}

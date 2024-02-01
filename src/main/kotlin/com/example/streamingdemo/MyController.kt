package com.example.streamingdemo

import com.example.streamingdemo.auth.JwtService
import com.example.streamingdemo.coroutines.SecurityCoroutineContext
import com.example.streamingdemo.coroutines.SpringScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

@RestController
class MyController(
  private val authenticationManager: AuthenticationManager,
  private val jwtService: JwtService,
  private val scope: SpringScope,
  private val coroutineDispatcher: CoroutineDispatcher
) {

  @GetMapping("/token")
  fun token(): String {
    authenticationManager.authenticate(UsernamePasswordAuthenticationToken("admin", "admin"))
    return jwtService.generateToken("admin")
  }

  private val mockStream =
    flowOf(1, 2, 3, 4)
      .onEach { delay(1000) }

  @GetMapping("/async")
  fun future(): CompletableFuture<String> =
    scope.future {
      delay(3000)
      "Hello World after 3 seconds"
    }

  @GetMapping("/events")
  fun responseBodyEmitter(): ResponseBodyEmitter =
    ResponseBodyEmitter().apply {
      scope.launch(SecurityCoroutineContext() + MDCContext()) {
        try {
          mockStream.collect(::send)
          complete()
        } catch (e: Throwable) {
          completeWithError(e)
        }
      }
    }

  @GetMapping("/events2")
  fun sseEmitter(): SseEmitter = SseEmitter().apply {
    scope.launch(SecurityCoroutineContext() + MDCContext()) {
      try {
        mockStream.collect(::send)
        complete()
      } catch (e: Throwable) {
        completeWithError(e)
      }
    }
  }

  /** This will **only** stream in case `A` is `String`. */
  @GetMapping("/events3")
  fun flow(): Flow<String> =
    mockStream
      .map { it.toString() }
      .flowOn(coroutineDispatcher + SecurityCoroutineContext() + MDCContext())
}

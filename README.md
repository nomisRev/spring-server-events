# spring-server-events

Spring MVC example of server sent events and Spring Security

## Requirements

1. Spring dependencies (Web, Security & Jackson)
2. JWT dependencies (jsonwebtoken api, impl & jackson)
3. KotlinX Integrations (Coroutines & SLF4J)
4. Optional: KotlinX Reactor & Reactive Streams

## Locally running the server

```console
./gradlew bootRun
```

If the Spring server is started, you can curl the endpoints.
First we need to retrieve the token:

```console
curl --location --no-buffer --request GET 'localhost:8080/token'
```

And then we need replace `{token}` with the result of the previous `curl` command.

```console
curl --location --no-buffer --request GET 'localhost:8080/events' \
--header 'Authorization: Bearer {token}'
```

You can also try `event2`, and `events3` to respectively test `SseEmitter` and `Flow`.

## Security

Only the bare minimum is implemented here in terms of Security to keep the example simple.
We use a hardcoded user with `username = admin` and `password = admin`,
we gave him `ADMIN` role for example use cases.

You can find all the relevant code in `com.example.streamingdemo.auth`, and is set up as usual:

Configuring Spring is done using `@EnableWebSecurity`, `SecurityFilterChain`,
`PasswordEncoder`, `AuthenticationProvider` & `AuthenticationManager`. Nothing special needs to be configured here.

**Important:** for Spring to be able to complete request processing after the server sent all its events we need to
use `RequestAttributeSecurityContextRepository`! If this is not done it'll result in `AccessDeniedException`!
We need to save the `SecurityHolderContext` into the `RequestAttributeSecurityContextRepository`
using `saveContext(context, request, response)`.
This necessary since Spring
6, [Require Explicit Saving of SecurityContextRepository](https://docs.spring.io/spring-security/reference/5.8/migration/servlet/session-management.html#_require_explicit_saving_of_securitycontextrepository).

See [JWTRequestFilter](https://github.com/nomisRev/spring-server-events/blob/dd671b3dd8a750707451d171d9fd0c10ded1aaaf/src/main/kotlin/com/example/streamingdemo/auth/JWTRequestFilter.kt#L61),
for practical details.

### SecurityHolderContext & MDC

`SecurityHolderContext` and `MDC` are `ThreadLocal` constructs,
and thus they're not properly propagated between _dispatched_ coroutines.

We want both to be properly managed throughout KotlinX Coroutines,
and therefore we use `ThreadContextElement`. This gives us the opportunity to `updateThreadContext`,
and `restoreThreadContext` whenever we enter or exit a coroutine. Such that the state is properly maintained.

Luckily KotlinX already implements one for `MDC` out-of-the-box, but not for `SecurityHolderContext`.
[The SecurityCoroutineContext implementation can be found here](https://github.com/nomisRev/spring-server-events/blob/main/src/main/kotlin/com/example/streamingdemo/coroutines/SecurityCoroutineContext.kt).

### SpringScope

In order to _launch_ a coroutine, we need a KotlinX `CoroutineScope`,
this is important such that the lifecycle of the coroutines is properly maintained to the Spring application lifecycle.
The easiest way to do this is to implement `DestroyableBean`, and make our implementing class a `@Component`.

By backing the `CoroutineScope` with a `SupervisorJob` a child doesn't fail and cancel the parent.
This means that **all** children have to handle their own errors, but luckily all uncaught errors are properly logged
thanks to `CoroutineExceptionHandler`.

We run these coroutines on Spring's `AsyncTaskExecutor`, which we convert into a `CoroutineDispather`.

[The SpringScope implementation can be found here](https://github.com/nomisRev/spring-server-events/blob/main/src/main/kotlin/com/example/streamingdemo/coroutines/SpringScope.kt).

### Server Sent events

We have 3 options of sending server side events:

1. ResponseBodyEmitter
2. SseEmitter
3. KotlinX Flow

##### ResponseBodyEmitter

`ResponseBodyEmitter` allows us to `send` messages and `complete` or `completeWithError` the emitter.
This can easily be done by combining `SpringScope`, `SecurityCoroutineContext`, and `MDCContext` explained above.

As you can see in the snippet below:

1. we construct a `ResponseBodyEmitter`
2. Launch a coroutine on a managed `SpringScope`, setting up the proper contexts
3. We `try/catch` collecting our `Flow`, and if finished we `complete` the emitter.
   If something went wrong we `completeWithError` the emitter.

```kotlin
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
```

This is really neat, and powerful since `send` allows us to send different kind of messages with different `MediaType`.

##### SseEmitter

`SseEmitter` add some convenience methods on top of `ResponseBodyEmitter`,
but some might be undesired for example it prefixes all send data with `data:`.

As you can see, the resulting code is identical.

```kotlin
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
```

##### Flow

Directly returning `Flow` to Spring MVC is possible, and Spring will
use [ReactiveAdapterRegistry](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/ReactiveAdapterRegistry.html).

Be careful since this requires Reactive Streams, and Reactor to be on the classpath even though it is not used by us
directly.

```kotlin
// Required for Flow -> SseEmitter
implementation("org.reactivestreams:reactive-streams:1.0.4")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
```

This solution looks simplest from the `Controller` point-of-view,
but some care is required because it might become "blocking" depending on the type.

`Flow<String` streams correctly over the network, but `Flow<Int>` becomes blocking which is not the case
for `ResponseBodyEmitter` or `SseEmitter` although the might internally convert to `toString()`.

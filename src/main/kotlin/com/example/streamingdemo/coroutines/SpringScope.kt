package com.example.streamingdemo.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@Component
class SpringScope(coroutineDispatcher: CoroutineDispatcher) : CoroutineScope, DisposableBean {
  private val logger: Logger = LoggerFactory.getLogger(SpringScope::class.java)
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext =
    coroutineDispatcher + job + CoroutineExceptionHandler { context, throwable ->
      if (throwable !is CancellationException) {
        val coroutineName = context[CoroutineName] ?: context.toString()
        logger.error("Unhandled exception caught for $coroutineName", throwable)
      }
    }

  override fun destroy() = runBlocking {
    job.cancelAndJoin()
  }
}
package org.naphi.commons

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class IncrementingThreadFactory(private val prefix: String) : ThreadFactory {
    private val adder = AtomicInteger()
    override fun newThread(r: Runnable) = Thread(r).also { it.name = "$prefix-${adder.getAndIncrement()}" }
}

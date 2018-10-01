package com.loafofpiecrust.turntable.util

import android.support.v4.util.Pools
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.selects.select
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

/* Copyright (c) 2008-2018, Nathan Sweet
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */



class InstancePool<T: Any>(
    private val capacity: Int,
    private val builder: () -> T
) {
    // map of instance to currently executing usage job.
    private val instances = IdentityHashMap<T, Job>()

    private inner class Task(
        val context: CoroutineContext,
        val block: suspend (T) -> Unit
    )
    private val box = actor<Task>(BG_POOL, capacity = Channel.UNLIMITED) {
        // events are an acquisition request
        consumeEach { task ->
            val x = if (instances.size < capacity) {
                builder.invoke()
            } else select {
                for ((x, job) in instances) {
                    job.onJoin {
                        x
                    }
                }
            }

            instances[x] = async(task.context) {
                task.block(x)
            }
        }
    }

    fun <R> acquireBlocking(block: suspend (T) -> R) = runBlocking { acquire(BG_POOL, block) }

    suspend fun <R> acquire(context: CoroutineContext = BG_POOL, block: suspend (T) -> R): R {
        return suspendCoroutine { cont ->
            box.offer(Task(context) {
                cont.resume(block(it))
            })
        }
    }
}
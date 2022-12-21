package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.NoLeftRedirections
import java.time.Duration
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Bucket4j
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

interface LimitRedirectUseCase{
    fun addLimit(limit: Int, hash: String)
    fun consume(hash: String)
}

class LimitRedirectUseCaseImpl() : LimitRedirectUseCase{
    private val buckets : ConcurrentHashMap<String, Bucket> = ConcurrentHashMap()
    override fun addLimit(limit: Int, hash: String): Unit = runBlocking {
        GlobalScope.launch {
            val rate = Bandwidth.simple(limit.toLong(), Duration.ofHours(1))
            buckets[hash] = Bucket4j.builder().addLimit(rate).build()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun consume(hash: String){
        val bucket = buckets[hash]
        if (bucket != null) {
            if (!bucket.tryConsume(1)) {
                throw NoLeftRedirections(hash)
            }
        }
    }
}
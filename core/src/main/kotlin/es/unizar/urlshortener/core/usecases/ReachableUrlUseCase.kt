package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.UrlNotReachable
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture

interface ReachableUrlUseCase {
    fun isReachable(key: String)
}

class ReachableUrlUseCaseImpl() :
    ReachableUrlUseCase {
        @OptIn(DelicateCoroutinesApi::class)
        override fun isReachable(key: String): Unit = runBlocking {
            GlobalScope.launch {
                try {
                    withTimeout(12000) {
                        val urlCheck = URL(key)
                        val connection: HttpURLConnection =
                            withContext(Dispatchers.IO) {
                                urlCheck.openConnection()
                            } as HttpURLConnection
                        val status = connection.responseCode
                        if (status != 200) {
                            println("El codigo no deveulve bien :" + status)
                            throw UrlNotReachable(key) // Si existe el host destino pero no es un OK
                        }
                    }
                } catch (e: UnknownHostException) { //Si no existe el host destino
                    throw UrlNotReachable(key)
                }
            }
        }
    }
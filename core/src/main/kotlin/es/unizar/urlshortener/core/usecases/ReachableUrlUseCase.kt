package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.UrlNotReachable
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

interface ReachableUrlUseCase {
    fun isReachable(key: String)
}

class ReachableUrlUseCaseImpl() :
    ReachableUrlUseCase {

        override fun isReachable(url: String) = runBlocking {
            val job = launch {
                try {
                    withTimeout(12000) {
                        val urlCheck = URL(url)
                        val connection: HttpURLConnection = urlCheck.openConnection() as HttpURLConnection
                        val status = connection.responseCode
                        if (status != 200) {
                            println("El codigo no deveulve bien :" + status)
                            throw UrlNotReachable(url) // Si existe el host destino pero no es un OK
                        }
                    }
                } catch (e: UnknownHostException) { //Si no existe el host destino
                    throw UrlNotReachable(url)
                }
            }
            job.join()
        }
    }
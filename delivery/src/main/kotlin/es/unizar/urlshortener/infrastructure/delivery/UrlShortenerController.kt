package es.unizar.urlshortener.infrastructure.delivery

import auxiliar.Graphics
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import javax.servlet.http.HttpServletRequest

/**
 * The specification of the controller.
 */
interface UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * **Note**: Delivery of use cases [RedirectUseCase] and [LogClickUseCase].
     */
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Void>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>
    fun qrcode(@PathVariable id: String): ResponseEntity<ByteArray>

    fun getDataGraphic(): ResponseEntity<JSONMetricResponse>
}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val qrcode: String? = null,
    val limit: Int? = null
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val properties: Map<String, Any> = emptyMap(),
)

data class JSONMetricResponse(
    val metric: Map<String, ArrayList<out Any>>? = null
)

/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */
@RestController
class UrlShortenerControllerImpl(
        val redirectUseCase: RedirectUseCase,
        val logClickUseCase: LogClickUseCase,
        val createShortUrlUseCase: CreateShortUrlUseCase,
        val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
        val urlCounter: Counter = Counter.builder("url")
        .description("URLs shortened")
        .register(registry),
        val graphic: Graphics = Graphics(),
        val limitRedirectUseCase: LimitRedirectUseCase,
        val getBrowserAndOS: UserAgentInfoImpl = UserAgentInfoImpl(),
    ) : UrlShortenerController {
    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Void> = runBlocking {
        redirectUseCase.redirectTo(id).let {
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            getBrowser(request)
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr))
            try {
                limitRedirectUseCase.consume(id)
                ResponseEntity<Void>(h, HttpStatus.valueOf(it.mode))
            } catch (e: NoLeftRedirections) {
                throw NoLeftRedirections(e.url)
            }
        }
    }

    private fun getBrowser(request: HttpServletRequest) = runBlocking {
        val deferred = CompletableFuture<Array<String>>()
        GlobalScope.launch {
            //https://gist.github.com/c0rp-aubakirov/a4349cbd187b33138969
            val y = request.getHeader("User-Agent")
            val browser = getBrowserAndOS.getBrowser(y)
            val os = getBrowserAndOS.getOS(y)
            deferred.complete(
                arrayOf(browser, os)
            )
        }
        withContext(Dispatchers.IO) {
            deferred.thenApply {
                println("El navegador es: " + it[0] + " y el SO es: " + it[1])
            }
        }
    }
    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        try {
            val currentTimeMillisStart = System.currentTimeMillis()
            var limit = 0
            if(data.limit != null && data.limit > 0 ){
                limit = data.limit
            }
            createShortUrlUseCase.create(
                url = data.url,
                data = ShortUrlProperties(
                    ip = request.remoteAddr,
                    qrcode = data.qrcode.equals("on")
                ),
                limit = limit
            ).let {
                val h = HttpHeaders()
                val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
                urlCounter.increment()
                h.location = url
                h.contentType = MediaType.APPLICATION_JSON
                val response = ShortUrlDataOut(
                    url = url,
                    properties = mapOf(
                        "safe" to it.properties.safe
                    )
                )
                graphic.save((System.currentTimeMillis()-currentTimeMillisStart).toDouble(),
                                currentTimeMillisStart.toDouble())
                ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
            }
        } catch (e: InvalidUrlException) {
            throw InvalidUrlException(e.url)
        } catch (e: UrlNotReachable) {
            throw RedirectionNotFound(e.url)
        } catch(e: UrlAlreadyExists){
            throw UrlAlreadyExists(e.url)
        }

    @OptIn(DelicateCoroutinesApi::class)
    @GetMapping("/api/graphic")
    override fun getDataGraphic(): ResponseEntity<JSONMetricResponse> = runBlocking {
        val deferred = CompletableFuture<JSONMetricResponse>()
        GlobalScope.launch {
            val listData  = graphic.getListData()
            val listLabel = graphic.getListLabel()
            deferred.complete(
                JSONMetricResponse(
                    mapOf(
                        "newData" to listData,
                        "newLabel" to listLabel
                    )
                )
            )
        }
        withContext(Dispatchers.IO) {
            deferred.thenApply {
                val h = HttpHeaders()
                ResponseEntity<JSONMetricResponse>(it, h, HttpStatus.OK)
            }.get()
        }

    }
    @GetMapping("/{id}/qrcode")
    override fun qrcode(@PathVariable id: String): ResponseEntity<ByteArray> = runBlocking {
        redirectUseCase.getShortUrl(id).let {
            if (it.properties.qrcode) {
                val deferred = CompletableFuture<ByteArray>()
                GlobalScope.launch {
                    val qrCodeWriter = QRCodeWriter()
                    // Generate the QR code
                    val qrCode = qrCodeWriter.encode(it.redirection.target, BarcodeFormat.QR_CODE, 200, 200)
                    // Save the QR code as an image file
                    val image = MatrixToImageWriter.toBufferedImage(qrCode)
                    val outputStream = ByteArrayOutputStream()
                    ImageIO.write(image, "PNG", outputStream)
                    deferred.complete(
                        outputStream.toByteArray()
                    )
                }
                withContext(Dispatchers.IO) {
                    deferred.thenApply {
                        val h = HttpHeaders()
                        h.contentType = MediaType.IMAGE_PNG
                        ResponseEntity(it, h, HttpStatus.OK)
                    }.get()
                }
            } else {
                ResponseEntity(HttpStatus.BAD_REQUEST)
            }
        }
    }
}

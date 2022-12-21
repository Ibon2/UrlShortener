package es.unizar.urlshortener.infrastructure.delivery

import auxiliar.Graphics
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.UrlNotReachable
import es.unizar.urlshortener.core.usecases.*
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
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
    fun qrcode(@PathVariable id: String): ResponseEntity<ByteArray?>

    fun getDataGraphic(): ResponseEntity<JSONMetricResponse>
}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val sponsor: String? = null,
    val qrcode: String? = null
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
    val graphic: Graphics = Graphics()
    ) : UrlShortenerController {
    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Void> =
        redirectUseCase.redirectTo(id).let {
            //https://gist.github.com/c0rp-aubakirov/a4349cbd187b33138969
            val getBrowserAndOS = UserAgentInfoImpl()
            var y = request.getHeader("User-Agent")
            var browser = getBrowserAndOS.getBrowser(y)
            var os = getBrowserAndOS.getOS(y)
            println("El navegador es: " + browser + " y el SO es: " + os)
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr))
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            ResponseEntity<Void>(h, HttpStatus.valueOf(it.mode))
        }

    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        try {
            val currentTimeMillisStart = System.currentTimeMillis()
            var qrcodeExists = false
            if (data.qrcode != null) {
                qrcodeExists = true
                println("No es nulo asi que generamos ByteArray")
                /*val qrCodeWriter = QRCodeWriter()
                // Generate the QR code
                val qrCode = qrCodeWriter.encode(data.url, BarcodeFormat.QR_CODE, 200, 200)

                // Save the QR code as an image file
                val image = MatrixToImageWriter.toBufferedImage(qrCode)
                outputStream = ByteArrayOutputStream()
                ImageIO.write(image, "PNG", outputStream)*/
                //imageBytes = outputStream.toByteArray()
            }
            createShortUrlUseCase.create(
                url = data.url,
                data = ShortUrlProperties(
                    ip = request.remoteAddr,
                    sponsor = data.sponsor,
                    qrcode = qrcodeExists
                )
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
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val response = ShortUrlDataOut(
                properties = mapOf(
                    "error" to e.message.toString()
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.BAD_REQUEST)
        } catch (e: UrlNotReachable) {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val response = ShortUrlDataOut(
                properties = mapOf(
                    "error" to e.message.toString()
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.BAD_REQUEST)
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
    override fun qrcode(@PathVariable id: String): ResponseEntity<ByteArray?> =
            redirectUseCase.getShortUrl(id).let {
                println("En let con it qrcode: "+it.properties.qrcode)
                if(it.properties.qrcode){
                    val qrCodeWriter = QRCodeWriter()
                    // Generate the QR code
                    val qrCode = qrCodeWriter.encode(it.redirection.target, BarcodeFormat.QR_CODE, 200, 200)

                    // Save the QR code as an image file
                    val image = MatrixToImageWriter.toBufferedImage(qrCode)
                    val outputStream = ByteArrayOutputStream()
                    ImageIO.write(image, "PNG", outputStream)
                    val imageBytes = outputStream.toByteArray()
                    val headers = HttpHeaders()
                    headers.contentType = MediaType.IMAGE_PNG
                    //val imageByte = it.toByteArray()
                    // Return the QR code image in the response
                    ResponseEntity(imageBytes, headers, HttpStatus.OK)
                }else{
                    ResponseEntity(HttpStatus.BAD_REQUEST)
                }

        }

}

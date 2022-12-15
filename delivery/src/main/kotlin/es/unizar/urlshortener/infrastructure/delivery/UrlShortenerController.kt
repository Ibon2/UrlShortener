package es.unizar.urlshortener.infrastructure.delivery

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.jayway.jsonpath.JsonPath
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.UrlNotReachable
import es.unizar.urlshortener.core.usecases.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    fun metrics(): ResponseEntity<InfoMetricsResponse>
    fun urlTotal(): ResponseEntity<JSONMetricResponse>
    fun cpuUsage(): ResponseEntity<JSONMetricResponse>
    fun uptime(): ResponseEntity<JSONMetricResponse>
    fun qrcode(@RequestParam url: String): ResponseEntity<ByteArray>
}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val sponsor: String? = null,
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val properties: Map<String, Any> = emptyMap(),
)

/**
 * Data metrics of the url.
 */
data class InfoMetricsResponse(
    val list: Map<String, String>? = null
)

/**
 * Data metrics of the url.
 */
data class JSONMetricResponse(
    val metric: Map<String, String>? = null
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
    val registry: MeterRegistry,
    val urlCounter: Counter = Counter.builder("url")
                                .description("URLs shortened")
                                .register(registry)
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
            createShortUrlUseCase.create(
                url = data.url,
                data = ShortUrlProperties(
                    ip = request.remoteAddr,
                    sponsor = data.sponsor
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
                ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
            }
        } catch(e: InvalidUrlException){
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val response = ShortUrlDataOut(
                properties = mapOf(
                    "error" to e.message.toString()
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.BAD_REQUEST)
        } catch(e: UrlNotReachable){
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val response = ShortUrlDataOut(
                properties = mapOf(
                    "error" to e.message.toString()
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.BAD_REQUEST)
        }
    @GetMapping("/api/metrics")
    override fun metrics(): ResponseEntity<InfoMetricsResponse> =
        let {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val response = InfoMetricsResponse(
                list = mapOf(
                    "recortadas" to "Nº URLS recortadas",
                    "cpu" to "Uso de la CPU",
                    "time" to "Tiempo total de la maquina",
                    "cuatro" to "info metrica 4"
                )
            )
            ResponseEntity<InfoMetricsResponse>(response, h, HttpStatus.OK)
        }
    @GetMapping("/api/metrics/url")
    override fun urlTotal(): ResponseEntity<JSONMetricResponse> =
        let {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val client = HttpClient.newBuilder().build();
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/actuator/metrics/url"))
                .build();
            val total = client.send(request, HttpResponse.BodyHandlers.ofString());

            val context = JsonPath.parse(total.body())
            val name: String = context.read("name")
            val description: String = context.read("description")
            val measurement: String = context.read<Double>("measurements[0].value").toString()
            val response = JSONMetricResponse(
                mapOf(
                    "name" to name,
                    "description" to description,
                    "measurement" to measurement
                )
            )
            ResponseEntity<JSONMetricResponse>(response, h, HttpStatus.OK)
        }
    @GetMapping("/api/metrics/cpu")
    override fun cpuUsage(): ResponseEntity<JSONMetricResponse> =
        let {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val client = HttpClient.newBuilder().build();
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/actuator/metrics/process.cpu.usage"))
                .build();
            val total = client.send(request, HttpResponse.BodyHandlers.ofString());

            val context = JsonPath.parse(total.body())
            val name: String = context.read("name")
            val description: String = context.read("description")
            val measurement: String = context.read<Double>("measurements[0].value").toString()
            val response = JSONMetricResponse(
                mapOf(
                    "name" to name,
                    "description" to description,
                    "measurement" to measurement
                )
            )
            ResponseEntity<JSONMetricResponse>(response, h, HttpStatus.OK)
        }

    @GetMapping("/api/metrics/uptime")
    override fun uptime(): ResponseEntity<JSONMetricResponse> =
        let {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val client = HttpClient.newBuilder().build();
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/actuator/metrics/process.uptime"))
                .build();
            val total = client.send(request, HttpResponse.BodyHandlers.ofString());

            val context = JsonPath.parse(total.body())
            val name: String = context.read("name")
            val description: String = context.read("description")
            val measurement: String = context.read<Double>("measurements[0].value").toString()
            val format: String = context.read("baseUnit")
            val response = JSONMetricResponse(
                mapOf(
                    "name" to name,
                    "description" to description,
                    "measurement" to measurement,
                    "format" to format
                )
            )
            ResponseEntity<JSONMetricResponse>(response, h, HttpStatus.OK)
        }
    @GetMapping("/qrcode")
    override fun qrcode(@RequestParam url: String): ResponseEntity<ByteArray> {

        val qrCodeWriter = QRCodeWriter()

        // Generate the QR code
        val qrCode = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 200, 200)

        // Save the QR code as an image file
        val image = MatrixToImageWriter.toBufferedImage(qrCode)
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", outputStream)
        val imageBytes = outputStream.toByteArray()

        // Set the headers for the response
        val headers = HttpHeaders()
        headers.contentType = MediaType.IMAGE_PNG

        // Return the QR code image in the response
        return ResponseEntity(imageBytes, headers, HttpStatus.OK)
    }
}

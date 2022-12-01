package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import net.minidev.json.JSONObject
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    val total: Map<String, String>? = null
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
    var registry: MeterRegistry,
    var urlCounter: Counter = Counter.builder("URL.shortened")
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
        createShortUrlUseCase.create(
            url = data.url,
            data = ShortUrlProperties(
                ip = request.remoteAddr,
                sponsor = data.sponsor
            )
        ).let {
            val h = HttpHeaders()
            val urlCheck = URL(data.url)
            val connection:HttpURLConnection = urlCheck.openConnection() as HttpURLConnection
            val status = connection.getResponseCode()
            if(status.equals(200)){
                println("Es buenoo")
                urlCounter.increment()
            }else{
                println("El codigo no deveulve bien :"+status)
            }
            val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
            h.location = url
            //h.contentType = MediaType.APPLICATION_JSON
            val response = ShortUrlDataOut(
                url = url,
                properties = mapOf(
                    "safe" to it.properties.safe
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
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
    @GetMapping("/api/metrics/URL")
    override fun urlTotal(): ResponseEntity<JSONMetricResponse> =
        let {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val client = HttpClient.newBuilder().build();
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/actuator/metrics/URL.shortened"))
                .build();
            val total = client.send(request, HttpResponse.BodyHandlers.ofString());
            val totalParse = total.body().replace('\\',' ')
            val response = JSONMetricResponse(
                mapOf(
                    "urlShortenedTotal" to totalParse
                )
            )
            ResponseEntity<JSONMetricResponse>(response, h, HttpStatus.OK)
        }
    @GetMapping("/api/metrics/CPU")
    override fun cpuUsage(): ResponseEntity<JSONMetricResponse> =
        let {
            val h = HttpHeaders()
            h.contentType = MediaType.APPLICATION_JSON
            val client = HttpClient.newBuilder().build();
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/actuator/metrics/process.cpu.usage"))
                .build();
            val total = client.send(request, HttpResponse.BodyHandlers.ofString());
            val totalParse = total.body().replace('\\',' ')
            val response = JSONMetricResponse(
                mapOf(
                    "CPUUsage" to totalParse // Formato: "{\"name\":\"process.uptime\",\"description\":\"The uptime of the Java virtual machine\",\"baseUnit\":\"seconds\",\"measurements\":[{\"statistic\":\"VALUE\",\"value\":107.651}],\"availableTags\":[]}"
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
            val totalParse = total.body().replace('\\',' ')
            val response = JSONMetricResponse(
                mapOf(
                    "uptime" to totalParse
                )
            )
            ResponseEntity<JSONMetricResponse>(response, h, HttpStatus.OK)
        }

}

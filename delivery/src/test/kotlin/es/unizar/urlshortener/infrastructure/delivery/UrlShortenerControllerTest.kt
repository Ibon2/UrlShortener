package es.unizar.urlshortener.infrastructure.delivery

import auxiliar.Graphics
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.LimitRedirectUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.OffsetDateTime

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class,
        UserAgentInfoImpl::class]
)
class UrlShortenerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var limitRedirectUseCase: LimitRedirectUseCase

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase


    /**
     * Test falla porque espera un header "User-Agent"
     * en la línea 108 de UrlShortenerController que no está
     * y al fallar nunca se completa y el progrma queda boqueado
     * en la línea 118 de UrlShortenerController
     *
     * Este test nunca debería de haber fallado ya que se basa en la lógica original.
     */
    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        given(
                createShortUrlUseCase.create(
                        url = "http://example.com/",
                        data = ShortUrlProperties(
                                ip = "127.0.0.1",
                                qrcode = true
                        ),
                        limit = 0
                )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

        given(redirectUseCase.redirectTo("f684a3c4")
        ).willReturn( Redirection("http://www.example.com/"))

        mockMvc.perform(
            post("/api/link")
                    .param("url", "http://example.com/")
                            .param("limit", "0")
                            .param("qrcode", "on")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{id}", "f684a3c4").header("User-Agent", "UrlAgentHeader"))
        .andExpect(status().isTemporaryRedirect)
        .andExpect(redirectedUrl("http://www.example.com/"))
    }

    /**
     * Este test pasa, pero es de los originales.
     */
    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        given(redirectUseCase.redirectTo("key"))
            .willAnswer { throw RedirectionNotFound("key") }

        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))

        verify(logClickUseCase, never()).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }
}

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class]
)
class CreateRedirectionTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var limitRedirectUseCase: LimitRedirectUseCase


    /**
     * Test falla al interpretar porque lo que se está pasando a `create` en el `given`
     * no se corresponde a lo que realmente se le pasa en el UrlShortenerController L134-142.
     */
    @Test
    fun `creates returns a basic redirect if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = false
                ),
                limit = 0
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))
        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("limit", "0")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
    }

    /**
     * Test falla al interpretar porque lo que se está pasando a `create` en el `given`
     * no se corresponde a lo que realmente se le pasa en el UrlShortenerController L134-142.
     */
    @Test
    fun `creates returns bad request if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = false
                ),
                limit = 0
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "ftp://example.com/")
                .param("limit", "0")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value(400))
    }
    @Test
    fun `creates returns bad request if url not reachable`() {
        given(
            createShortUrlUseCase.create(
                url = "http://exampleeeee.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = false
                ),
                limit = 0
            )
        ).willAnswer { throw RedirectionNotFound("http://exampleeeee.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://exampleeeee.com/")
                .param("limit", "0")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `creates returns conflict if url already exists`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = false
                ),
                limit = 0
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/"))
        ).willAnswer { throw UrlAlreadyExists("http://exampleeeee.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("limit", "0")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("limit", "0")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isConflict)
    }

}

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class]
)
class LeftRedirectionsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var limitRedirectUseCase: LimitRedirectUseCase

    /**
     * Test falla al interpretar porque lo que se está pasando a `create` en el `given`
     * no se corresponde a lo que realmente se le pasa en el UrlShortenerController L134-142.
     */
    @Test
    fun `redirectTo throws NoLeftRedirections when limit is reached`() {

        given(
            createShortUrlUseCase.create(
                url = "http://www.example.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = false
                ),
                limit = 1
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://www.example.com/")))

        given(
            redirectUseCase.redirectTo("f684a3c4")
        ).willReturn(
            Redirection("http://www.example.com/")
        ).willAnswer { throw NoLeftRedirections("http://localhost/f684a3c4") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://www.example.com/")
                .param("limit", "1")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{id}", "f684a3c4").header("User-Agent", "UrlAgentHeader"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://www.example.com/"))

        mockMvc.perform(get("/{id}", "f684a3c4").header("User-Agent", "UrlAgentHeader"))
            .andExpect(status().isGone)
    }

}

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class]
)
class QRTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var limitRedirectUseCase: LimitRedirectUseCase


    /**
     * Test falla al interpretar porque lo que se está pasando a `create` en el `given`
     * no se corresponde a lo que realmente se le pasa en el UrlShortenerController L134-142.
     */
    @Test
    fun `redirectTo returns valid qr if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "http://www.example.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = true
                ),
                limit = 0
            )
        ).willReturn(
            ShortUrl("f684a3c4", Redirection("http://www.example.com/"), OffsetDateTime.now(),ShortUrlProperties(qrcode = true)))
        given(
            redirectUseCase.getShortUrl("f684a3c4")
        ).willReturn(
            ShortUrl("f684a3c4", Redirection("http://localhost:8080/f684a3c4/qrcode"), OffsetDateTime.now(),ShortUrlProperties(qrcode = true))
        )

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://www.example.com/")
                .param("limit", "0")
                .param("qrcode", "on")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{hash}/qrcode", "f684a3c4"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE))

    }

    /**
     * Test falla al interpretar porque lo que se está pasando a `create` en el `given`
     * no se corresponde a lo que realmente se le pasa en el UrlShortenerController L134-142.
     */
    @Test
    fun `redirectTo throws bad request if qr is not selected`() {
        given(
            createShortUrlUseCase.create(
                url = "http://www.example.com/",
                data = ShortUrlProperties(
                    ip = "127.0.0.1",
                    qrcode = false
                ),
                limit = 0
            )
        ).willReturn(
            ShortUrl("f684a3c4", Redirection("http://www.example.com/")))

        given(
            redirectUseCase.getShortUrl("f684a3c4")
        ).willReturn(
            ShortUrl("f684a3c4", Redirection("http://localhost:8080/f684a3c4/qrcode"))
        )

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://www.example.com/")
                .param("limit", "0")
                .param("qrcode", "false")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{hash}/qrcode", "f684a3c4"))
            .andExpect(status().isBadRequest)

    }

}
@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class]
)
class GraphicTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var limitRedirectUseCase: LimitRedirectUseCase

    /**
     * Test falla al interpretar que newData devuelve la cadena "[]" en lugar de una lista vacía (a veces).
     */
    @Test
    fun `redirectTo returns metric list`() {
        given(
                createShortUrlUseCase.create(
                        url = "http://example.com/",
                        data = ShortUrlProperties(
                                ip = "127.0.0.1",
                                qrcode = true
                        ),
                        limit = 0
                )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

        given(
        redirectUseCase.redirectTo("f684a3c4")
        ).willReturn( Redirection("http://www.example.com/"))

        mockMvc.perform(
        post("/api/link")
                .param("url", "http://example.com/")
                        .param("limit", "0")
                        .param("qrcode", "on")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                )
                .andDo(print())
                .andExpect(status().isCreated)
                .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{id}", "f684a3c4").header("User-Agent", "UrlAgentHeader"))
        .andExpect(status().isTemporaryRedirect)
        .andExpect(redirectedUrl("http://www.example.com/"))

        mockMvc.perform(
        get("/api/graphic")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.metric.newData.[*]").isNotEmpty)
        .andExpect(jsonPath("$.metric.newLabel.[*]").isNotEmpty)

    }
}
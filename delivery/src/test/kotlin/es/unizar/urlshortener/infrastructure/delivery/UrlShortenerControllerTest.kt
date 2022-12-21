package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
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

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class]
)
class UrlShortenerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))

        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        verify(logClickUseCase).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }

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
        )

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

        mockMvc.perform(get("/{id}", "f684a3c4"))
            .andExpect(status().isOk)
            .andExpect(redirectedUrl("http://www.example.com/"))

        mockMvc.perform(get("/{id}", "f684a3c4"))
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }

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
                .param("qrcode", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{hash}/qr", "f684a3c4"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE))

    }

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
                .param("qrcode", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))

        mockMvc.perform(get("/{hash}/qr", "f684a3c4"))
            .andExpect(status().isBadRequest)

    }

    @Test
    fun `redirectTo returns metric list`() {
        mockMvc.perform(
            post("/api/graphic")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.metric.newData").value("[]"))
            .andExpect(jsonPath("$.metric.newLabel").value("[]"))

    }
}
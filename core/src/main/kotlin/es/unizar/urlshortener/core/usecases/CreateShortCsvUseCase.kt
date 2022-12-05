package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import javax.servlet.http.HttpServletRequest

data class CSVList(
    val hash: String,
)

interface CreateShortCsvUseCase {
    fun create(file: MultipartFile, request: HttpServletRequest): MutableMap<String,String>
}

class CreateShortCsvUseCaseImpl(
    private val createShortUrlUseCase: CreateShortUrlUseCase
) : CreateShortCsvUseCase {
    override fun create(file: MultipartFile, request: HttpServletRequest): MutableMap<String,String> =
        let{
            val map = mutableMapOf<String,String>()

            val reader: BufferedReader = file.inputStream.bufferedReader()
            val iterator = reader.lineSequence().iterator()
            while(iterator.hasNext()) {
                val line = iterator.next()
                try {
                    val url = createShortUrlUseCase.create(
                        url = line,
                        data = ShortUrlProperties(
                            ip = request.remoteAddr,
                            sponsor = null
                        ))
                    map[line]= url.hash
                } catch (e: InvalidUrlException) {
                    map[line]= e.message.toString()
                } catch (e: UrlNotReachable) {
                    map[line]= e.message.toString()
                }
            }
            reader.close()
            return map
        }
}
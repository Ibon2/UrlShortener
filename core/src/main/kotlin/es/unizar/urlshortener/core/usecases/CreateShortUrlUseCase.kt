package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*

/**
 * Given an url returns the key that is used to create a short URL.
 * When the url is created optional data may be added.
 *
 * **Note**: This is an example of functionality.
 */
interface CreateShortUrlUseCase {
    fun create(url: String, data: ShortUrlProperties, limit: Int): ShortUrl
}

/**
 * Implementation of [CreateShortUrlUseCase].
 */
class CreateShortUrlUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val validatorService: ValidatorService,
    private val hashService: HashService,
    private val reachableUrlUseCase: ReachableUrlUseCase,
    private val limitRedirectUseCase: LimitRedirectUseCase
) : CreateShortUrlUseCase {
    override fun create(url: String, data: ShortUrlProperties, limit: Int): ShortUrl =
        if (validatorService.isValid(url)) {
            reachableUrlUseCase.isReachable(
                url
            ).let {
                val id: String = hashService.hasUrl(url)
                shortUrlRepository.findByKey(id).let{
                    if(it != null){ //Existe la url en el repositorio
                        throw UrlAlreadyExists(url)
                    }else{ //No existe la url en el repositorio
                        val su = ShortUrl(
                            hash = id,
                            redirection = Redirection(target = url),
                            properties = ShortUrlProperties(
                                safe = data.safe,
                                ip = data.ip,
                                sponsor = data.sponsor,
                                qrcode = data.qrcode
                            )
                        )
                        if(limit > 0){
                            limitRedirectUseCase.addLimit(limit, id)
                        }
                        println("en create() "+su.properties.qrcode)
                        shortUrlRepository.save(su)
                    }
                }
            }
        } else {
            throw InvalidUrlException(url)
        }
}

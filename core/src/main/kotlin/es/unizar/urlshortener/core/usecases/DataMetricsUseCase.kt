package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import java.time.OffsetDateTime


data class InfoMetrics(
    val uno: String,
    val dos: String,
    val tres: String,
    val cuatro: String,
)

/**
 *
 */
interface DataMetricsUseCase {
    fun create(): InfoMetrics
    fun info(): InfoMetrics

}

/**
 * Implementation of [CreateDataMetricsUseCase].
 */
class DataMetricsUseCaseImpl()
    : DataMetricsUseCase {
        override fun create(): InfoMetrics =
            InfoMetrics(
                uno = "info metrica 1",
                dos = "info metrica 2",
                tres = "info metrica 3",
                cuatro = "info metrica 4"
            )

        override fun info(): InfoMetrics =
            InfoMetrics(
                uno = "info metrica 1",
                dos = "info metrica 2",
                tres = "info metrica 3",
                cuatro = "info metrica 4"
            )
    }

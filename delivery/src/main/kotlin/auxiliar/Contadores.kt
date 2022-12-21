package auxiliar

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry


class Contadores(){
    private val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val urlCounter: Counter = Counter.builder("url")
                                            .description("URLs shortened")
                                            .register(registry)
    fun getContadores(): Counter {
        return urlCounter
    }
}
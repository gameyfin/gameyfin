package de.grimsi.gameyfin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
public class WebClientConfig implements WebClientCustomizer {

    @Override
    public void customize(WebClient.Builder webClientBuilder) {
        webClientBuilder.filter(logResponse());
        webClientBuilder.filter(logRequest());

        // Enable use of system proxy
        webClientBuilder.clientConnector(new ReactorClientHttpConnector(HttpClient.create().proxyWithSystemProperties()));
    }

    /**
     * This fixes the wrong Content-Type in reponses of the IGDB API by overwriting it so the WebClient is able to parse it automatically
     * They return "application/protobuf", correct would be "application/x-protobuf"
     * @return the filter function
     */
    public static ExchangeFilterFunction fixProtobufContentTypeInterceptor() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse ->
                Mono.just(clientResponse.mutate()
                        .headers(headers -> headers.remove(HttpHeaders.CONTENT_TYPE))
                        .header(HttpHeaders.CONTENT_TYPE, String.valueOf(ProtobufHttpMessageConverter.PROTOBUF))
                        .build()));
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.debug("Response: {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    private ExchangeFilterFunction logRequest() {
        return (clientRequest, next) -> {
            log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
            return next.exchange(clientRequest);
        };
    }
}

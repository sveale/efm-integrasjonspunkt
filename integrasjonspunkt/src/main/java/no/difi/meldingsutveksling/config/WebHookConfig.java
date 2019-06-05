package no.difi.meldingsutveksling.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.difi.meldingsutveksling.webhooks.UrlPusher;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebHookConfig {

    private final IntegrasjonspunktProperties integrasjonspunktProperties;

    @Bean
    public UrlPusher urlPusher(RestTemplateBuilder restTemplateBuilder) {

        IntegrasjonspunktProperties.WebHooks webHooks = integrasjonspunktProperties.getWebhooks();

        return new UrlPusher(restTemplateBuilder
                .setConnectTimeout(webHooks.getConnectTimeout())
                .setReadTimeout(webHooks.getReadTimeout())
                .errorHandler(new DefaultResponseErrorHandler() {
                    @Override
                    public void handleError(ClientHttpResponse response) throws IOException {
                        HttpStatus statusCode = getHttpStatusCode(response);
                        log.info("Webhook push failed with: {} {}", statusCode, response.getStatusText());
                    }
                })
                .build());
    }
}

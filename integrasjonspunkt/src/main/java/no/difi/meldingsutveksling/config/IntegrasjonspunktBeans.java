package no.difi.meldingsutveksling.config;

import no.difi.meldingsutveksling.noarkexchange.IntegrasjonspunktImpl;
import no.difi.meldingsutveksling.queue.dao.QueueDao;
import no.difi.meldingsutveksling.queue.service.Queue;
import no.difi.meldingsutveksling.scheduler.QueueScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrasjonspunktBeans {
    @Autowired
    IntegrasjonspunktConfig config;

    @Bean
    public QueueScheduler queueScheduler(IntegrasjonspunktImpl integrasjonspunkt, IntegrasjonspunktConfig config) {
        return new QueueScheduler(queueService(), integrasjonspunkt, config);
    }

    @Bean
    public Queue queueService() {
        return new Queue(new QueueDao());
    }
}
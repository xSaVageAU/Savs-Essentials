package savage.essentials.nats.standalone.messaging;

import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.messaging.MessagingProvider;

public class NatsStandaloneMessagingProviderFactory implements MessagingProvider {
    @Override
    public String getId() {
        return "nats-standalone";
    }

    @Override
    public EssentialsMessaging create() {
        return new NatsMessagingProvider();
    }
}

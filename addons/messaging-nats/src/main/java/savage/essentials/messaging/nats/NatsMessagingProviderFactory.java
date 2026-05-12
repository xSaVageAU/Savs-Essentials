package savage.essentials.messaging.nats;

import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.messaging.MessagingProvider;

public class NatsMessagingProviderFactory implements MessagingProvider {
    @Override
    public String getId() {
        return "nats";
    }

    @Override
    public EssentialsMessaging create() {
        return new NatsMessagingProvider();
    }
}

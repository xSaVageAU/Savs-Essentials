package savage.essentials.api.messaging;

/**
 * Interface for mods providing a messaging backend.
 * Register this as an entrypoint in fabric.mod.json under "savs-essentials:messaging".
 */
public interface MessagingProvider {
    /**
     * @return The unique ID for this messaging type (e.g. "nats", "redis").
     */
    String getId();

    /**
     * @return A new instance of the messaging implementation.
     */
    EssentialsMessaging create();
}

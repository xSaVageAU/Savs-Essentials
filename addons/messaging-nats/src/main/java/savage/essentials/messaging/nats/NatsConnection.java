package savage.essentials.messaging.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

public class NatsConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats");
    private static Connection connection;

    public static synchronized Connection get() {
        if (connection == null || !connection.getStatus().equals(Connection.Status.CONNECTED)) {
            connect();
        }
        return connection;
    }

    private static void connect() {
        NatsConfig config = NatsConfig.get();
        try {
            Options.Builder optionsBuilder = new Options.Builder()
                    .server(config.url)
                    .connectionName("Savs-Essentials")
                    .maxReconnects(-1) // Unlimited reconnects
                    .reconnectWait(Duration.ofSeconds(2))
                    .connectionListener((conn, type) -> LOGGER.info("NATS Connection event: {}", type));

            if (config.token != null && !config.token.isEmpty()) {
                optionsBuilder.token(config.token.toCharArray());
            }

            connection = Nats.connect(optionsBuilder.build());
            LOGGER.info("Connected to NATS at {}", config.url);
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to connect to NATS at {}", config.url, e);
        }
    }

    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                LOGGER.info("NATS connection closed.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

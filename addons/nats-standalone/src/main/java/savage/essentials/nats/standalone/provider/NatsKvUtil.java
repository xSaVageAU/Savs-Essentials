package savage.essentials.nats.standalone.provider;

import com.github.luben.zstd.Zstd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.nats.standalone.NatsConfig;
import savage.essentials.nats.standalone.NatsConnection;

import java.nio.charset.StandardCharsets;

public class NatsKvUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats-standalone");
    private static final Gson GSON = new GsonBuilder().create();
    private static KeyValue kvInstance;

    public static synchronized KeyValue getKv() {
        if (kvInstance == null) {
            Connection conn = NatsConnection.get();
            NatsConfig config = NatsConfig.get();
            try {
                try {
                    conn.keyValueManagement().create(KeyValueConfiguration.builder().name(config.kvBucket).build());
                } catch (Exception ignored) {}
                kvInstance = conn.keyValue(config.kvBucket);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize NATS KV", e);
            }
        }
        return kvInstance;
    }

    public static byte[] compress(byte[] data) {
        return Zstd.compress(data, 1);
    }

    public static byte[] decompress(byte[] compressedData) {
        long size = Zstd.getFrameContentSize(compressedData);
        if (size < 0) {
            throw new RuntimeException("Failed to decompress NATS payload: Invalid or unknown Zstd frame size (" + size + ")");
        }
        return Zstd.decompress(compressedData, (int) size);
    }

    public static <T> T parseWire(byte[] data, Class<T> clazz) throws Exception {
        if (data == null) return null;
        byte[] decompressed = decompress(data);
        return GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), clazz);
    }

    public static <T> T readFromKv(KeyValue kv, String key, Class<T> clazz) {
        try {
            var entry = kv.get(key);
            return entry != null ? parseWire(entry.getValue(), clazz) : null;
        } catch (Exception e) {
            LOGGER.error("Failed to read {} from KV", key, e);
            return null;
        }
    }

    public static void writeToKv(KeyValue kv, String key, Object data) throws Exception {
        byte[] compressed = compress(GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        kv.put(key, compressed);
    }

    public record ProfileWire(String serverId, String playerUuid, Profile profile) {}
    public record WarpWire(String serverId, String name, Warp warp, boolean deleted) {}
}

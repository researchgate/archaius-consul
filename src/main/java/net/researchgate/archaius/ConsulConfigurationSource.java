package net.researchgate.archaius;

import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.KeyValueClient;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.google.common.io.BaseEncoding;
import com.netflix.config.PollResult;
import com.netflix.config.PolledConfigurationSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsulConfigurationSource implements PolledConfigurationSource {

    private final KeyValueClient client;
    private final String facility;
    private final int length;

    public ConsulConfigurationSource(KeyValueClient client, String facility) {
        this.client = client;
        this.facility = facility;
        this.length = facility.length() + 1;
    }

    @Override
    public PollResult poll(boolean initial, Object checkPoint) throws Exception {
        Response<List<GetValue>> kvValues = client.getKVValues(facility);
        Map<String, Object> properties = new HashMap<>();
        for (GetValue value : kvValues.getValue()) {
            if (!value.getKey().endsWith("/")) {
                byte[] decoded = BaseEncoding.base64().decode(value.getValue());
                properties.put(value.getKey().substring(length), new String(decoded));
                System.err.println("Add " + value.getKey().substring(length));
            }
        }
        return PollResult.createFull(properties);
    }

}

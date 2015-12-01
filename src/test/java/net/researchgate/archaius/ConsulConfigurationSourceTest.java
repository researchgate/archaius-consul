package net.researchgate.archaius;

import static org.mockito.Mockito.when;

import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.KeyValueClient;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.google.common.io.BaseEncoding;
import com.netflix.config.DynamicConfiguration;
import com.netflix.config.FixedDelayPollingScheduler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ConsulConfigurationSourceTest {

    private final String facility = "testFacility";

    private ConsulConfigurationSource consulConfigurationSource;

    @Mock
    private KeyValueClient consulClient;

    private Response<List<GetValue>> response;
    private DynamicConfiguration dc;

    @Before
    public void setUp() throws Exception {
        consulConfigurationSource = new ConsulConfigurationSource(consulClient, facility);

        ArrayList<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey(facility + "/" + "key.int");
        intValue.setValue(BaseEncoding.base64().encode("10".getBytes()));
        arrayList.add(intValue);
        GetValue stringValue = new GetValue();
        stringValue.setKey(facility + "/" + "key.string");
        stringValue.setValue(BaseEncoding.base64().encode("Some String".getBytes()));
        arrayList.add(stringValue);
        GetValue boolValue = new GetValue();
        boolValue.setKey(facility + "/" + "key.bool");
        boolValue.setValue(BaseEncoding.base64().encode(Boolean.FALSE.toString().getBytes()));
        arrayList.add(boolValue);

        response = new Response<>(arrayList, 0l, false, 0l);
        when(consulClient.getKVValues(facility)).thenReturn(response);

        consulConfigurationSource.poll(true, null);
        dc = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 1000000, false));
    }

    @Test
    public void testPoll() throws Exception {
        Thread.sleep(10);
        Assert.assertEquals(10, dc.getInt("key.int"));
        Assert.assertEquals("Some String", dc.getString("key.string"));
        Assert.assertEquals(false, dc.getBoolean("key.bool"));
    }

}

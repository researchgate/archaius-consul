package net.researchgate.archaius;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.netflix.archaius.config.PollingDynamicConfig;
import com.netflix.archaius.config.polling.FixedPollingStrategy;
import org.apache.commons.codec.binary.Base64;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.TimeUnit;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ConsulClient.class)
public class ConsulConfigurationSourceTest {

    private static final String FACILITY = "testFacility";
    private static final String HOSTS = "localhost:8500";

    private ConsulConfigurationSource consulConfigurationSource;

    @Mock
    private ConsulClient consulClient;

    @Mock
    private ConsulClientFactory clientFactory;

    private Response<List<GetValue>> response;
    private PollingDynamicConfig dc;

    @Before
    public void setUp() throws Exception {
        consulConfigurationSource = new ConsulConfigurationSource(HOSTS, FACILITY, clientFactory);
        when(clientFactory.getClient(any(String.class))).thenReturn(consulClient);
        when(clientFactory.getClient(any(String.class), any(Integer.class))).thenReturn(consulClient);

        when(consulClient.getKVValues(FACILITY)).thenReturn(new Response<List<GetValue>>(new ArrayList<GetValue>(), 0L, false, 0L));
    }

    @Test
    public void testClientCreation() throws InterruptedException, Exception {
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey(FACILITY + "/" + "key.int");
        intValue.setValue(Base64.encodeBase64String("10".getBytes()));
        arrayList.add(intValue);
        response = new Response<>(arrayList, 0L, false, 0L);
        when(consulClient.getKVValues(FACILITY)).thenReturn(response);

        dc = new PollingDynamicConfig(consulConfigurationSource, new FixedPollingStrategy(1, TimeUnit.SECONDS));
        Thread.sleep(10);

        verify(clientFactory).getClient("localhost", 8500);
        Assert.assertEquals(new Integer(10), dc.getInteger("key.int"));

        ConsulClient newClient = PowerMockito.mock(ConsulClient.class);
        when(clientFactory.getClient("second", 8100)).thenReturn(newClient);
        when(consulClient.getKVValues(FACILITY)).thenThrow(new TransportException(null));
//        when(newClient.getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT)).
//                thenReturn(new Response<>((List<CatalogService>) new ArrayList<CatalogService>(), 0L, true, 0L));

        consulConfigurationSource.call();

        verify(clientFactory, atLeastOnce()).getClient("second", 8100);
//        verify(newClient, atMost(2)).getCatalogService(CONSUL_SERVICE_NAME, QueryParams.DEFAULT);
        Thread.sleep(10);

        Assert.assertEquals(new Integer(10), dc.getInteger("key.int"));
    }

    @Test
    public void testPoll() throws Exception {
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey(FACILITY + "/" + "key.int");
        intValue.setValue(Base64.encodeBase64String("10".getBytes()));
        arrayList.add(intValue);
        GetValue stringValue = new GetValue();
        stringValue.setKey(FACILITY + "/" + "key.string");
        stringValue.setValue(Base64.encodeBase64String("Some String".getBytes()));
        arrayList.add(stringValue);
        GetValue boolValue = new GetValue();
        boolValue.setKey(FACILITY + "/" + "key.bool");
        boolValue.setValue(Base64.encodeBase64String(Boolean.FALSE.toString().getBytes()));
        arrayList.add(boolValue);

        response = new Response<>(arrayList, 0L, false, 0L);
        when(consulClient.getKVValues(FACILITY)).thenReturn(response);

        consulConfigurationSource.call();
        dc = new PollingDynamicConfig(consulConfigurationSource, new FixedPollingStrategy(1, TimeUnit.SECONDS));

        Thread.sleep(10);
        Assert.assertEquals(new Integer(10), dc.getInteger("key.int"));
        Assert.assertEquals("Some String", dc.getString("key.string"));
        Assert.assertEquals(Boolean.FALSE, dc.getBoolean("key.bool"));
    }

    @Test
    public void testWithoutFacility() throws InterruptedException, Exception {
        consulConfigurationSource = new ConsulConfigurationSource(HOSTS, "/", clientFactory);
        List<GetValue> arrayList = new ArrayList<>();
        GetValue intValue = new GetValue();
        intValue.setKey("key.int");
        intValue.setValue(Base64.encodeBase64String("10".getBytes()));
        arrayList.add(intValue);
        GetValue stringValue = new GetValue();
        stringValue.setKey("user-service.lab.config");
        stringValue.setValue(Base64.encodeBase64String("Some String".getBytes()));
        arrayList.add(stringValue);
        GetValue boolValue = new GetValue();
        boolValue.setKey("key.bool");
        boolValue.setValue(Base64.encodeBase64String(Boolean.FALSE.toString().getBytes()));
        arrayList.add(boolValue);

        response = new Response<>(arrayList, 0L, false, 0L);
        when(consulClient.getKVValues("/")).thenReturn(response);

        consulConfigurationSource.call();
        dc = new PollingDynamicConfig(consulConfigurationSource, new FixedPollingStrategy(1, TimeUnit.SECONDS));

        Thread.sleep(10);
        Assert.assertEquals(new Integer(10), dc.getInteger("key.int"));
        Assert.assertEquals("Some String", dc.getString("user-service.lab.config"));
        Assert.assertEquals(Boolean.FALSE, dc.getBoolean("key.bool"));
    }

    @Test
    public void testWithFacilityWithDots() throws InterruptedException {
        final String FACILITY_DOTS = "/user-service.lab.config/";
        int length = FACILITY_DOTS.length();
        length = FACILITY_DOTS.length() + (FACILITY_DOTS.charAt(0) == '/' ? 0 : 1) - (FACILITY_DOTS.endsWith("/") ? 1 : 0);
        String sub = ("user-service.lab.config/" + "something").substring(length);
        System.out.println(sub);
    }

}

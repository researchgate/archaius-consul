# Consul configuration source for archaius

Adds a consul configuration source to archaius that uses the key value store.

## Basic usage
```java
String hosts = "localhost:8500,localhost:8100";
String rootPath = "/myServiceConf";

ConsulConfigurationSource consulConfigurationSource = new ConsulConfigurationSource(hosts, rootPath);
DynamicConfiguration dynamicConfiguration = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 5000, false));
ConfigurationManager.install(dynamicConfiguration);
```

Now for example hystrix is configurable with the consul key value store.

## Consul is not available
If that happens the configuration will stay as it is and the configuration source will try to connect to another consul. It fetches all service nodes that are available under "consul" in the same interval as it fetches the key values.

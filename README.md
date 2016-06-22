# Consul configuration source for archaius

Adds a consul configuration source to archaius that uses the key value store.

## Basic usage
```java
String rootPath = "/myServiceConf";

ConsulConfigurationSource consulConfigurationSource = new ConsulConfigurationSource(rootPath);
DynamicConfiguration dynamicConfiguration = new DynamicConfiguration(consulConfigurationSource, new FixedDelayPollingScheduler(-1, 5000, false));
ConfigurationManager.install(dynamicConfiguration);
```

Now for example hystrix is configurable with the consul key value store. By default it connects to the default of the consul client you can find in https://github.com/Ecwid/consul-api. (Usually it is localhost:8500)
As an alternative you can set the agent host:port on the constructor and also lists as "host:port,host2:port".

## Consul is not available
If that happens the configuration will stay as it is and the configuration source will try to connect on the next interval again.
But if consul is not available on startup the configuration source doesn't set any configuration and tries to connect the next time.

# Consul configuration source for archaius

Adds a consul configuration source to archaius that uses the key value store.

[ ![Download](https://api.bintray.com/packages/researchgate/maven/archaius-consul/images/download.svg) ](https://bintray.com/researchgate/maven/archaius-consul/_latestVersion)
[![Build Status](https://travis-ci.org/researchgate/archaius-consul.svg?branch=master)](https://travis-ci.org/researchgate/archaius-consul)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

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

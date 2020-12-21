/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.backend.docker;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container.PortMapping;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;

import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.service.ExistingContainerInfo;

public class DockerEngineBackend extends AbstractDockerBackend {

	@Override
	protected Container startContainer(ContainerSpec spec, Proxy proxy) throws Exception {
		Builder hostConfigBuilder = HostConfig.builder();
		
		Map<String, List<PortBinding>> portBindings = new HashMap<>();
		if (isUseInternalNetwork()) {
			// In internal networking mode, we can access container ports directly, no need to bind on host.
		} else {
			// Allocate ports on the docker host to proxy to.
			for (Integer containerPort: spec.getPortMapping().values()) {
				int hostPort = portAllocator.allocate(proxy.getId());
				portBindings.put(String.valueOf(containerPort), Collections.singletonList(PortBinding.of("0.0.0.0", hostPort)));
			}
		}
		hostConfigBuilder.portBindings(portBindings);
		
		hostConfigBuilder.memoryReservation(memoryToBytes(spec.getMemoryRequest()));
		hostConfigBuilder.memory(memoryToBytes(spec.getMemoryLimit()));
		if (spec.getCpuLimit() != null) {
			// Workaround, see https://github.com/spotify/docker-client/issues/959
			long period = 100000;
			long quota = (long) (period * Float.parseFloat(spec.getCpuLimit()));
			hostConfigBuilder.cpuPeriod(period);
			hostConfigBuilder.cpuQuota(quota);
		}
		
		Optional.ofNullable(spec.getNetwork()).ifPresent(n -> hostConfigBuilder.networkMode(spec.getNetwork()));
		Optional.ofNullable(spec.getDns()).ifPresent(dns -> hostConfigBuilder.dns(dns));
		Optional.ofNullable(spec.getVolumes()).ifPresent(v -> hostConfigBuilder.binds(v));
		hostConfigBuilder.privileged(isPrivileged() || spec.isPrivileged());

		Map<String, String> labels = spec.getLabels();

		for (RuntimeValue runtimeValue : proxy.getRuntimeValues().values()) {
			if (runtimeValue.getKey().getIncludeAsLabel() || runtimeValue.getKey().getIncludeAsAnnotation()) {
				labels.put(runtimeValue.getKey().getKeyAsLabel(), runtimeValue.getValue());
			}
		}

		ContainerConfig containerConfig = ContainerConfig.builder()
			    .hostConfig(hostConfigBuilder.build())
			    .image(spec.getImage())
			    .labels(labels)
			    .exposedPorts(portBindings.keySet())
			    .cmd(spec.getCmd())
			    .env(convertEnv(buildEnv(spec, proxy)))
			    .build();
		ContainerCreation containerCreation = dockerClient.createContainer(containerConfig);
		
		if (spec.getNetworkConnections() != null) {
			for (String networkConnection: spec.getNetworkConnections()) {
				dockerClient.connectToNetwork(containerCreation.id(), networkConnection);
			}
		}
		
		dockerClient.startContainer(containerCreation.id());
		
		Container container = new Container();
		container.setSpec(spec);
		container.setId(containerCreation.id());
		
		// Calculate proxy routes for all configured ports.
		for (String mappingKey: spec.getPortMapping().keySet()) {
			int containerPort = spec.getPortMapping().get(mappingKey);
			
			List<PortBinding> binding = portBindings.get(String.valueOf(containerPort));
			int hostPort = Integer.valueOf(Optional.ofNullable(binding).map(pb -> pb.get(0).hostPort()).orElse("0"));

			String mapping = mappingStrategy.createMapping(mappingKey, container, proxy);
			URI target = calculateTarget(container, containerPort, hostPort);
			proxy.getTargets().put(mapping, target);
		}
		
		return container;
	}
	
	protected URI calculateTarget(Container container, int containerPort, int hostPort) throws Exception {
		String targetProtocol;
		String targetHostName;
		String targetPort;
		String targetPath = computeTargetPath(container.getSpec().getTargetPath());

		if (isUseInternalNetwork()) {
			targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, DEFAULT_TARGET_PROTOCOL);
			
			// For internal networks, DNS resolution by name only works with custom names.
			// See comments on https://github.com/docker/for-win/issues/1009
			ContainerInfo info = dockerClient.inspectContainer(container.getId());
			targetHostName = info.config().hostname();

			targetPort = String.valueOf(containerPort);
		} else {
			URL hostURL = new URL(getProperty(PROPERTY_URL, DEFAULT_TARGET_URL));
			targetProtocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, hostURL.getProtocol());
			targetHostName = hostURL.getHost();
			targetPort = String.valueOf(hostPort);
		}
		
		return new URI(String.format("%s://%s:%s%s", targetProtocol, targetHostName, targetPort, targetPath));
	}
	
	@Override
	protected void doStopProxy(Proxy proxy) throws Exception {
		for (Container container: proxy.getContainers()) {
			String[] networkConnections = container.getSpec().getNetworkConnections();
			if (networkConnections != null) {
				for (String conn: networkConnections) {
					dockerClient.disconnectFromNetwork(container.getId(), conn);
				}
			}
			dockerClient.removeContainer(container.getId(), RemoveContainerParam.forceKill());
		}
		portAllocator.release(proxy.getId());
	}

	@Override
	public List<ExistingContainerInfo> scanExistingContainers() throws Exception {
		ArrayList<ExistingContainerInfo> containers = new ArrayList<ExistingContainerInfo>();
		
		for (com.spotify.docker.client.messages.Container container: dockerClient.listContainers(ListContainersParam.allContainers())) {
			ImmutableMap<String, String> labels = container.labels();

			String proxyId = labels.get(RUNTIME_LABEL_PROXY_ID);
			if (proxyId == null) {
				continue; // this isn't a container created by us
			}
			
			String specId = labels.get(RUNTIME_LABEL_PROXY_SPEC_ID);
			if (specId == null) {
				continue; // this isn't a container created by us
			}
			
			String userId = labels.get(RUNTIME_LABEL_USER_ID);
			if (userId == null) {
				continue; // this isn't a container created by us
			}
			
			String startupTimestmap = labels.get(RUNTIME_LABEL_STARTUP_TIMESTAMP);
			if (startupTimestmap == null) {
				continue; // this isn't a container created by us
			}
			
			Map<Integer, Integer> portBindings = new HashMap<>();
			for (PortMapping portMapping: container.ports()) {
				int hostPort = portMapping.publicPort();
				int containerPort = portMapping.privatePort();
				portBindings.put(containerPort, hostPort);
			}	
			
			boolean running = container.state().toLowerCase().equals("running");
			
			containers.add(new ExistingContainerInfo(container.id(),
					proxyId, specId, container.image(), userId, portBindings,
					Long.parseLong(startupTimestmap),
					running,
					new HashMap()
					));
		}
		
		return containers;
	}
	
	public void setupPortMappingExistingProxy(Proxy proxy, Container container, Map<Integer, Integer> portBindings) throws Exception {
		for (Map.Entry<Integer, Integer> portBinding : portBindings.entrySet()) {
			// Calculate proxy routes for specified ports
			Optional<Entry<String, Integer>> specifiedMapping = container.getSpec().getPortMapping().entrySet().stream().filter(m -> m.getValue().equals(portBinding.getKey())).findFirst();
			if (specifiedMapping.isPresent()) {
				portAllocator.addExistingPort(proxy.getUserId(), portBinding.getValue());
				String mapping = mappingStrategy.createMapping(specifiedMapping.get().getKey(), container, proxy);
				URI target = calculateTarget(container, portBinding.getKey(), portBinding.getValue());
				proxy.getTargets().put(mapping, target);
			}
		}
	}
	
}

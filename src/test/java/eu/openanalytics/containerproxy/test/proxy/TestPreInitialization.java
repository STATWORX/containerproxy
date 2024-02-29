/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy.test.proxy;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.IDelegateProxyStore;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.SeatIdKey;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.DelegateProxy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.TargetIdKey;
import eu.openanalytics.containerproxy.test.helpers.ContainerSetup;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.containerproxy.test.helpers.TestProxySharingScaler;
import eu.openanalytics.containerproxy.util.Retrying;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class TestPreInitialization {

    private static Stream<Arguments> backends() {
        return Stream.of(
            Arguments.of("docker", Map.of("proxy.container-backend", "docker")),
            Arguments.of("docker-swarm", Map.of("proxy.container-backend", "docker-swarm")),
            Arguments.of("kubernetes", Map.of("proxy.container-backend", "kubernetes"))
        );
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void simpleTest(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization.yml", properties, true)) {
                ProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_simpleTest", ProxySharingScaler.class);
                inst.enableCleanup();
                String id = inst.client.startProxy("simpleTest");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // target id should be different from proxy id
                Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                Assertions.assertEquals("/api/route/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));
                Assertions.assertNotNull(proxy.getRuntimeValue(SeatIdKey.inst));

                waitUntilNoPendingSeats(proxySharingScaler);
                Assertions.assertEquals(1, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                // start an additional app
                Instant start = Instant.now();
                String id2 = inst.client.startProxy("simpleTest");
                Proxy proxy2 = inst.proxyService.getProxy(id2);
                inst.client.testProxyReachable(proxy2.getTargetId());

                // target id should be different from first app
                Assertions.assertNotEquals(proxy2.getTargetId(), proxy.getTargetId());
                // seat id should be different from first app
                Assertions.assertNotEquals(proxy2.getRuntimeValue(SeatIdKey.inst), proxy.getRuntimeValue(SeatIdKey.inst));

                // should have scaled-up
                waitUntilNoPendingSeats(proxySharingScaler);
                Assertions.assertEquals(2, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());

                // stop first app
                inst.client.stopProxy(id);
                Assertions.assertEquals(0, proxySharingScaler.getNumPendingSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(2, proxySharingScaler.getNumUnclaimedSeats());

                // wait until scale-down happened
                waitUntilUnNumberOfUnClaimedSeats(proxySharingScaler, 1);
                Instant stop = Instant.now();
                Assertions.assertEquals(0, proxySharingScaler.getNumPendingSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumClaimedSeats());
                Assertions.assertEquals(1, proxySharingScaler.getNumUnclaimedSeats());
                // scale-down should take at least two minutes
                Assertions.assertTrue(Duration.between(start, stop).toSeconds() > 120);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testSetPublicPathPrefix(String backend, Map<String, String> properties) {
        ProxySharingScaler.setPublicPathPrefix("/my/custom/path/");
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization.yml", properties, true)) {
                inst.enableCleanup();
                String id = inst.client.startProxy("simpleTest");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // target id should be different from proxy id
                Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                Assertions.assertEquals("/my/custom/path/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));
                Assertions.assertNotNull(proxy.getRuntimeValue(SeatIdKey.inst));

                inst.client.stopProxy(id);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    public void testNoContainerReUse(String backend, Map<String, String> properties) {
        try (ContainerSetup containerSetup = new ContainerSetup(backend)) {
            try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-pre-initialization.yml", properties, true)) {
                inst.enableCleanup();
                TestProxySharingScaler proxySharingScaler = inst.getBean("proxySharingScaler_noReUse", TestProxySharingScaler.class);
                proxySharingScaler.disableCleanup();
                IDelegateProxyStore delegateProxyStore = proxySharingScaler.getDelegateProxyStore();

                String id = inst.client.startProxy("noReUse");
                Proxy proxy = inst.proxyService.getProxy(id);
                inst.client.testProxyReachable(proxy.getTargetId());

                // target id should be different from proxy id
                Assertions.assertNotEquals(proxy.getTargetId(), proxy.getId());
                Assertions.assertEquals("/api/route/" + proxy.getTargetId() + "/", proxy.getRuntimeValue(PublicPathKey.inst));
                Assertions.assertEquals(proxy.getTargetId(), proxy.getRuntimeValue(TargetIdKey.inst));
                Assertions.assertNotNull(proxy.getRuntimeValue(SeatIdKey.inst));

                // wait for scale up to finish
                waitUntilUnNumberOfUnClaimedSeats(proxySharingScaler, 1);

                List<String> delegateProxyIds = new ArrayList<>(delegateProxyStore.getAllDelegateProxies().stream().map(it -> it.getProxy().getId()).toList());
                Assertions.assertEquals(2, delegateProxyIds.size());
                Assertions.assertTrue(delegateProxyIds.remove(proxy.getTargetId()));
                String existingDelegateProxyId = delegateProxyIds.get(0);

                inst.client.stopProxy(id);

                // proxied stop, DelegateProxy should still be there, but without seats
                Assertions.assertEquals(2, delegateProxyStore.getAllDelegateProxies().size());
                Assertions.assertNotNull(delegateProxyStore.getDelegateProxy(existingDelegateProxyId));
                DelegateProxy delegateProxy = delegateProxyStore.getDelegateProxy(proxy.getTargetId());
                Assertions.assertTrue(delegateProxy.getSeatIds().isEmpty());

                proxySharingScaler.enableCleanup();
                // old proxy should get cleaned up
                waitUntilNumberOfDelegateProxies(proxySharingScaler, 1);
            }
        }
    }


    // TODO test re-use with more than one seats
    // TODO test crashed
    // TODO test scaleDownDelay
    // TODO test minimumSeatsAvailable
    // TODO test config change

    private void waitUntilNoPendingSeats(ProxySharingScaler proxySharingScaler) {
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return proxySharingScaler.getNumPendingSeats() == 0;
        }, 60_000, "assert no pending seats", 1, true);
        Assertions.assertTrue(noPendingSeats);
    }

    private void waitUntilUnNumberOfUnClaimedSeats(ProxySharingScaler proxySharingScaler, int numSeats) {
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return proxySharingScaler.getNumUnclaimedSeats() == numSeats;
        }, 180_000, "assert number of unclaimed seats", 1, true);
        Assertions.assertTrue(noPendingSeats);
    }

    private void waitUntilNumberOfDelegateProxies(TestProxySharingScaler proxySharingScaler, int numSeats) {
        boolean noPendingSeats = Retrying.retry((c, m) -> {
            return proxySharingScaler.getDelegateProxyStore().getAllDelegateProxies().size() == numSeats;
        }, 60_000, "assert number delegated proxies", 1, true);
        Assertions.assertTrue(noPendingSeats);
    }

}

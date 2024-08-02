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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.MaxLifetimeKey;
import eu.openanalytics.containerproxy.service.leader.GlobalEventLoopService;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import eu.openanalytics.containerproxy.security.UserEncrypt;
import org.springframework.core.env.Environment;

/**
 * This service releases proxies which reached their max-lifetime.
 */
@Service
public class ProxyMaxLifetimeService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final StructuredLogger slog = new StructuredLogger(log);
    private String secretString;

    @Inject
    private ProxyService proxyService;

    @Inject
    private Environment environment;

    @Inject
    private IProxyReleaseStrategy releaseStrategy;

    @Inject
    private GlobalEventLoopService globalEventLoop;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void scheduleCleanup() {
        globalEventLoop.schedule(this::performCleanup);
    }

    private void performCleanup() {
        secretString = environment.getProperty("proxy.user-encrypt-key");
        for (Proxy proxy : proxyService.getAllProxies()) {
            if (mustBeReleased(proxy)) {
                String uptime = DurationFormatUtils.formatDurationWords(
                    System.currentTimeMillis() - proxy.getStartupTimestamp(),
                    true, false);
                log.info(String.format(
                    "Forcefully releasing proxy because it reached the max lifetime [user: %s] [spec: %s] [id: %s] [uptime: %s]", 
                    UserEncrypt.obfuscateUser(proxy.getUserId(),secretString), proxy.specId, proxy.getId(), uptime)
                    );
                slog.info(proxy, String.format("Forcefully releasing proxy because it reached the max lifetime [uptime: %s]", uptime));
                releaseStrategy.releaseProxy(proxy);
            }
        }

    }

    private Boolean mustBeReleased(Proxy proxy) {
        if (proxy.getStatus() != ProxyStatus.Up) {
            return false;
        }

        Long maxLifeTime = proxy.getRuntimeObject(MaxLifetimeKey.inst);

        if (maxLifeTime > 0) {
            Instant notBeforeTime = Instant.now().minus(maxLifeTime, ChronoUnit.MINUTES);

            return Instant.ofEpochMilli(proxy.getStartupTimestamp()).isBefore(notBeforeTime);
        }

        return false;
    }

}

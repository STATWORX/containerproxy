/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
package eu.openanalytics.containerproxy.stat.impl;

import eu.openanalytics.containerproxy.event.*;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.stat.IStatCollector;

import javax.inject.Inject;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import static eu.openanalytics.containerproxy.event.BridgeableEvent.SOURCE_NOT_AVAILABLE;
public abstract class AbstractDbCollector implements IStatCollector {
    @Inject
    protected Environment environment;
    @Value("${proxy.usage-stats-disable-admin:false}")
    protected boolean disableAdminLogging;
    @Inject
    protected UserService userService;

    protected boolean disableAdminCollector() {
        return !this.disableAdminLogging || !this.userService.isAdmin();
    }

    @EventListener
    public void onUserLogoutEvent(UserLogoutEvent event) throws IOException {
        if (this.disableAdminCollector()) {
        writeToDb(event.getTimestamp(), event.getUserId(), "Logout", null);
        }
    }

    @EventListener
    public void onUserLoginEvent(UserLoginEvent event) throws IOException {
        if (this.disableAdminCollector()) {
        writeToDb(event.getTimestamp(), event.getUserId(), "Login", null);
        }
    }

    @EventListener
    public void onProxyStartEvent(ProxyStartEvent event) throws IOException {
        if (this.disableAdminCollector()) {
            if (event.getSource().equals(SOURCE_NOT_AVAILABLE)) {
                writeToDb(event.getTimestamp(), event.getUserId(), "ProxyStart", event.getSpecId());
            }
        }    
    }

    @EventListener
    public void onProxyStopEvent(ProxyStopEvent event) throws IOException {
        if (this.disableAdminCollector()) {
            if (event.getSource().equals(SOURCE_NOT_AVAILABLE)) {
                writeToDb(event.getTimestamp(), event.getUserId(), "ProxyStop", event.getSpecId());
            }
        }
    }

    @EventListener
    public void onProxyStartFailedEvent(ProxyStartFailedEvent event) throws IOException {
        // TODO
    }

    @EventListener
    public void onAuthFailedEvent(AuthFailedEvent event) throws IOException {
        // TODO
    }

    @EventListener
    public void onPbiClickEvent(PbiClickEvent event) throws IOException {
       if (this.disableAdminCollector()) {
          this.writeToDb(event.getTimestamp(), event.getUserId(), "PbiClick", event.getDashboardId());
       }
 
    }

    protected abstract void writeToDb(long timestamp, String userId, String type, String data) throws IOException;

}

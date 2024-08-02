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
package eu.openanalytics.containerproxy.test.helpers;
import eu.openanalytics.containerproxy.ContainerProxyApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import javax.annotation.Nonnull;

public class PropertyOverrideContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@Nonnull ConfigurableApplicationContext configurableApplicationContext) {
        MutablePropertySources propertySources = configurableApplicationContext.getEnvironment().getPropertySources();
        PropertiesPropertySource defaultProperties = new PropertiesPropertySource("shinyProxyDefaultProperties", ContainerProxyApplication.getDefaultProperties());
        propertySources.addFirst(defaultProperties);

        // remove any external, file-based property source
        // we don't want any application.yml or application.properties to be loaded during the tests
        propertySources
            .stream()
            .map(PropertySource::getName)
            .filter(p -> p.contains("Config resource 'file ") && p.contains("via location 'optional:file:./'"))
            .toList()
            .forEach(propertySources::remove);
    }
}

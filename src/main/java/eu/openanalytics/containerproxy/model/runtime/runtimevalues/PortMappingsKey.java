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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.model.runtime.PortMappings;

public class PortMappingsKey extends RuntimeValueKey<PortMappings> {

    public static final PortMappingsKey inst = new PortMappingsKey();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PortMappingsKey() {
        super("openanalytics.eu/sp-port-mappings",
            "SHINYPROXY_PORT_MAPPINGS",
            false,
            true,
            false,
            false, // important: may not be exposed in API for security
            true,
            true,
            PortMappings.class);
    }

    @Override
    public PortMappings deserializeFromString(String value) {
        try {
            return objectMapper.readValue(value, PortMappings.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String serializeToString(PortMappings value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

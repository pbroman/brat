package dev.pbroman.brat.core.api.handler;

import dev.pbroman.brat.core.api.RequestDefinition;

public interface RequestHandler<T extends RequestDefinition> {

    Object performRequest(T requestDefinition);

}

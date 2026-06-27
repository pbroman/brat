package dev.pbroman.brat.core.api.handler;

import dev.pbroman.brat.core.data.RequestDefinition;

public interface RequestHandler {

    Object performRequest(RequestDefinition requestDefinition);

}

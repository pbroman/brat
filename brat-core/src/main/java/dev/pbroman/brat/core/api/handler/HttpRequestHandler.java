package dev.pbroman.brat.core.api.handler;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;

public interface HttpRequestHandler extends RequestHandler<HttpRequestDefinition> {

    @Override
    HttpResponse performRequest(HttpRequestDefinition requestDefinition);

}

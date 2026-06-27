package dev.pbroman.brat.core.api.handler;

import org.springframework.http.ResponseEntity;

import dev.pbroman.brat.core.data.RequestDefinition;

public interface HttpRequestHandler extends RequestHandler {

    @Override
    ResponseEntity<String> performRequest(RequestDefinition requestDefinition);

}

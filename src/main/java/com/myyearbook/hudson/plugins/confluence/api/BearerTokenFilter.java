package com.myyearbook.hudson.plugins.confluence.api;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

import javax.ws.rs.core.HttpHeaders;

public final class BearerTokenFilter extends ClientFilter {

    private final String token;

    public BearerTokenFilter(String token) {
        this.token = token;
    }

    @Override
    public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
        if (!cr.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            cr.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return getNext().handle(cr);
    }
}

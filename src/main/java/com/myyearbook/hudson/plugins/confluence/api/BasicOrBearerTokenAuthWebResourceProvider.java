package com.myyearbook.hudson.plugins.confluence.api;

import com.atlassian.confluence.rest.client.authentication.AuthenticatedWebResourceProvider;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class BasicOrBearerTokenAuthWebResourceProvider extends AuthenticatedWebResourceProvider {
    private static final Logger log = LoggerFactory.getLogger(BasicOrBearerTokenAuthWebResourceProvider.class);

    private char[] accessToken;

    public BasicOrBearerTokenAuthWebResourceProvider(Client client, String baseUrl, String path) {
        super(client, baseUrl, path);
    }

    @Override
    public WebResource newRestWebResource() {
        // create resource with a basic authentication filter if username and password are provided
        WebResource resource = super.newRestWebResource();
        if (this.accessToken != null) {
            resource.addFilter(new BearerTokenFilter(new String(this.accessToken)));
            log.debug("Using web resource with token authentication");
        } else {
            log.debug("Leaving original web resource unchanged");
        }

        return resource;
    }

    @Override
    public void setAuthContext(String username, char[] password) {
        super.setAuthContext(username, password);

        if (this.accessToken != null) {
            // override old access token
            Arrays.fill(this.accessToken, '\u0000');
        }

        if (username != null && username.length() > 0) {
            // username not null means, basic authentication is requested
            accessToken = null;
            return;
        }

        this.accessToken = ArrayUtils.clone(password);
    }
}


package com.myyearbook.hudson.plugins.confluence.credentials;


import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Util;
import hudson.util.Secret;

@NameWith(ConfluenceApiToken.NameProvider.class)
public interface ConfluenceApiToken extends StandardCredentials {

    Secret getApiToken();

    class NameProvider extends CredentialsNameProvider<ConfluenceApiToken> {
        @Override
        public String getName(ConfluenceApiToken c) {
            String description = Util.fixEmptyAndTrim(c.getDescription());
            return "Confluence API token" + (description != null ? " (" + description + ")" : "");
        }
    }
}

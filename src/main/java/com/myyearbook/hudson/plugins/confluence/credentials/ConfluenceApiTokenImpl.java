package com.myyearbook.hudson.plugins.confluence.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public final class ConfluenceApiTokenImpl extends BaseStandardCredentials implements ConfluenceApiToken {

    private Secret apiToken;

    @DataBoundConstructor
    public ConfluenceApiTokenImpl(CredentialsScope scope, String id, String description, Secret apiToken) {
        super(scope, id, description);
        this.apiToken = apiToken;
    }

    @Override
    public Secret getApiToken() {
        return apiToken;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Override
        public String getDisplayName() {
            return "Confluence API token";
        }
    }
}

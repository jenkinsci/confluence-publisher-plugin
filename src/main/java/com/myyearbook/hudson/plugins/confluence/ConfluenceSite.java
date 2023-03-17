/*
 * Copyright 2011-2012 MeetMe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.myyearbook.hudson.plugins.confluence;

import com.atlassian.confluence.rest.client.RestClientFactory;
import com.atlassian.confluence.rest.client.authentication.AuthenticatedWebResourceProvider;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

/**
 * Represents an external Confluence installation and configuration needed to access it.
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class ConfluenceSite implements Describable<ConfluenceSite> {
    /**
     * The base URL of the Confluence site
     */
    public final URL url;

    private String credentialsId;
    private transient String username;
    private transient Secret password;

    /**
     * Stapler constructor
     *
     * @param url
     * @param credentialsId
     */
    @DataBoundConstructor
    public ConfluenceSite(URL url, final String credentialsId) {
        LOGGER.log(Level.FINER, "ctor args: " + url + ", " + credentialsId);

        if (!url.toExternalForm().endsWith("/")) {
            try {
                url = new URL(url.toExternalForm() + "/");
            } catch (MalformedURLException e) {
                throw new AssertionError(e); // impossible
            }
        }

        this.url = url;
        this.credentialsId = hudson.Util.fixEmptyAndTrim(credentialsId);
    }

    /**
     * Creates a remote access session to this Confluence site
     *
     * @return {@link ConfluenceSession}
     */
    public ConfluenceSession createSession() {
        final String restUrl = url.toExternalForm();
        LOGGER.log(Level.FINEST, "[confluence] Using Confluence base url: " + restUrl);

        AuthenticatedWebResourceProvider authenticatedWebResourceProvider = new AuthenticatedWebResourceProvider(
                RestClientFactory.newClient(),
                restUrl,
                "");
        if (credentialsId != null) {
            StandardCredentials credentials = CredentialsMatchers.firstOrNull(
                    lookupCredentials(
                            StandardCredentials.class,
                            Jenkins.get(),
                            ACL.SYSTEM,
                            URIRequirementBuilder.create().build()),
                    CredentialsMatchers.withId(credentialsId));
            if (credentials != null) {
                if (credentials instanceof UsernamePasswordCredentials) {
                    UsernamePasswordCredentials userPwCreds = (UsernamePasswordCredentials)credentials;

                    authenticatedWebResourceProvider.setAuthContext(
                            userPwCreds.getUsername(),
                            userPwCreds.getPassword().getPlainText().toCharArray());
                }
                else {
                    throw new IllegalStateException("No credentials found for credentialsId: " + credentialsId);
                }
            }
        }

        return new ConfluenceSession(authenticatedWebResourceProvider);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
    }

    public String getName() {
        return this.url.getHost();
    }

    public String getCredentialsId() throws IOException {
        if(StringUtils.isBlank(credentialsId) && StringUtils.isNotBlank(username) && password != null) {
            migrateCredentials();
        }
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    @Deprecated
    public String getUsername(){
        return username;
    }

    @DataBoundSetter
    public void setUsername(String username){
        this.username = Util.fixEmptyAndTrim(username);
    }

    @Deprecated
    public Secret getPassword(){
        return password;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
    }

    public void setPassword(String password) {
        this.password = Secret.fromString(password);
    }

    @Override
    public String toString() {
        return "Confluence{" + getName() + "}";
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<ConfluenceSite> {
        public DescriptorImpl() {
            super(ConfluenceSite.class);
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId) {

            final StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }

            return result
                    .includeEmptyValue()
                    .includeMatchingAs(
                            item instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task) item) : ACL.SYSTEM,
                            item,
                            StandardCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class))
                    .includeCurrentValue(credentialsId);
        }

        /**
         * Checks if the user name and password are valid.
         */
        @RequirePOST
        public FormValidation doLoginCheck(@QueryParameter String url,
                @QueryParameter String credentialsId)
                throws IOException {

            url = hudson.Util.fixEmpty(url);

            if (url == null) {// URL not entered yet
                return FormValidation.ok();
            }

            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null || !jenkins.hasPermission(Jenkins.ADMINISTER))
                return FormValidation.ok();

            final ConfluenceSite site = new ConfluenceSite(new URL(url), credentialsId);

            try {
                site.createSession().getCurrentUser();
                return FormValidation.ok("SUCCESS");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to login to Confluence at " + url, e);
                return FormValidation.error(e, "Failed to login");
            }
        }

        /**
         * Checks if the Confluence URL is accessible.
         */
        public FormValidation doUrlCheck(@QueryParameter final String url) throws IOException,
                ServletException {
            // this can be used to check existence of any file in any URL, so
            // admin only
            if (!Jenkins.get().hasPermission(Hudson.ADMINISTER)) {
                return FormValidation.ok();
            }

            final String newurl = hudson.Util.fixEmpty(url);

            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {

                    if (newurl == null) {
                        return FormValidation.error("Enter a URL");
                    }

                    try {
                        if (findText(open(new URL(newurl)), "Atlassian Confluence")) {
                            return FormValidation.ok();
                        }

                        return FormValidation.error("Not a Confluence URL");
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Unable to connect to " + url, e);
                        return handleIOException(url, e);
                    }
                }
            }.check();
        }

        public FormValidation doCheckCredentialsId(
                @AncestorInPath Item item,
                @QueryParameter String value) {

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            }
            else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }

            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }

            ListBoxModel creds = CredentialsProvider.listCredentials(
                    StandardCredentials.class,
                    item,
                    item instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task) item) : ACL.SYSTEM,
                    URIRequirementBuilder.create().build(),
                    CredentialsMatchers.withId(value));
            if (creds.isEmpty()) {
                return FormValidation.error("Cannot find currently selected credentials");
            }

            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Confluence Site";
        }
    }

    //For backwards compatibility. ReadResolve is called on startup
    private Object readResolve() throws IOException {
        if (StringUtils.isBlank(credentialsId) && StringUtils.isNotBlank(username) && password != null)
            migrateCredentials();

        return this;
    }

    private void migrateCredentials() throws IOException {
         final List<StandardUsernamePasswordCredentials> credentials =
                CredentialsMatchers.filter(
                        lookupCredentials(
                                StandardUsernamePasswordCredentials.class,
                                Jenkins.get(),
                                ACL.SYSTEM,
                                URIRequirementBuilder.create().build()),
                        CredentialsMatchers.withUsername(username));

        for (final StandardUsernamePasswordCredentials cred : credentials) {
            if (StringUtils.equals(password.getPlainText(), Secret.toString(cred.getPassword()))) {
                // If some credentials have the same username/password, use those.
                credentialsId = cred.getId();
                break;
            }
        }

        if (StringUtils.isBlank(credentialsId)) {
            // If we couldn't find any existing credentials,
            // create new credentials with the principal and secret and use it.
            for (CredentialsStore credentialsStore : CredentialsProvider.lookupStores(Jenkins.get())) {
                if (credentialsStore instanceof SystemCredentialsProvider.StoreImpl) {
                    String newCredentialsId = UUID.randomUUID().toString();

                    credentialsStore.addCredentials(
                        credentialsStore.getDomains().get(0),
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM,
                                newCredentialsId,
                                "Migrated from confluence-publisher username/password",
                                username,
                                password.getPlainText()));

                    credentialsId = newCredentialsId;
                    break;
                }
            }
        }

        username = null;
        password = null;
    }

    private static final Logger LOGGER = Logger.getLogger(ConfluenceSite.class.getName());
}

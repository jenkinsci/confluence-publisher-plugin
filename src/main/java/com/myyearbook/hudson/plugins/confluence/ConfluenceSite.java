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

import com.myyearbook.hudson.plugins.confluence.rpc.XmlRpcClient;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;
import hudson.util.Secret;

import org.apache.axis.AxisFault;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import jenkins.model.Jenkins;

import jenkins.plugins.confluence.soap.v1.ConfluenceSoapService;
import jenkins.plugins.confluence.soap.v1.RemoteServerInfo;

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

    /**
     * The username to login as
     */
    public final String username;

    /**
     * The password for that user
     */
    public final Secret password;

    /**
     * Stapler constructor
     *
     * @param url
     * @param username
     * @param password
     */
    @DataBoundConstructor
    public ConfluenceSite(URL url, final String username, final String password) {
        LOGGER.log(Level.FINER, "ctor args: " + url + ", " + username + ", " + password);

        if (!url.toExternalForm().endsWith("/")) {
            try {
                url = new URL(url.toExternalForm() + "/");
            } catch (MalformedURLException e) {
                throw new AssertionError(e); // impossible
            }
        }

        this.url = url;
        this.username = hudson.Util.fixEmptyAndTrim(username);
        this.password = Secret.fromString(password);
    }

    /**
     * Creates a remote access session to this Confluence site
     *
     * @return {@link ConfluenceSession}
     * @throws RemoteException
     */
    public ConfluenceSession createSession() throws RemoteException {
        final String rpcUrl = Util.confluenceUrlToSoapUrl(url.toExternalForm());
        LOGGER.log(Level.FINEST, "[confluence] Using RPC url: " + rpcUrl);

        final ConfluenceSoapService service = XmlRpcClient.getInstance(rpcUrl);
        final String token;

        if (username != null && password != null) {
            token = service.login(username, Secret.toString(password));
        } else {
            // Empty string token means anonymous access
            token = "";
        }

        RemoteServerInfo info = service.getServerInfo(token);

        jenkins.plugins.confluence.soap.v2.ConfluenceSoapService serviceV2 = null;

        if (info.getMajorVersion() >= 4) {
            String v2Url = Util.confluenceUrlToSoapV2Url(url.toExternalForm());
            serviceV2 = XmlRpcClient.getV2Instance(v2Url);
        }

        return new ConfluenceSession(service, serviceV2, token, info);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public String getName() {
        return this.url.getHost();
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

        /**
         * Checks if the user name and password are valid.
         */
        public FormValidation doLoginCheck(@QueryParameter String url,
                @QueryParameter String username, @QueryParameter String password)
                throws IOException {

            url = hudson.Util.fixEmpty(url);

            if (url == null) {// URL not entered yet
                return FormValidation.ok();
            }

            username = hudson.Util.fixEmpty(username);
            password = hudson.Util.fixEmpty(password);

            if (username == null || password == null) {
                return FormValidation.warning("Enter username and password");
            }

            final ConfluenceSite site = new ConfluenceSite(new URL(url), username, password);

            try {
                site.createSession();
                return FormValidation.ok("SUCCESS");
            } catch (AxisFault e) {
                LOGGER.log(Level.WARNING, "Failed to login to Confluence at " + url, e);
                return FormValidation.error(e, "Failed to login");
            } catch (RemoteException e) {
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
            if (!Jenkins.getInstance().hasPermission(Hudson.ADMINISTER)) {
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

        @Override
        public String getDisplayName() {
            return "Confluence Site";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ConfluenceSite.class.getName());
}

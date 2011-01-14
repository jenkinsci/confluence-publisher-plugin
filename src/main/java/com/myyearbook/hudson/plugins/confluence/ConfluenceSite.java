package com.myyearbook.hudson.plugins.confluence;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.axis.AxisFault;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.myyearbook.hudson.plugins.confluence.rpc.XmlRpcClient;

/**
 * Represents an external Confluence installation and configuration needed to
 * access it.
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public class ConfluenceSite implements Describable<ConfluenceSite> {
    /**
     * The base URL of the Confluence site
     */
    public final URL url;

    /**
     * The username to login as
     */
    public final String password;

    /**
     * The password for that user
     */
    public final String username;

    /**
     * Stapler constructor
     * 
     * @param url
     * @param username
     * @param password
     */
    @DataBoundConstructor
    public ConfluenceSite(URL url, final String username, final String password) {
	LOGGER.log(Level.INFO, "ctor args: " + url + ", " + username + ", " + password);
	if (!url.toExternalForm().endsWith("/")) {
	    try {
		url = new URL(url.toExternalForm() + "/");
	    } catch (MalformedURLException e) {
		throw new AssertionError(e); // impossible
	    }
	}

	this.url = url;
	this.username = hudson.Util.fixEmptyAndTrim(username);
	this.password = hudson.Util.fixEmptyAndTrim(password);
    }

    /**
     * Creates a remote access session to this Confluence site
     * 
     * @return {@link ConfluenceSession}
     * @throws RemoteException
     */
    public ConfluenceSession createSession() throws RemoteException {
	final String rpcUrl = Util.confluenceUrlToSoapUrl(url.toExternalForm());
	final ConfluenceSoapService service = XmlRpcClient.getInstance(rpcUrl);
	final String token;
	if (username != null && password != null) {
	    token = service.login(username, password);
	} else {
	    // Empty string token means anonymous access
	    token = "";
	}
	return new ConfluenceSession(service, token);
    }

    public DescriptorImpl getDescriptor() {
	return (DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(getClass());
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
	public FormValidation doLoginCheck(@QueryParameter String url, @QueryParameter String username,
		@QueryParameter String password) throws IOException {
	    url = hudson.Util.fixEmpty(url);
	    if (url == null) {// URL not entered yet
		return FormValidation.ok();
	    }
	    username = hudson.Util.fixEmpty(username);
	    password = hudson.Util.fixEmpty(password);
	    if (username == null || password == null) {
		return FormValidation.warning("Enter username and password");
	    }
	    ConfluenceSite site = new ConfluenceSite(new URL(url), username, password);
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
	public FormValidation doUrlCheck(@QueryParameter final String url) throws IOException, ServletException {
	    // this can be used to check existence of any file in any URL, so
	    // admin only
	    if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
		return FormValidation.ok();

	    final String newurl = hudson.Util.fixEmpty(url);

	    return new FormValidation.URLCheck() {
		@Override
		protected FormValidation check() throws IOException, ServletException {

		    if (newurl == null) {
			return FormValidation.error("Enter a URL");
		    }

		    try {
			if (findText(open(new URL(newurl)), "Atlassian Confluence"))
			    return FormValidation.ok();
			else
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

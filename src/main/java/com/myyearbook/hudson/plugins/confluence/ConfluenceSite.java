package com.myyearbook.hudson.plugins.confluence;

import hudson.model.AbstractProject;
import hudson.plugins.jira.soap.ConfluenceSoapService;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.myyearbook.hudson.plugins.confluence.rpc.XmlRpcClient;

public class ConfluenceSite {
    private static final Logger LOGGER = Logger.getLogger(ConfluenceSite.class.getName());

    public final URL url;
    public final String username;
    public final String password;

    /**
     * Stapler constructor
     * 
     * @param url
     * @param username
     * @param password
     */
    @DataBoundConstructor
    public ConfluenceSite(@QueryParameter("confluence.url") URL url,
	    @QueryParameter("confluence.username") final String username,
	    @QueryParameter("confluence.password") final String password) {
	if (!url.toExternalForm().endsWith("/")) {
	    try {
		url = new URL(url.toExternalForm() + "/");
	    } catch (MalformedURLException e) {
		throw new AssertionError(e); // impossible
	    }
	}

	this.url = url;
	this.username = username;
	this.password = password;
    }

    public String getName() {
	return this.url.getHost();
    }

    @Override
    public String toString() {
	return "ConfluenceSite[" + getName() + "]";
    }

    public ConfluenceSession createSession() throws RemoteException {
	final String rpcUrl = Util.confluenceUrlToSoapUrl(url.toExternalForm());
	ConfluenceSoapService service = XmlRpcClient.getInstance(rpcUrl);
	return new ConfluenceSession(this, service, service.login(username, password));
    }
}

package com.myyearbook.hudson.plugins.confluence;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.codehaus.swizzle.confluence.ConfluenceException;
import org.codehaus.swizzle.confluence.Page;
import org.codehaus.swizzle.confluence.SwizzleException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.myyearbook.hudson.plugins.confluence.rpc.Client;

public class ConfluencePublisher extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(ConfluencePublisher.class.getName());

    private String url;

    @DataBoundConstructor
    public ConfluencePublisher(@QueryParameter("confluence.url") String url) throws MalformedURLException,
	    SwizzleException {

	url = "http://confluence.mybdev.com/display/FLASH/Download+API";

	this.url = url;
	final Client client = Client.getInstance(url);
	client.login("tuser", "temp1pass.");
    }

    public BuildStepMonitor getRequiredMonitorService() {
	return BuildStepMonitor.BUILD;
    }

    @Override
    public DescriptorImpl getDescriptor() {
	return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
	private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

	@Override
	public boolean isApplicable(Class<? extends AbstractProject> arg0) {
	    return true;
	}

	@Override
	public String getDisplayName() {
	    return "Publish to Confluence";
	}

	@Override
	public Publisher newInstance(StaplerRequest req, JSONObject formData)
		throws hudson.model.Descriptor.FormException {
	    return req.bindJSON(ConfluencePublisher.class, formData);
	}

	public FormValidation doUrlCheck(@QueryParameter("confluence.url") String url) throws ConfluenceException,
		SwizzleException, MalformedURLException {
	    String rpcUrl = Util.confluenceUrlToXmlRpcUrl(url);

	    Client client = Client.getInstance(rpcUrl);

	    client.login("tuser", "temp1pass.");
	    LOGGER.log(Level.INFO, "Called LOGIN method");

	    Page pageData = client.getPage("FLASH", "Download API");
	    LOGGER.log(Level.INFO, "Get page data for Download+API: " + pageData.getId());

	    LOGGER.log(Level.FINE, "Content: {0}", new Object[] { pageData.getContent() });
	    

	    return FormValidation.ok("Success!");
	}
    }
}

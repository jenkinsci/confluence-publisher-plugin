package com.myyearbook.hudson.plugins.confluence;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.plugins.jira.soap.RemoteAttachment;
import hudson.plugins.jira.soap.RemotePage;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.axis.AxisFault;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class ConfluencePublisher extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(ConfluencePublisher.class.getName());
    private final String siteName;
    private final String fileSet;
    private final boolean attachArtifacts;
    private String spaceName;
    private String pageName;

    @DataBoundConstructor
    public ConfluencePublisher(@QueryParameter("confluence.siteName") String siteName,
	    @QueryParameter("confluence.spaceName") final String spaceName,
	    @QueryParameter("confluence.pageName") final String pageName,
	    @QueryParameter("confluence.attachArtifacts") final boolean attachArtifacts,
	    @QueryParameter("confluence.fileSet") final String fileSet) {

	if (siteName == null) {
	    ConfluenceSite[] sites = getDescriptor().getSites();
	    if (sites.length > 0) {
		siteName = sites[0].getName();
	    }
	}
	this.siteName = siteName;
	this.spaceName = spaceName;
	this.pageName = pageName;
	this.attachArtifacts = attachArtifacts;
	this.fileSet = fileSet;
    }

    public ConfluenceSite getSite() {
	ConfluenceSite[] sites = getDescriptor().getSites();
	if (siteName == null && sites.length > 0) {
	    // default
	    return sites[0];
	}

	for (ConfluenceSite site : sites) {
	    if (site.getName().equals(siteName)) {
		return site;
	    }
	}
	return null;
    }

    public BuildStepMonitor getRequiredMonitorService() {
	return BuildStepMonitor.BUILD;
    }

    @Override
    public DescriptorImpl getDescriptor() {
	return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
	    throws InterruptedException, IOException {

	ConfluenceSite site = getSite();
	ConfluenceSession confluence = site.createSession();

	// TODO: edit pages?

	if (!this.attachArtifacts) {
	    // Nothing to attach
	    return true;
	}

	if (!Result.SUCCESS.equals(build.getResult())) {
	    // Don't process for unsuccessful builds
	    return true;
	}

	FilePath ws = build.getWorkspace();
	if (ws == null) {
	    // Possibly running on a slave that went down
	    return true;
	}

	String artifacts = build.getEnvironment(listener).expand(this.fileSet);
	FilePath[] files = ws.list(artifacts);
	if (files.length == 0) {
	    listener.getLogger().println("[confluence] No files matched the pattern '" + this.fileSet + "'.");

	    String msg = null;
	    try {
		msg = ws.validateAntFileMask(artifacts);
	    } catch (Exception e) {
		listener.getLogger().println("[confluence] " + e.getMessage());
	    }

	    if (msg != null) {
		listener.getLogger().println("[confluence] " + msg);
	    }
	    return true;
	}

	listener.getLogger().println("[confluence] Found " + files.length + " artifacts to upload to Confluence...");

	long pageId;
	RemotePage pageData = confluence.getPage(spaceName, pageName);
	pageId = pageData.getId();

	for (FilePath file : files) {
	    listener.getLogger().println("[confluence] Uploading " + file.getName() + " ...");
	    try {
		RemoteAttachment result = confluence.addAttachment(pageId, file);
		listener.getLogger().println("[confluence] Result: " + result.getUrl());
	    } catch (RemoteException re) {
		listener.error("[confluence] Unable to upload file...");
		re.printStackTrace(listener.getLogger());
	    }
	}

	return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
	private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

	private final CopyOnWriteList<ConfluenceSite> sites = new CopyOnWriteList<ConfluenceSite>();

	public DescriptorImpl() {
	    super(ConfluencePublisher.class);
	    load();
	}

	@Override
	public boolean isApplicable(Class<? extends AbstractProject> p) {
	    LOGGER.log(Level.INFO, "in publisher, sites: " + sites);
	    return sites != null && sites.size() > 0;
	}

	public ConfluenceSite[] getSites() {
	    return sites.toArray(new ConfluenceSite[0]);
	}

	@Override
	public String getDisplayName() {
	    return "Publish to Confluence";
	}

	@Override
	public boolean configure(StaplerRequest req, JSONObject formData) {
	    LOGGER.log(Level.INFO, "Saving configuration from global!");
	    sites.replaceBy(req.bindParametersToList(ConfluenceSite.class, "confluence."));
	    save();
	    return true;
	}

	@Override
	public Publisher newInstance(StaplerRequest req, JSONObject formData)
		throws hudson.model.Descriptor.FormException {
	    LOGGER.log(Level.INFO, "Creating instance of Confluence Publisher");
	    return req.bindJSON(ConfluencePublisher.class, formData);
	}

	/**
	 * Checks if the JIRA URL is accessible and exists.
	 */
	public FormValidation doUrlCheck(@QueryParameter final String value) throws IOException, ServletException {
	    // this can be used to check existence of any file in any URL, so
	    // admin only
	    if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
		return FormValidation.ok();

	    return new FormValidation.URLCheck() {
		@Override
		protected FormValidation check() throws IOException, ServletException {
		    String url = hudson.Util.fixEmpty(value);
		    if (url == null) {
			return FormValidation.error("Enter a URL");
		    }

		    try {
			if (findText(open(new URL(url)), "Atlassian Confluence"))
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

	/**
	 * Checks if the user name and password are valid.
	 */
	public FormValidation doLoginCheck(StaplerRequest request) throws IOException {
	    String url = hudson.Util.fixEmpty(request.getParameter("url"));
	    if (url == null) {// URL not entered yet
		return FormValidation.ok();
	    }
	    String user = hudson.Util.fixEmpty(request.getParameter("user"));
	    String pass = hudson.Util.fixEmpty(request.getParameter("pass"));
	    if (user == null || pass == null) {
		return FormValidation.warning("Enter username and password");
	    }
	    ConfluenceSite site = new ConfluenceSite(new URL(url), user, pass);
	    try {
		site.createSession();
		return FormValidation.ok();
	    } catch (AxisFault e) {
		LOGGER.log(Level.WARNING, "Failed to login to Confluence at " + url, e);
		return FormValidation.error(e.getFaultString());
	    } catch (RemoteException e) {
		LOGGER.log(Level.WARNING, "Failed to login to Confluence at " + url, e);
		return FormValidation.error(e.getMessage());
	    }
	}
    }
}

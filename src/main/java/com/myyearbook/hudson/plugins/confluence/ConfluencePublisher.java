package com.myyearbook.hudson.plugins.confluence;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.jira.soap.RemoteAttachment;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemoteSpace;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class ConfluencePublisher extends Notifier {

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
	private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

	private final List<ConfluenceSite> sites = new ArrayList<ConfluenceSite>();

	public DescriptorImpl() {
	    super(ConfluencePublisher.class);
	    load();
	}

	@Override
	public boolean configure(StaplerRequest req, JSONObject formData) {
	    LOGGER.log(Level.INFO, "Saving configuration from global! json: " + formData.toString());
	    this.setSites(req.bindJSONToList(ConfluenceSite.class, formData.get("sites")));
	    save();
	    return true;
	}

	public FormValidation doPageNameCheck(@QueryParameter final String siteName,
		@QueryParameter final String spaceName, @QueryParameter final String pageName) {
	    ConfluenceSite site = this.getSiteByName(siteName);

	    if (hudson.Util.fixEmptyAndTrim(spaceName) == null || hudson.Util.fixEmptyAndTrim(pageName) == null) {
		return FormValidation.ok();
	    }

	    if (site == null) {
		return FormValidation.error("Unknown site:" + siteName);
	    }

	    try {
		ConfluenceSession confluence = site.createSession();
		RemotePage page = confluence.getPage(spaceName, pageName);
		if (page != null) {
		    return FormValidation.ok("OK: " + page.getTitle());
		}
		return FormValidation.error("Page not found");
	    } catch (RemoteException re) {
		return FormValidation.error(re, re.getMessage());
	    }
	}

	public FormValidation doSpaceNameCheck(@QueryParameter final String siteName,
		@QueryParameter final String spaceName) {
	    ConfluenceSite site = this.getSiteByName(siteName);

	    if (hudson.Util.fixEmptyAndTrim(spaceName) == null) {
		return FormValidation.ok();
	    }

	    if (site == null) {
		return FormValidation.error("Unknown site:" + siteName);
	    }

	    try {
		ConfluenceSession confluence = site.createSession();
		RemoteSpace space = confluence.getSpace(spaceName);
		if (space != null) {
		    return FormValidation.ok("OK: " + space.getName());
		}
		return FormValidation.error("Space not found");
	    } catch (RemoteException re) {
		return FormValidation.error(re, re.getMessage());
	    }
	}

	@Override
	public String getDisplayName() {
	    return "Publish to Confluence";
	}

	public ConfluenceSite getSiteByName(String siteName) {
	    for (ConfluenceSite site : sites) {
		if (site.getName().equals(siteName)) {
		    return site;
		}
	    }
	    return null;
	}

	public List<ConfluenceSite> getSites() {
	    LOGGER.log(Level.INFO, "getSites: " + sites);
	    return sites;
	}

	@Override
	public boolean isApplicable(Class<? extends AbstractProject> p) {
	    LOGGER.log(Level.INFO, "in publisher, sites: " + sites);
	    return sites != null && sites.size() > 0;
	}

	@Override
	public Publisher newInstance(StaplerRequest req, JSONObject formData)
		throws hudson.model.Descriptor.FormException {
	    LOGGER.log(Level.INFO, "Creating instance of Confluence Publisher");
	    return req.bindJSON(ConfluencePublisher.class, formData);
	}

	public void setSites(List<ConfluenceSite> sites) {
	    LOGGER.log(Level.INFO, "+setSites: " + this.sites);
	    this.sites.clear();
	    this.sites.addAll(sites);
	    LOGGER.log(Level.INFO, "-setSites: " + this.sites);
	}
    }

    private static final Logger LOGGER = Logger.getLogger(ConfluencePublisher.class.getName());
    private static final String CONTENT_TYPE = "application/octet-stream";
    private final String siteName;
    private final boolean attachArchivedArtifacts;
    private final boolean attachArtifacts;
    private final String fileSet;

    private final String spaceName;
    private final String pageName;

    @DataBoundConstructor
    public ConfluencePublisher(String siteName, final String spaceName, final String pageName,
	    final boolean attachArtifacts, final boolean attachArchivedArtifacts, final String fileSet) {

	if (siteName == null) {
	    List<ConfluenceSite> sites = getDescriptor().getSites();
	    if (sites != null && sites.size() > 0) {
		siteName = sites.get(0).getName();
	    }
	}
	this.siteName = siteName;
	this.spaceName = spaceName;
	this.pageName = pageName;
	this.attachArtifacts = true;// attachArtifacts;
	this.attachArchivedArtifacts = attachArchivedArtifacts;
	this.fileSet = fileSet;

	LOGGER.log(Level.INFO, "Data-bound: {0}, {1}, {2}, {3}", new Object[] { siteName, spaceName, pageName,
		attachArchivedArtifacts });
    }

    @Override
    public DescriptorImpl getDescriptor() {
	return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * @return the fileSet
     */
    public String getFileSet() {
	return fileSet;
    }

    /**
     * @return the pageName
     */
    public String getPageName() {
	return pageName;
    }

    public BuildStepMonitor getRequiredMonitorService() {
	return BuildStepMonitor.BUILD;
    }

    public ConfluenceSite getSite() {
	List<ConfluenceSite> sites = getDescriptor().getSites();

	if (sites == null) {
	    return null;
	}

	if (siteName == null && sites.size() > 0) {
	    // default
	    return sites.get(0);
	}

	for (ConfluenceSite site : sites) {
	    if (site.getName().equals(siteName)) {
		return site;
	    }
	}
	return null;
    }

    /**
     * @return the siteName
     */
    public String getSiteName() {
	return siteName;
    }

    /**
     * @return the spaceName
     */
    public String getSpaceName() {
	return spaceName;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
	    throws InterruptedException, IOException {

	ConfluenceSite site = getSite();
	ConfluenceSession confluence = site.createSession();

	// TODO: edit pages?

	if (!this.attachArtifacts) {
	    // Nothing to attach
	    log(listener, "Not attaching files.");
	    return true;
	}

	if (!Result.SUCCESS.equals(build.getResult())) {
	    // Don't process for unsuccessful builds
	    log(listener, "Build status is not SUCCESS (" + build.getResult().toString() + ").");
	    return true;
	}

	FilePath ws = build.getWorkspace();
	if (ws == null) {
	    // Possibly running on a slave that went down
	    log(listener, "Workspace is null!");
	    return true;
	}

	long pageId;
	RemotePage pageData = confluence.getPage(spaceName, pageName);
	pageId = pageData.getId();
	String attachmentComment = build.getEnvironment(listener).expand("Published from Hudson build: $BUILD_URL");

	// //FIXME:
	// if (this.attachArchivedArtifacts) {
	// List<Run<?, ?>.Artifact> existingArtifacts = build.getArtifacts();
	// Run<?, ?>.Artifact artifact;
	// Iterator<Run<?, ?>.Artifact> it = existingArtifacts.iterator();
	// while (it.hasNext()) {
	// artifact = it.next();
	// log(listener, "Uploading as attachment: " + artifact.getFileName());
	// confluence.addAttachment(pageId, artifact.getFile(), CONTENT_TYPE,
	// attachmentComment);
	// }
	// }

	if (this.fileSet == null) {
	    log(listener, "No fileset pattern configured.");
	    return true;
	}

	log(listener, "Uploading attachments to Confluence page: " + pageData.getUrl());

	String artifacts = build.getEnvironment(listener).expand(this.fileSet);
	FilePath[] files = ws.list(artifacts);
	if (files.length == 0) {
	    log(listener, "No files matched the pattern '" + this.fileSet + "'.");

	    String msg = null;
	    try {
		msg = ws.validateAntFileMask(artifacts);
	    } catch (Exception e) {
		log(listener, "" + e.getMessage());
	    }

	    if (msg != null) {
		log(listener, "" + msg);
	    }
	    return true;
	}

	log(listener, "Found " + files.length + " artifact(s) to upload to Confluence...");

	for (FilePath file : files) {
	    final String fileName = file.getName();
	    final String contentType = URLConnection.guessContentTypeFromName(fileName);
	    log(listener, " - Uploading " + fileName + " (" + contentType + ") ...");
	    try {
		RemoteAttachment result = confluence.addAttachment(pageId, file, contentType, attachmentComment);
		log(listener, "   done: " + result.getUrl());
	    } catch (RemoteException re) {
		listener.error("Unable to upload file...");
		re.printStackTrace(listener.getLogger());
		return false;
	    }
	}
	log(listener, "Done");

	return true;
    }

    protected void log(BuildListener listener, String message) {
	listener.getLogger().println("[confluence] " + message);
    }

    /**
     * @return the attachArchivedArtifacts
     */
    public boolean shouldAttachArchivedArtifacts() {
	return attachArchivedArtifacts;
    }

    /**
     * @return the attachArtifacts
     */
    public boolean shouldAttachArtifacts() {
	return attachArtifacts;
    }
}

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

import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor;
import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor.TokenNotFoundException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import jenkins.plugins.confluence.soap.v1.RemoteAttachment;
import jenkins.plugins.confluence.soap.v1.RemotePage;
import jenkins.plugins.confluence.soap.v1.RemotePageSummary;
import jenkins.plugins.confluence.soap.v1.RemotePageUpdateOptions;
import jenkins.plugins.confluence.soap.v1.RemoteSpace;

public class ConfluencePublisher extends Notifier implements Saveable {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final String siteName;
    private final boolean attachArchivedArtifacts;
    private final boolean buildIfUnstable;
    private final String fileSet;
    private final boolean replaceAttachments;
    private final String labels;

    private final String spaceName;
    private final String pageName;

    private DescribableList<MarkupEditor, Descriptor<MarkupEditor>> editors = new DescribableList<MarkupEditor, Descriptor<MarkupEditor>>(
            this);

    @DataBoundConstructor
    public ConfluencePublisher(String siteName, final boolean buildIfUnstable,
            final String spaceName, final String pageName, final String labels, final boolean attachArchivedArtifacts,
            final String fileSet, final List<MarkupEditor> editorList, final boolean replaceAttachments) throws IOException {

        if (siteName == null) {
            List<ConfluenceSite> sites = getDescriptor().getSites();

            if (sites != null && sites.size() > 0) {
                siteName = sites.get(0).getName();
            }
        }

        this.siteName = siteName;
        this.spaceName = spaceName;
        this.pageName = pageName;
        this.labels = labels;
        this.buildIfUnstable = buildIfUnstable;
        this.attachArchivedArtifacts = attachArchivedArtifacts;
        this.fileSet = fileSet;
        this.replaceAttachments = replaceAttachments;

        if (editorList != null) {
            this.editors.addAll(editorList);
        } else {
            this.editors.clear();
        }
    }

    @Exported
    public List<MarkupEditor> getConfiguredEditors() {
        return this.editors.toList();
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

    /**
     * @return the labels
     */
    public String getLabels() {
        return labels;
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

    protected List<RemoteAttachment> performAttachments(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener, ConfluenceSession confluence,
            final RemotePageSummary pageData) throws IOException, InterruptedException {

        final long pageId = pageData.getId();
        FilePath ws = build.getWorkspace();
        final List<RemoteAttachment> remoteAttachments = new ArrayList<RemoteAttachment>();
        if (ws == null) {
            // Possibly running on a slave that went down
            log(listener, "Workspace is unavailable.");
            return remoteAttachments;
        }

        String attachmentComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");

        log(listener, "Uploading attachments to Confluence page: " + pageData.getUrl());

        final List<FilePath> files = new ArrayList<FilePath>();

        if (this.attachArchivedArtifacts) {
            final List<FilePath> archived = this.findArtifacts(build.getArtifactsDir());

            if (archived.size() == 0) {
                log(listener, "Attempting to attach the archived artifacts, but there are no"
                        + " archived artifacts from the job! Check job configuration...");
            } else {
                log(listener, "Found " + archived.size()
                        + " archived artifact(s) to upload to Confluence...");
                files.addAll(archived);
            }
        }

        final String fileSet = hudson.Util.fixEmptyAndTrim(this.fileSet);

        if (!StringUtils.isEmpty(fileSet)) {
            log(listener, "Evaluating fileset pattern: " + fileSet);

            // Expand environment variables
            final String artifacts = build.getEnvironment(listener).expand(fileSet);
            // Obtain a list of all files that match the pattern
            final FilePath[] workspaceFiles = ws.list(artifacts);

            if (workspaceFiles.length > 0) {
                log(listener, "Found " + workspaceFiles.length
                        + " workspace artifact(s) to upload to Confluence...");

                for (FilePath file : workspaceFiles) {
                    if (!files.contains(file)) {
                        files.add(file);
                    } else {
                        // Don't include the file twice if it's already in the
                        // list
                        log(listener, " - pattern matched an archived artifact: " + file.getName());
                    }
                }
            } else {
                log(listener, "No files matched the pattern '" + fileSet + "'.");
                String msg = null;

                try {
                    msg = ws.validateAntFileMask(artifacts);
                } catch (Exception e) {
                    log(listener, "" + e.getMessage());
                }

                if (msg != null) {
                    log(listener, "" + msg);
                }
            }
        }

        log(listener, "Uploading " + files.size() + " file(s) to Confluence...");

	boolean shouldRemoveExistingAttachments = false;
	List<RemoteAttachment> existingAtachments = null;
        if(shouldReplaceAttachments()){
            RemoteAttachment[] attachments = confluence.getAttachments(pageId);
            if(attachments != null &&  attachments.length > 0){
                existingAtachments = Arrays.asList(confluence.getAttachments(pageId));
                shouldRemoveExistingAttachments = true;
            }
        }

        for (FilePath file : files) {
            final String fileName = file.getName();

            if(shouldRemoveExistingAttachments){
		for (RemoteAttachment remoteAttachment : existingAtachments) {
			if(remoteAttachment.getFileName().equals(fileName)){
				try{
					confluence.removeAttachment(pageId, remoteAttachment);
					existingAtachments.remove(remoteAttachment);
				log(listener, "Deleted existing " + remoteAttachment.getFileName() + " from Confluence before upload new...");
					break;
				}catch (RemoteException e) {
					log(listener, "Deleting error: " + e.toString());
					throw e;
				}
			}
                }
            }

            String contentType = URLConnection.guessContentTypeFromName(fileName);

            if (StringUtils.isEmpty(contentType)) {
                // Confluence does not allow an empty content type
                contentType = DEFAULT_CONTENT_TYPE;
            }

            log(listener, " - Uploading file: " + fileName + " (" + contentType + ")");

            try {
                final RemoteAttachment result = confluence.addAttachment(pageId, file,
                        contentType, attachmentComment);
                remoteAttachments.add(result);
                log(listener, "   done: " + result.getUrl());
            } catch (IOException ioe) {
                listener.error("Unable to upload file...");
                ioe.printStackTrace(listener.getLogger());
            } catch (InterruptedException ie) {
                listener.error("Unable to upload file...");
                ie.printStackTrace(listener.getLogger());
            }
        }
        log(listener, "Done");

        return remoteAttachments;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws RemoteException {

        boolean result = true;
        ConfluenceSite site = getSite();

        if (site == null) {
            log(listener, "Not publishing because no Confluence Site could be found. " +
                    "Check your Confluence configuration in system settings.");
            return true;
        }

        ConfluenceSession confluence = site.createSession();
        Result buildResult = build.getResult();

        if (!buildIfUnstable && !Result.SUCCESS.equals(buildResult)) {
            // Don't process for unsuccessful builds
            log(listener, "Build status is not SUCCESS (" + build.getResult().toString() + ").");
            return true;
        }

        EnvVarAction buildResultAction = new EnvVarAction("BUILD_RESULT", String
                .valueOf(buildResult));
        build.addAction(buildResultAction);

        String spaceName = this.spaceName;
        String pageName = this.pageName;

        try {
            spaceName = build.getEnvironment(listener).expand(spaceName);
            pageName = build.getEnvironment(listener).expand(pageName);
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.getLogger());
        }

        RemotePageSummary pageData;

        try {
            pageData = confluence.getPageSummary(spaceName, pageName);
        } catch (RemoteException e) {
            // Still shouldn't fail the job, so just dump this to the console and keep going (true).
            log(listener, "Unable to locate page: " + spaceName + "/" + pageName + ".  Attempting to create the page now...");

            try {
                pageData = this.createPage(confluence, spaceName, pageName);
            } catch (RemoteException exc2) {
                log(listener, "Page could not be created!  Aborting edits...");
                e.printStackTrace(listener.getLogger());
                return true;
            }
        }
        
        // Add the page labels
        String labels = this.labels;
        if (StringUtils.isNotBlank(labels)) {
            try {
                String expandedLabels = build.getEnvironment(listener).expand(labels);
                result &= confluence.addLabels(pageData.getId(), expandedLabels);

            } catch (OperationNotSupportedException e) {
                e.printStackTrace(listener.getLogger());
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
            } catch (InterruptedException e) {
                e.printStackTrace(listener.getLogger());
            }
        }

        // Perform attachment uploads
        List<RemoteAttachment> remoteAttachments = null;
        try {
            remoteAttachments = this.performAttachments(build, launcher, listener, confluence, pageData);
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.getLogger());
        }

        // Wiki editing is only supported in versions prior to 4.0
        if (!editors.isEmpty()) {
            if (!confluence.isVersion4() && pageData instanceof RemotePage) {
                // Perform wiki replacements
                try {
                    result &= this.performWikiReplacements(build, launcher, listener, confluence,
                            (RemotePage) pageData, remoteAttachments);
                } catch (IOException e) {
                    e.printStackTrace(listener.getLogger());
                } catch (InterruptedException e) {
                    e.printStackTrace(listener.getLogger());
                }
            } else {
                log(listener, "EXPERIMENTAL: performing storage format edits on Confluence 4.0");

                // Must use the v2 API for this.
                jenkins.plugins.confluence.soap.v2.RemotePage pageDataV2 = confluence
                        .getPageV2(pageData.getId());

                try {
                    result &= this.performWikiReplacements(build, launcher, listener, confluence,
                            pageDataV2, remoteAttachments);
                } catch (IOException e) {
                    e.printStackTrace(listener.getLogger());
                } catch (InterruptedException e) {
                    e.printStackTrace(listener.getLogger());
                }
            }
        }

        // Not returning `result`, because this publisher should not
        // fail the job
        return true;
    }

    /**
     * Creates a new Page in Confluence.
     * 
     * @param confluence
     * @param spaceName
     * @param pageName
     * @return The resulting Page
     * @throws RemoteException
     */
    private RemotePage createPage(ConfluenceSession confluence, String spaceName, String pageName)
            throws RemoteException {
        RemotePage newPage = new RemotePage();
        newPage.setTitle(pageName);
        newPage.setSpace(spaceName);
        newPage.setContent("");
        return confluence.storePage(newPage);
    }

    private boolean performWikiReplacements(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener, ConfluenceSession confluence,
            jenkins.plugins.confluence.soap.v2.RemotePage pageDataV2, List<RemoteAttachment> remoteAttachments) throws IOException,
            InterruptedException {

        final String editComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");
        final jenkins.plugins.confluence.soap.v2.RemotePageUpdateOptions options = new jenkins.plugins.confluence.soap.v2.RemotePageUpdateOptions(
                false, editComment);

        // Get current content
        String content = performEdits(build, listener, pageDataV2.getContent(), true, remoteAttachments);

        // Now set the replacement content
        pageDataV2.setContent(content);
        confluence.updatePageV2(pageDataV2, options);
        return true;
    }

    /**
     *
     * @param build
     * @param launcher
     * @param listener
     * @param confluence
     * @param pageData
     * @param remoteAttachments
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    protected boolean performWikiReplacements(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener, ConfluenceSession confluence, RemotePage pageData, List<RemoteAttachment> remoteAttachments)
            throws IOException, InterruptedException {

        final String editComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");
        final RemotePageUpdateOptions options = new RemotePageUpdateOptions(false, editComment);

        // Get current content
        String content = performEdits(build, listener, pageData.getContent(), false, remoteAttachments);

        // Now set the replacement content
        pageData.setContent(content);
        confluence.updatePage(pageData, options);
        return true;
    }

    private String performEdits(final AbstractBuild<?, ?> build, final BuildListener listener,
            String content, final boolean isNewFormat, List<RemoteAttachment> remoteAttachments) {
        for (MarkupEditor editor : this.editors) {
            log(listener, "Performing wiki edits: " + editor.getDescriptor().getDisplayName());

            try {
                content = editor.performReplacement(build, listener, content, isNewFormat, remoteAttachments);
            } catch (TokenNotFoundException e) {
                log(listener, "ERROR while performing replacement: " + e.getMessage());
            }
        }

        return content;
    }

    /**
     * Recursively scan a directory, returning all files encountered
     * 
     * @param artifactsDir
     * @return
     */
    private List<FilePath> findArtifacts(File artifactsDir) {
        ArrayList<FilePath> files = new ArrayList<FilePath>();

        if (artifactsDir != null && artifactsDir.isDirectory()) {
            File[] listed = artifactsDir.listFiles();

            if (listed != null) {
                for (File f : listed) {
                    if (f == null) continue;

                    if (f.isDirectory()) {
                        files.addAll(findArtifacts(f));
                    } else if (f.isFile()) {
                        files.add(new FilePath(f));
                    }
                }
            }
        }

        return files;
    }

    /**
     * Log helper
     * 
     * @param listener
     * @param message
     */
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
     * @return the buildIfUnstable
     */
    public boolean shouldBuildIfUnstable() {
        return buildIfUnstable;
    }
    /**
     * @return the replaceAttachments
     */
    public boolean shouldReplaceAttachments() {
        return replaceAttachments;
    }

    public void save() throws IOException {
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private final List<ConfluenceSite> sites = new ArrayList<ConfluenceSite>();

        public DescriptorImpl() {
            super(ConfluencePublisher.class);
            load();
        }

        public List<Descriptor<MarkupEditor>> getEditors() {
            final List<Descriptor<MarkupEditor>> editors = new ArrayList<Descriptor<MarkupEditor>>();

            for (Descriptor<MarkupEditor> editor : MarkupEditor.all()) {
                editors.add(editor);
            }

            return editors;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            this.setSites(req.bindJSONToList(ConfluenceSite.class, formData.get("sites")));
            save();
            return true;
        }

        public FormValidation doPageNameCheck(@QueryParameter final String siteName,
                @QueryParameter final String spaceName, @QueryParameter final String pageName) {
            ConfluenceSite site = this.getSiteByName(siteName);

            if (hudson.Util.fixEmptyAndTrim(spaceName) == null
                    || hudson.Util.fixEmptyAndTrim(pageName) == null) {
                return FormValidation.ok();
            }

            if (site == null) {
                return FormValidation.error("Unknown site:" + siteName);
            }

            try {
                ConfluenceSession confluence = site.createSession();
                RemotePageSummary page = confluence.getPageSummary(spaceName, pageName);

                if (page != null) {
                    return FormValidation.ok("OK: " + page.getTitle());
                }

                return FormValidation.error("Page not found");
            } catch (RemoteException re) {
                if (StringUtils.contains(pageName, '$') || StringUtils.contains(spaceName, '$')) {
                    return FormValidation
                            .warning("Unable to determine if the page exists because it contains build-time parameters.");
                }

                return FormValidation.warning("Page not found. Check that the page still exists. "
                        + "If you continue, we'll try to create the page at publish-time.");
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
                if (StringUtils.contains(spaceName, '$')) {
                    return FormValidation
                            .warning("Unable to determine if the space exists because it contains build-time parameters.");
                }

                return FormValidation.error(re, "Space not found");
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
            return sites;
        }

        @Override
        public boolean isApplicable(
                @SuppressWarnings("rawtypes") Class<? extends AbstractProject> p) {
            return sites != null && sites.size() > 0;
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws hudson.model.Descriptor.FormException {
            return req.bindJSON(ConfluencePublisher.class, formData);
        }

        public void setSites(List<ConfluenceSite> sites) {
            this.sites.clear();
            this.sites.addAll(sites);
        }
    }

    /**
     * Build action that is capable of inserting arbitrary KVPs into the EnvVars.
     * 
     * @author jhansche
     */
    public static class EnvVarAction implements EnvironmentContributingAction {
        private final String name;
        private final String value;

        public EnvVarAction(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return null;
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            env.put(name, value);
		}
	}
}

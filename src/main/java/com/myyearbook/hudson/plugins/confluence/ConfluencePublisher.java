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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.naming.OperationNotSupportedException;

import jenkins.plugins.confluence.soap.v1.RemoteAttachment;
import jenkins.plugins.confluence.soap.v1.RemotePage;
import jenkins.plugins.confluence.soap.v1.RemotePageSummary;
import jenkins.plugins.confluence.soap.v1.RemotePageUpdateOptions;
import jenkins.plugins.confluence.soap.v1.RemoteSpace;

public final class ConfluencePublisher extends Notifier implements Saveable, SimpleBuildStep {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private @Nonnull final String siteName;
    private @Nonnull final String spaceName;
    private @Nonnull final String pageName;
    private boolean attachArchivedArtifacts;
    private boolean buildIfUnstable;
    private String fileSet;
    private boolean replaceAttachments;
    private String labels;
    private long parentId;
    private DescribableList<MarkupEditor, Descriptor<MarkupEditor>> editors = new DescribableList<>(
            this);

    @Deprecated
    public ConfluencePublisher(String siteName, final boolean buildIfUnstable,
            final String spaceName, final String pageName, final String labels, final boolean attachArchivedArtifacts,
            final String fileSet, final List<MarkupEditor> editorList, final boolean replaceAttachments, final long parentId) throws IOException {

        this(siteName, spaceName, pageName);

        setParentId(parentId);
        setLabels(labels);
        setBuildIfUnstable(buildIfUnstable);
        setAttachArchivedArtifacts(attachArchivedArtifacts);
        setFileSet(fileSet);
        setReplaceAttachments(replaceAttachments);
        setEditorList(editorList);
    }

    @DataBoundConstructor
    public ConfluencePublisher(@Nonnull String siteName, final @Nonnull String spaceName, final @Nonnull String pageName) {
        if (siteName == null) {
            List<ConfluenceSite> sites = getDescriptor().getSites();

            if (sites != null && sites.size() > 0) {
                siteName = sites.get(0).getName();
            }
        }

        this.siteName = siteName;
        this.spaceName = spaceName;
        this.pageName = pageName;
    }

    @DataBoundSetter
    public void setBuildIfUnstable(boolean buildIfUnstable) {
        this.buildIfUnstable = buildIfUnstable;
    }

    @DataBoundSetter
    public void setAttachArchivedArtifacts(boolean attachArchivedArtifacts) {
        this.attachArchivedArtifacts = attachArchivedArtifacts;
    }

    @DataBoundSetter
    public void setFileSet(final String fileSet) {
        this.fileSet = StringUtils.isEmpty(fileSet) ? null : fileSet;
    }

    @DataBoundSetter
    public void setReplaceAttachments(boolean replaceAttachments) {
        this.replaceAttachments = replaceAttachments;
    }

    @DataBoundSetter
    public void setLabels(final String labels) {
        this.labels = StringUtils.isEmpty(labels) ? null : labels;
    }

    @DataBoundSetter
    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    @DataBoundSetter
    public void setEditorList(final List<MarkupEditor> editorList) {
        if (editorList != null) {
            this.editors.addAll(editorList);
        } else {
            this.editors.clear();
        }
    }

    public List<MarkupEditor> getEditorList() {
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
    public @Nonnull String getPageName() {
        return pageName;
    }

    /**
     * @return the parentId
     */
    public long getParentId() {
        return parentId;
    }

    /**
     * @return the labels
     */
    public String getLabels() {
        return labels;
    }

    @Override
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
    public @Nonnull String getSiteName() {
        return siteName;
    }

    /**
     * @return the spaceName
     */
    public @Nonnull String getSpaceName() {
        return spaceName;
    }

    protected List<RemoteAttachment> performAttachments(Run<?, ?> build, FilePath ws,
            TaskListener listener, ConfluenceSession confluence,
            final RemotePageSummary pageData) throws IOException, InterruptedException {

        final long pageId = pageData.getId();
        final List<RemoteAttachment> remoteAttachments = new ArrayList<>();
        if (ws == null) {
            // Possibly running on a slave that went down
            log(listener, "Workspace is unavailable.");
            return remoteAttachments;
        }

        String attachmentComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");

        log(listener, "Uploading attachments to Confluence page: " + pageData.getUrl());

        final List<FilePath> files = new ArrayList<>();

        if (this.attachArchivedArtifacts) {
            final List<FilePath> archived = this.findArtifacts(build.getArtifactsDir());

            if (archived.isEmpty()) {
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
        if(isReplaceAttachments()){
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
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws InterruptedException, IOException {
        boolean result = true;
        ConfluenceSite site = getSite();

        if (site == null) {
            log(listener, "Not publishing because no Confluence Site could be found. " +
                    "Check your Confluence configuration in system settings.");
            return;
        }

        ConfluenceSession confluence = site.createSession();
        Result buildResult = build.getResult();

        if (!buildIfUnstable && buildResult != null && !Result.SUCCESS.equals(buildResult)) {
            // Don't process for unsuccessful builds
            log(listener, "Build status is not SUCCESS (" + buildResult + ").");
            return;
        }

        EnvVarAction buildResultAction = new EnvVarAction("BUILD_RESULT", String
                .valueOf(buildResult));
        build.addAction(buildResultAction);

        String spaceName = this.spaceName;
        String pageName = this.pageName;
        long parentId = this.parentId;

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
                // if we haven't specified a parent, assign the Space home page as the parent
                if (parentId == 0L) {
                    RemoteSpace space = confluence.getSpace(spaceName);
                    if (space != null) {
                        parentId = space.getHomePage();
                    }
                }

                pageData = this.createPage(confluence, spaceName, pageName, parentId);

            } catch (RemoteException exc2) {
                log(listener, "Page could not be created!  Aborting edits...");
                e.printStackTrace(listener.getLogger());
                return;
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
            remoteAttachments = this.performAttachments(build, filePath, listener, confluence, pageData);
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
                    result &= this.performWikiReplacements(build, filePath, listener, confluence,
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
                    result &= this.performWikiReplacements(build, filePath, listener, confluence,
                            pageDataV2, remoteAttachments);
                } catch (IOException e) {
                    e.printStackTrace(listener.getLogger());
                } catch (InterruptedException e) {
                    e.printStackTrace(listener.getLogger());
                }
            }
        }
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
    private RemotePage createPage(ConfluenceSession confluence, String spaceName, String pageName, long parentId)
            throws RemoteException {
        RemotePage newPage = new RemotePage();
        newPage.setTitle(pageName);
        newPage.setSpace(spaceName);
        newPage.setContent("");
        newPage.setParentId(parentId);
        return confluence.storePage(newPage);
    }

    private boolean performWikiReplacements(Run<?, ?> build, FilePath filePath, TaskListener listener,
			ConfluenceSession confluence,
            jenkins.plugins.confluence.soap.v2.RemotePage pageDataV2, List<RemoteAttachment> remoteAttachments)
			throws IOException, InterruptedException {

        final String editComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");
        final jenkins.plugins.confluence.soap.v2.RemotePageUpdateOptions options = new jenkins.plugins.confluence.soap.v2.RemotePageUpdateOptions(
                false, editComment);

        // Get current content
        String content = performEdits(build, filePath, listener, pageDataV2.getContent(), true, remoteAttachments);

        // Now set the replacement content
        pageDataV2.setContent(content);
        confluence.updatePageV2(pageDataV2, options);
        return true;
    }

    /**
     *
     * @param build
     * @param listener
     * @param confluence
     * @param pageData
     * @param remoteAttachments
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    protected boolean performWikiReplacements(Run<?, ?> build, FilePath filePath, TaskListener listener,
            ConfluenceSession confluence, RemotePage pageData, List<RemoteAttachment> remoteAttachments)
            throws IOException, InterruptedException {

        final String editComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");
        final RemotePageUpdateOptions options = new RemotePageUpdateOptions(false, editComment);

        // Get current content
        String content = performEdits(build, filePath, listener, pageData.getContent(), false, remoteAttachments);

        // Now set the replacement content
        pageData.setContent(content);
        confluence.updatePage(pageData, options);
        return true;
    }

    private String performEdits(final Run<?, ?> build, FilePath filePath, final TaskListener listener,
            String content, final boolean isNewFormat, List<RemoteAttachment> remoteAttachments) {
        for (MarkupEditor editor : this.editors) {
            log(listener, "Performing wiki edits: " + editor.getDescriptor().getDisplayName());

            try {
                content = editor.performReplacement(build, filePath, listener, content, isNewFormat, remoteAttachments);
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
        ArrayList<FilePath> files = new ArrayList<>();

        if (artifactsDir != null && artifactsDir.isDirectory()) {
            File[] listed = artifactsDir.listFiles();

            if (listed != null) {
                for (File f : listed) {
                    if (f == null) {
                        continue;
                    }

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
    protected void log(TaskListener listener, String message) {
        listener.getLogger().println("[confluence] " + message);
    }

    /**
     * @return the attachArchivedArtifacts
     */
    public boolean isAttachArchivedArtifacts() {
        return attachArchivedArtifacts;
    }

    /**
     * @return the buildIfUnstable
     */
    public boolean isBuildIfUnstable() {
        return buildIfUnstable;
    }
    /**
     * @return the replaceAttachments
     */
    public boolean isReplaceAttachments() {
        return replaceAttachments;
    }

    @Override
    public void save() throws IOException {
    }

    @Extension
    @Symbol("publishConfluence")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private final List<ConfluenceSite> sites = new ArrayList<>();

        public DescriptorImpl() {
            super(ConfluencePublisher.class);
            load();
        }

        public List<Descriptor<MarkupEditor>> getEditors() {
            final List<Descriptor<MarkupEditor>> editors = new ArrayList<>();

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

        public FormValidation doParentIdCheck(@QueryParameter final String siteName,
                                              @QueryParameter final String spaceName, @QueryParameter final String parentId) {
            ConfluenceSite site = this.getSiteByName(siteName);

            if (hudson.Util.fixEmptyAndTrim(spaceName) == null
                    || hudson.Util.fixEmptyAndTrim(parentId) == null) {
                return FormValidation.ok();
            }

            Long parentIdL;
            try {
                 parentIdL = Long.valueOf(parentId);
            } catch (NumberFormatException nfe) {
                return FormValidation.error("The parent page id should be a numeric id.");
            }

            if (site == null) {
                return FormValidation.error("Unknown site:" + siteName);
            }

            try {
                ConfluenceSession confluence = site.createSession();
                jenkins.plugins.confluence.soap.v2.RemotePage page = confluence.getPageV2(parentIdL);

                if (page != null) {
                    return FormValidation.ok("OK: " + page.getTitle());
                }

                return FormValidation.error("Page not found");
            } catch (RemoteException re) {
                return FormValidation.warning("Page not found. Check that the page still exists. ");
            }
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
            return Collections.unmodifiableList(sites);
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

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return null;
        }

        @Override
        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            env.put(name, value);
		}
	}
}

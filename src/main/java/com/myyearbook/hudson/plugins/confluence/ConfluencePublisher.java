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

import com.atlassian.confluence.api.model.content.*;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.service.exceptions.ServiceException;
import com.atlassian.fugue.Option;
import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor;
import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor.TokenNotFoundException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.VirtualFile;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ConfluencePublisher extends Notifier implements Saveable, SimpleBuildStep {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private @NonNull final String siteName;
    private @NonNull final String spaceName;
    private @NonNull final String pageName;
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
                               final String fileSet, final List<MarkupEditor> editorList, final boolean replaceAttachments, final long parentId) {

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
    public ConfluencePublisher(@NonNull String siteName, final @NonNull String spaceName, final @NonNull String pageName) {
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
    public @NonNull String getPageName() {
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
    public @NonNull String getSiteName() {
        return siteName;
    }

    /**
     * @return the spaceName
     */
    public @NonNull String getSpaceName() {
        return spaceName;
    }

    protected List<Content> performAttachments(Run build, FilePath ws,
                                               TaskListener listener, ConfluenceSession confluence,
                                               final Content pageContent) throws IOException, InterruptedException {

        final long pageId = pageContent.getId().asLong();
        final List<Content> remoteAttachments = new ArrayList<>();
        if (ws == null) {
            // Possibly running on a slave that went down
            log(listener, "Workspace is unavailable.");
            return remoteAttachments;
        }

        String attachmentComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: $BUILD_URL");

        log(listener, "Uploading attachments to Confluence page: " + pageContent.getTitle());

        final List<VirtualFile> files = new ArrayList<>();

        if (this.attachArchivedArtifacts) {
            final List<Run.Artifact> artifacts = build.getArtifacts();

            if (artifacts.isEmpty()) {
                log(listener, "Attempting to attach the archived artifacts, but there are no"
                        + " archived artifacts from the job! Check job configuration...");
            } else {
                log(listener, "Found " + artifacts.size()
                        + " archived artifact(s) to upload to Confluence...");
                for (Run.Artifact artifact : artifacts) {
                    files.add(build.getArtifactManager().root().child(artifact.relativePath));
                }
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
                        files.add(file.toVirtualFile());
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
                    msg = ws.validateAntFileMask(artifacts, FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
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
        List<Content> existingAttachments = new ArrayList<>();
        if (isReplaceAttachments()) {
            List<Content> attachments = confluence.getAttachments(pageId);
            if (attachments != null && attachments.size() > 0) {
                existingAttachments.addAll(attachments);
                shouldRemoveExistingAttachments = true;
            }
        }

        for (VirtualFile file : files) {
            final String fileName = file.getName();

            if (shouldRemoveExistingAttachments) {
                for (Content remoteAttachment : existingAttachments) {
                    if (remoteAttachment.getTitle().equals(fileName)) {
                        try {
                            confluence.removeAttachment(remoteAttachment);
                            existingAttachments.remove(remoteAttachment);
                            log(listener, "Deleted existing " + remoteAttachment.getTitle() + " from Confluence before upload new...");
                            break;
                        } catch (ServiceException e) {
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
                final PageResponse<Content> result = confluence.addAttachment(pageId, file,
                        contentType, attachmentComment);
                remoteAttachments.addAll(result.getResults());
                log(listener, "   done: " + result.getResults().stream()
                        .map(Content::getTitle).collect(Collectors.joining(", ")));
            } catch (ServiceException se) {
                listener.error("Unable to upload file...");
                se.printStackTrace(listener.getLogger());
            }
        }
        log(listener, "Done");

        return remoteAttachments;
    }

    @Override
    public void perform(@NonNull Run<?, ?> build, @NonNull FilePath filePath, @NonNull Launcher launcher,
                        @NonNull TaskListener listener) throws InterruptedException, IOException {
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

        log(listener, "ParentId: " + parentId);

        try {
            spaceName = build.getEnvironment(listener).expand(spaceName);
            pageName = build.getEnvironment(listener).expand(pageName);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(listener.getLogger());
        }

        Content pageContent;

        try {
            String spaceAndPageNames = String.format("%s/%s", spaceName, pageName);
            pageContent = confluence.getContent(spaceName, pageName, true).orElseThrow(() -> new ServiceException(String.format("Page at \"%s\" not found!", spaceAndPageNames)));
        } catch (ServiceException e) {
            // Still shouldn't fail the job, so just dump this to the console and keep going (true).
            log(listener, e.getMessage());
            log(listener, "Unable to locate page: " + spaceName + "/" + pageName + ".  Attempting to create the page now...");

            try {
                // if we haven't specified a parent, assign the Space home page as the parent
                if (parentId == 0L) {
                    Space space = confluence.getSpace(spaceName).getOrNull();
                    if (space != null) {
                        parentId = space.getId();
                    }
                }

                pageContent = this.createPage(confluence, spaceName, pageName, parentId);

            } catch (ServiceException exc) {
                log(listener, "Page could not be created!  Aborting edits...");
                exc.printStackTrace(listener.getLogger());
                return;
            }
        }

        // Perform attachment uploads
        List<Content> remoteAttachments = null;
        try {
            remoteAttachments = this.performAttachments(build, filePath, listener, confluence, pageContent);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(listener.getLogger());
        }

        if (!editors.isEmpty()) {
            // Perform wiki replacements
                result &= this.performWikiReplacements(build, filePath, listener, confluence,
                        pageContent, remoteAttachments);
        }

        // Add the page labels
        String labels = this.labels;
        if (StringUtils.isNotBlank(labels)) {
            try {
                String expandedLabels = build.getEnvironment(listener).expand(labels);
                result &= confluence.addLabels(pageContent.getId().asLong(), expandedLabels);

            } catch (ServiceException se) {
                log(listener, se.getMessage());
            }
        }
        if (result) {
            try {
                result &= performEditComment(build, listener, confluence, pageContent);
            } catch (ServiceException se) {
                log(listener, se.getMessage());
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
    private Content createPage(ConfluenceSession confluence, String spaceName, String pageName, long parentId)
            throws ServiceException {
        Content parentContent = confluence.getContent(String.valueOf(parentId))
                .orElseThrow(() -> new ServiceException("Can't find parent content with Id:" + parentId));
        Content.ContentBuilder newPage = Content.builder()
                .title(pageName)
                .type(ContentType.PAGE)
                .space(spaceName)
                .body(ContentBody.contentBodyBuilder().build())
                .parent(parentContent);
        return confluence.createContent(newPage.build());
    }


    private boolean performWikiReplacements(Run<?, ?> build, FilePath filePath, TaskListener listener,
                                            ConfluenceSession confluence,
                                            Content pageContent, List<Content> remoteAttachments) {

        boolean isUpdated = false;
        //Ugly Hack, though required here. DO NOT REMOVE, otherwise  Content.ContentBuilder.build() will fail.
        Consumer<Map<ContentType, PageResponse<Content>>> SANITIZE_NESTED_CONTENT_MAP = (m) ->
                m.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey)
                        .collect(Collectors.toList()).stream().forEach(m::remove);

        SANITIZE_NESTED_CONTENT_MAP.accept(pageContent.getChildren());
        SANITIZE_NESTED_CONTENT_MAP.accept(pageContent.getDescendants());

        // Get current content and edit.
        String originContent = pageContent.getBody().get(ContentRepresentation.STORAGE).getValue();

        String contentEdited = performEdits(build, filePath, listener, originContent, remoteAttachments);
        //XHTML -> HTML self closing tag adjustment
        contentEdited = contentEdited.replaceAll(" /", "/");

        // Now set the replacement contentBody
        ContentBody contentBody = ContentBody.contentBodyBuilder()
                .representation(ContentRepresentation.STORAGE)
                .value(contentEdited)
                .build();
        List<Content> ancestors = pageContent.getAncestors();
        Content updatedContent = Content.builder(pageContent)
                .version(pageContent.getVersion().nextBuilder().build())
                .body(contentBody)
                .parent(ancestors.get(ancestors.size() - 1))
                .build();

        //post updated content.
        Content results = confluence.updateContent(updatedContent);

        //Check if remote content is updated.
        Optional<Content> remoteResults =
                confluence.getContent(pageContent.getSpace().getKey(), pageContent.getTitle(), true);
        if (remoteResults.isPresent()) {
            isUpdated = remoteResults.get().getVersion().getNumber() == results.getVersion().getNumber();
        }

        return isUpdated;
    }

    private boolean performEditComment(Run<?, ?> build, TaskListener listener,
                                       ConfluenceSession confluence, Content pageContent)
            throws IOException, InterruptedException, ServiceException {
        boolean isUpdated = false;
        final String editComment = build.getEnvironment(listener).expand(
                "Published from Jenkins build: <a href=\"$BUILD_URL\">$BUILD_URL</a>");

        Optional<Content> previousComment = Optional.empty();
        List<Content> cl = new ArrayList<>();

        Optional.ofNullable(pageContent.getChildren()).ifPresent(cn ->
                Optional.ofNullable(cn.get(ContentType.COMMENT)).ifPresent(cm ->
                        Optional.ofNullable(cm.getResults()).ifPresent(cl::addAll
                        )
                )
        );

        if (!cl.isEmpty()) {
            previousComment = cl.stream()
                    .filter(c -> c.getBody().get(ContentRepresentation.STORAGE)
                            .getValue().contains(editComment.split(":")[0]))
                    .sorted(Comparator.comparing(c -> c.getVersion().getNumber())
                    ).findFirst();
        }

        if (previousComment.isPresent()) {
            //Edit comment Content
            Content comment = Content.builder()
                    .type(ContentType.COMMENT)
                    .version(previousComment.get().getVersion().nextBuilder().build())
                    .id(previousComment.get().getId())
                    .container(pageContent)
                    .title("Re: " + pageContent.getTitle())
                    .extension("location", "footer")
                    .status(ContentStatus.CURRENT)
                    .body(ContentBody.contentBodyBuilder()
                            .representation(ContentRepresentation.STORAGE)
                            .value(editComment)
                            .build())
                    .build();
            confluence.updateContent(comment);
        } else {
            //Post new comment.
            createComment(confluence, pageContent, editComment);

        }
        //Check if remote content is updated.
        Optional<Content> remoteResults =
                confluence.getContent(pageContent.getSpace().getKey(), pageContent.getTitle(), true);
        if (remoteResults.isPresent()) {
            isUpdated = remoteResults.get().getChildren().get(ContentType.COMMENT)
                    .getResults().stream().map(r -> r.getBody().get(ContentRepresentation.STORAGE).getValue())
                    .collect(Collectors.toList())
                    .contains(editComment);
        }
        return isUpdated;
    }

    /**
     * Creates a new Comment to Confluence page.
     *
     * @param confluence
     * @param parentContent
     * @param commentText
     * @return The resulting comment Content
     * @throws RemoteException
     */
    private Content createComment(ConfluenceSession confluence, Content parentContent, String commentText)
            throws ServiceException {
        Content.ContentBuilder newComment = Content.builder()
                .title("Re: " + parentContent.getTitle())
                .body(ContentBody.contentBodyBuilder()
                        .representation(ContentRepresentation.STORAGE)
                        .value(commentText)
                        .build())
                .container(parentContent)
                .type(ContentType.COMMENT);
        return confluence.createContent(newComment.build());
    }

    private String performEdits(final Run<?, ?> build, FilePath filePath, final TaskListener listener,
                                String content, List<Content> remoteAttachments) {
        for (MarkupEditor editor : this.editors) {
            log(listener, "Performing wiki edits: " + editor.getDescriptor().getDisplayName());

            try {
                content = editor.performReplacement(build, filePath, listener, content, true, remoteAttachments);
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
    public void save() {
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

            final List<Descriptor<MarkupEditor>> editors = new ArrayList<>(MarkupEditor.all());

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
                Content page = confluence.getContent(String.valueOf(parentIdL)).orElseThrow(() -> new ServiceException("Page content is NULL"));

                if (page != null) {
                    return FormValidation.ok("OK: " + page.getTitle());
                }

                return FormValidation.error("Page not found");
            } catch (ServiceException re) {
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
                Content page = confluence.getContent(spaceName, pageName, false).orElse(null);

                if (page != null) {
                    return FormValidation.ok("OK: " + page.getTitle());
                }

                return FormValidation.error("Page not found");
            } catch (ServiceException re) {
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
                Option<Space> space = confluence.getSpace(spaceName);

                if (!space.isEmpty()) {
                    return FormValidation.ok("OK: " + space.get().getName());
                }

                return FormValidation.error("Space not found");
            } catch (ServiceException re) {
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
        public boolean isApplicable(Class<? extends AbstractProject> p) {
            return sites != null && sites.size() > 0;
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) {
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

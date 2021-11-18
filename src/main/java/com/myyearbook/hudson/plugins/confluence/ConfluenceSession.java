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

import com.atlassian.confluence.api.model.Expansion;
import com.atlassian.confluence.api.model.content.AttachmentUpload;
import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.Label;
import com.atlassian.confluence.api.model.content.Space;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.people.Person;
import com.atlassian.confluence.api.service.exceptions.ServiceException;
import com.atlassian.confluence.rest.client.*;
import com.atlassian.confluence.rest.client.authentication.AuthenticatedWebResourceProvider;
import com.atlassian.fugue.Option;
import com.atlassian.util.concurrent.Promise;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Connection to Confluence
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class ConfluenceSession {

    private final AuthenticatedWebResourceProvider authenticatedWebResourceProvider;
    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(3));
    private final RemoteAttachmentServiceImpl attachmentService;
    private final RemoteContentLabelService remoteContentLabelService;
    private final RemoteContentPropertyService contentPropertyService;
    private final RemoteContentService contentService;
    private final RemoteChildContentService childContentService;
    private final RemoteCQLSearchService cqlSearchService;
    private final RemotePersonService remotePersonService;
    private final RemoteSpaceService spaceService;

    private Logger log = Logger.getLogger(ConfluenceSession.class.getName());
    private Label.Prefix labelPrefix = Label.Prefix.global;
    private static Expansion[] expansions = toExpansionsArray(
            Content.Expansions.ANCESTORS,
            Content.Expansions.BODY,
            Content.Expansions.CHILDREN,
            Content.Expansions.CONTAINER,
            Content.Expansions.DESCENDANTS,
            Content.Expansions.HISTORY,
            Content.Expansions.METADATA,
            Content.Expansions.OPERATIONS,
            Content.Expansions.PERMISSIONS,
            Content.Expansions.SPACE,
            Content.Expansions.STATUS,
            Content.Expansions.VERSION,
            Content.Expansions.RESTRICTIONS,
            "body.storage",
            "children.comment.body.storage",
            "children.comment.version",
            "metadata.labels",
            "metadata.currentuser",
            "metadata.properties",
            "metadata.labels",
            "history.lastUpdated",
            "history.previousVersion",
            "history.contributors",
            "history.nextVersion",
            "restrictions.read.restrictions.user",
            "restrictions.read.restrictions.group",
            "restrictions.update.restrictions.user",
            "restrictions.update.restrictions.group"
    );

    /**
     * Constructor
     *
     * @param authenticatedWebResourceProvider
     */
    ConfluenceSession(final AuthenticatedWebResourceProvider authenticatedWebResourceProvider) {
        this.authenticatedWebResourceProvider = authenticatedWebResourceProvider;
        this.attachmentService = new RemoteAttachmentServiceImpl(authenticatedWebResourceProvider, executor);
        this.remoteContentLabelService = new RemoteContentLabelServiceImpl(authenticatedWebResourceProvider, executor);
        this.contentPropertyService = new RemoteContentPropertyServiceImpl(authenticatedWebResourceProvider, executor);
        this.contentService = new RemoteContentServiceImpl(authenticatedWebResourceProvider, executor);
        this.childContentService = new RemoteChildContentServiceImpl(authenticatedWebResourceProvider, executor);
        this.cqlSearchService = new RemoteCQLSearchServiceImpl(authenticatedWebResourceProvider, executor);
        this.remotePersonService = new RemotePersonServiceImpl(authenticatedWebResourceProvider, executor);
        this.spaceService = new RemoteSpaceServiceImpl(authenticatedWebResourceProvider, executor);
    }

    /**
     * Get a Space by key name
     *
     * @param spaceKey
     * @return {@link Promise} instance
     */
    public Option<Space> getSpace(String spaceKey) throws ServiceException {
        return spaceService.find()
                .withKeys(spaceKey).fetchOne().claim();
    }

    /**
     * Get a PageContent by Page key
     * @param contentId
     * @return {@link Optional} instance
     */
    public Optional<Content> getContent(String contentId) throws ServiceException {
        return contentService.find(expansions)
                .withId(ContentId.of(Long.parseLong(contentId)))
                .fetch().claim();
    }

    /**
     * Get a Content by spaceKey and Page title
     * @param spaceKey
     * @param pageTitle
     * @return {@link Optional} instance
     */
    public Optional<Content> getContent(String spaceKey, String pageTitle, boolean expanded) throws ServiceException {
        return contentService.find(expanded ? expansions : null)
                .withSpace(getSpace(spaceKey).getOrNull())
                .withTitle(pageTitle)
                .fetch().claim();
    }

    public Content createContent(final Content content) throws ServiceException {
        return contentService.create(content).claim();
    }

    public Content updateContent(final Content content) throws ServiceException {
        return contentService.update(content).claim();
    }

    /**
     * Get all attachments for a page
     * @param pageId
     * @return List of {@link Content}
     * @throws ServiceException
     */
    @SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "false positive")
    public List<Content> getAttachments(long pageId) throws ServiceException {
        return attachmentService.find(toExpansionsArray("body.storage"))
                .withContainerId(ContentId.of(pageId))
                .fetchMany(null).claim().getResults();
    }

    /**
     * Attach the file
     *
     * @param pageId
     * @param file
     * @param contentType
     * @param comment
     * @return {@link PageResponse} instance that was created on the server
     */
    public PageResponse<Content> addAttachment(long pageId, VirtualFile file, String contentType,
                                               String comment) throws ServiceException {
        try {
            File uploadFile = File.createTempFile("conf-upload-", null);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(uploadFile.getAbsoluteFile().getPath()))) {
                try (InputStream inSt = file.open()) {
                    bos.write(IOUtils.toByteArray(inSt));
                    bos.flush();
                    AttachmentUpload attachment = new AttachmentUpload(uploadFile, file.getName(), contentType, comment, false);
                    return attachmentService
                            .addAttachments(ContentId.of(pageId), Collections.singletonList(attachment))
                            .claim();
                }
            } catch (IOException ie) {
                log.severe(ie.getMessage());
            } finally {
                uploadFile.delete();
                //UNSURE IF REQUIRED HERE
                uploadFile.deleteOnExit();
            }
        } catch (ServiceException se) {
            log.severe(se.getMessage());
            throw se;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Remove attachment
     *
     * @param attachment
     * @throws ServiceException
     */
    public void removeAttachment(Content attachment) throws ServiceException {
        attachmentService.delete(attachment).claim();
    }


    /**
     * Add's labels to content, removing duplicates.
     *
     * @param labels comma separated labels string.
     * @return boolean success status.
     * @throws ServiceException
     */
    public boolean addLabels(long id, String labels) throws ServiceException {
        List<String> stringLabels = Arrays.asList(labels.toLowerCase().split(","));

        Function<List<String>, List<Label>> CONVERT_TO_LABELS = (strings) -> strings.stream()
                .map(string -> new Label(Label.builder(string).prefix(labelPrefix)))
                .collect(Collectors.toList());

        BiFunction<List<Label>, List<Label>, List<Label>> FILTER_EXISTING_LABELS = (existingLabels, newLabels) -> {
            List<String> existingNames = existingLabels.stream()
                    .map(Label::getLabel).map(String::toLowerCase).collect(Collectors.toList());
            return newLabels.stream()
                    .filter((l) -> !existingNames.contains(l.getLabel().toLowerCase()))
                    .collect(Collectors.toList());
        };

        List<Label> newLabels = CONVERT_TO_LABELS.apply(stringLabels);
        List<Label> existingLabels = getLabels(id).getResults();
        newLabels = FILTER_EXISTING_LABELS.apply(existingLabels, newLabels);

        if (!newLabels.isEmpty()) {
            remoteContentLabelService.addLabels(ContentId.of(id), newLabels).claim();
        }

        List<String> remoteLabels = getLabels(id).getResults().stream()
                .map(Label::getLabel).collect(Collectors.toList());

        return remoteLabels.containsAll(stringLabels);

    }

    @SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "false positive")
    public PageResponse<Label> getLabels(long id) {
        return (PageResponse<Label>) remoteContentLabelService
                .getLabels(ContentId.of(id), Collections.singletonList(labelPrefix), null).claim();
    }

    public Person getCurrentUser() {
        return remotePersonService.getCurrentUser().claim();
    }

    public static Expansion[] toExpansionsArray(String... expansions) {
        return Collections2.transform(Arrays.asList(expansions), Expansion.AS_EXPANSION).toArray(new Expansion[expansions.length]);
    }
}

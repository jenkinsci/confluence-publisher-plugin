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
package com.myyearbook.hudson.plugins.confluence.wiki.generators;

import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.link.LinkType;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Abstract class representing a method of generating Confluence wiki markup.
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public abstract class MarkupGenerator implements Describable<MarkupGenerator>, ExtensionPoint {
    public MarkupGenerator() {
        super();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<MarkupGenerator> getDescriptor() {
        return Jenkins.get().getDescriptor(getClass());
    }

    /**
     * Returns all {@link MarkupGenerator} descriptors
     *
     * @return
     */
    public static DescriptorExtensionList<MarkupGenerator, Descriptor<MarkupGenerator>> all() {
        return Jenkins.get().getDescriptorList(MarkupGenerator.class);
    }

    /**
     * Generates markup to be used for replacement
     *
     * @param build
     * @param listener
     * @return
     */
    public abstract String generateMarkup(Run<?, ?> build, FilePath filePath, TaskListener listener, List<Content> remoteAttachments);

    /**
     * Expands replacement variables in the generated text
     *
     * @param build
     * @param listener
     * @param generated
     * @return
     */
    protected String expand(final Run<?, ?> build, final TaskListener listener,
            final String generated, List<Content> remoteAttachments) {
	//If expansion failed, just return the unexpanded text
	String result = generated;
        try {
		result = expandAttachmentsLink(listener, generated,remoteAttachments);
		result = build.getEnvironment(listener).expand(result);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(listener.getLogger());
        }

        return result;
    }
    /**
     * Expands replacement $LINK[n] variables with uploaded files links
     *
     * @param listener
     * @param generated
     * @param remoteAttachments
     * @return
     */
    protected String expandAttachmentsLink(final TaskListener listener, String generated, List<Content> remoteAttachments ){
	String result = generated;
	for (int i = 0; i < remoteAttachments.size(); i++) {
		Content attachment = remoteAttachments.get(i);
			try {
				String url = attachment.getLinks().get(LinkType.DOWNLOAD).getPath();
                String href = url.substring(url.indexOf(new URI(url).getPath()))
                        .replaceAll("&", "&amp;");
				result = result.replace("$LINK["+i+"]", href);
			} catch (URISyntaxException e) {
	            e.printStackTrace(listener.getLogger());
			}
		}
	return result;
    }
}

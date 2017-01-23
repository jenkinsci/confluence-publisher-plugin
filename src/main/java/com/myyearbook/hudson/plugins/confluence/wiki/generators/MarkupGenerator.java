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

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import jenkins.model.Jenkins;

import jenkins.plugins.confluence.soap.v1.RemoteAttachment;

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
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    /**
     * Returns all {@link MarkupGenerator} descriptors
     *
     * @return
     */
    public static DescriptorExtensionList<MarkupGenerator, Descriptor<MarkupGenerator>> all() {
        return Jenkins.getInstance()
                .<MarkupGenerator, Descriptor<MarkupGenerator>> getDescriptorList(
                        MarkupGenerator.class);
    }

    /**
     * Generates markup to be used for replacement
     *
     * @param build
     * @param listener
     * @return
     */
    public abstract String generateMarkup(AbstractBuild<?, ?> build, BuildListener listener, List<RemoteAttachment> remoteAttachments);

    /**
     * Expands replacement variables in the generated text
     *
     * @param build
     * @param listener
     * @param generated
     * @return
     */
    protected String expand(final AbstractBuild<?, ?> build, final BuildListener listener,
            final String generated, List<RemoteAttachment> remoteAttachments) {
	//If expansion failed, just return the unexpanded text
	String result = generated;
        try {
		result = expandAttachmentsLink(listener, generated,remoteAttachments);
		result = build.getEnvironment(listener).expand(result);
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
        } catch (InterruptedException e) {
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
    protected String expandAttachmentsLink(final BuildListener listener, String generated, List<RemoteAttachment> remoteAttachments ){
	String result = generated;
	for (int i = 0; i < remoteAttachments.size(); i++) {
		RemoteAttachment attachment = remoteAttachments.get(i);
			try {
				String url = attachment.getUrl();
				String href = url.substring(url.indexOf(new URI(url).getPath()));
				result = result.replace("$LINK["+i+"]", href);
			} catch (URISyntaxException e) {
	            e.printStackTrace(listener.getLogger());
			}
		}
	return result;
    }
}

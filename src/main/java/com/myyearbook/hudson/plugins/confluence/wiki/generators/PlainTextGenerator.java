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
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.List;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Content generator that takes plain text input from the Job configuration. Any build variables
 * will be replaced.
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class PlainTextGenerator extends MarkupGenerator {
    public final String text;

    @DataBoundConstructor
    public PlainTextGenerator(final String text) {
        this.text = text;
    }

    @Override
    public Descriptor<MarkupGenerator> getDescriptor() {
        return super.getDescriptor();
    }

    @Override
	public String generateMarkup(Run<?, ?> build, FilePath filePath,
			TaskListener listener, List<Content> remoteAttachments) {
	    return expand(build, listener, this.text, remoteAttachments);
    }

    @Extension
    @Symbol("confluenceText")
    public static class DescriptorImpl extends Descriptor<MarkupGenerator> {
        @Override
        public String getDisplayName() {
            return "Plain text";
        }
    }
}

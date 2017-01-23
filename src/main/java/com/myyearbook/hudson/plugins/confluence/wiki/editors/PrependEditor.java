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
package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.Extension;
import hudson.model.BuildListener;

import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

/**
 * Represents a simple Wiki markup editor that prepends the content to the beginning of the page.
 * This editor requires no replacement tokens.
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class PrependEditor extends MarkupEditor {

    @DataBoundConstructor
    public PrependEditor(MarkupGenerator generator) {
        super(generator);
    }

    @Override
    public String performEdits(final BuildListener listener, final String content,
            final String generated, final boolean isNewFormat) {
        final StringBuilder sb = new StringBuilder(content);

        // Prepend the generated content to the beginning of the page
        if (isNewFormat) {
            // New format: wrap the new content with <p />
            sb.insert(0, generated);
        } else {
            // Insert newline at the beginning, generated content before that
            sb.insert(0, '\n').insert(0, generated);
        }
        return sb.toString();
    }

    @Extension
    public static final class DescriptorImpl extends MarkupEditorDescriptor {
        @Override
        public String getDisplayName() {
            return "Prepend content";
        }
    }
}

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
import hudson.Util;
import hudson.model.BuildListener;

import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

/**
 * Represents a token-based Wiki markup editor that inserts the new content immediately before the
 * replacement marker token.
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class BeforeTokenEditor extends MarkupEditor {
    public final String markerToken;

    @DataBoundConstructor
    public BeforeTokenEditor(final MarkupGenerator generator, final String markerToken) {
        super(generator);

        this.markerToken = unquoteToken(Util.fixEmptyAndTrim(markerToken));
    }

    @Override
    public String performEdits(final BuildListener listener, final String content,
            final String generated, final boolean isNewFormat) throws TokenNotFoundException {
        final StringBuffer sb = new StringBuffer(content);

        final int start = content.indexOf(markerToken);

        if (start < 0) {
            throw new TokenNotFoundException(
                    "Marker token could not be located in the page content: " + markerToken);
        }

        // Insert the newline at {start} first, and then {generated}
        // (the newline will appear after {generated})

        if (isNewFormat) {
            sb.insert(start, generated);
        } else {
            sb.insert(start, '\n').insert(start, generated);
        }
        return sb.toString();
    }

    @Extension
    public static final class DescriptorImpl extends MarkupEditorDescriptor {
        @Override
        public String getDisplayName() {
            return "Insert content before token";
        }
    }
}

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
import hudson.model.TaskListener;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

/**
 * Represents a token-based Wiki markup editor that inserts the new content between two (start/end)
 * replacement marker tokens.
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class BetweenTokensEditor extends MarkupEditor {
    public final String startMarkerToken;
    public final String endMarkerToken;

    @DataBoundConstructor
    public BetweenTokensEditor(final MarkupGenerator generator, final String startMarkerToken,
            final String endMarkerToken) {
        super(generator);
        this.startMarkerToken = unquoteToken(Util.fixEmptyAndTrim(startMarkerToken));
        this.endMarkerToken = unquoteToken(Util.fixEmptyAndTrim(endMarkerToken));
    }

    /**
     * Inserts the generated content in the section between the {@link #startMarkerToken} and
     * {@link #endMarkerToken}.
     *
     * @param listener
     * @param content
     * @param generated
     * @throws TokenNotFoundException
     */
    @Override
    public String performEdits(final TaskListener listener, final String content,
            final String generated, final boolean isNewFormat) throws TokenNotFoundException {
        final StringBuilder sb = new StringBuilder(content);

        final int markerStart = content.indexOf(startMarkerToken);
        final int contentStart = markerStart + startMarkerToken.length();

        if (markerStart < 0) {
            throw new TokenNotFoundException(
                    "Start-marker token could not be found in the page content: "
                            + startMarkerToken);
        }

        final int end = content.indexOf(endMarkerToken, contentStart);

        if (end < 0) {
            throw new TokenNotFoundException(
                    "End-marker token could not be found after the start-marker token: " + endMarkerToken);
        }

        // Remove the entire marked section (exclusive)
        sb.delete(contentStart, end);

        // Then insert the new content:
        if (isNewFormat) {
            sb.insert(contentStart, generated);
        } else {
            // Surround in newlines
            sb.insert(contentStart, '\n').insert(contentStart, generated).insert(contentStart, '\n');
        }
        return sb.toString();
    }

    @Extension
    @Symbol("confluenceBetweenTokens")
    public static final class DescriptorImpl extends MarkupEditorDescriptor {
        @Override
        public String getDisplayName() {
            return "Replace content between start/end tokens";
        }
    }
}

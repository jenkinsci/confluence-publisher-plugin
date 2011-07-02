
package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.Extension;
import hudson.model.BuildListener;

import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

/**
 * Represents a token-based Wiki markup editor that inserts the new content
 * between two (start/end) replacement marker tokens.
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public class BetweenTokensEditor extends MarkupEditor {
    public final String startMarkerToken;
    public final String endMarkerToken;

    @DataBoundConstructor
    public BetweenTokensEditor(final MarkupGenerator generator, final String startMarkerToken,
            final String endMarkerToken) {
        super(generator);
        this.startMarkerToken = hudson.Util.fixNull(startMarkerToken);
        this.endMarkerToken = hudson.Util.fixNull(endMarkerToken);
    }

    /**
     * Inserts the generated content in the section between the
     * {@link #startMarkerToken} and {@link #endMarkerToken}.
     * 
     * @param listener
     * @param content
     * @param generated
     * @throws TokenNotFoundException
     */
    @Override
    public String performEdits(BuildListener listener, String content, String generated)
            throws TokenNotFoundException {
        final StringBuffer sb = new StringBuffer(content);

        final int start = content.indexOf(startMarkerToken) + startMarkerToken.length();
        final int end = content.indexOf(endMarkerToken);

        if (start < 0) {
            throw new TokenNotFoundException(
                    "Start-marker token could not be found in the page content: "
                            + startMarkerToken);
        } else if (end < 0) {
            throw new TokenNotFoundException(
                    "End-marker token could not be found in the page content: " + endMarkerToken);
        }

        // Remove the entire marked section (exclusive)
        sb.delete(start, end);

        // Then insert the new content:
        sb.insert(start, '\n');
        sb.insert(start, generated);
        sb.insert(start, '\n');
        return sb.toString();
    }

    @Extension
    public static final class DescriptorImpl extends MarkupEditorDescriptor {
        @Override
        public String getDisplayName() {
            return "Replace content between start/end tokens";
        }
    }
}

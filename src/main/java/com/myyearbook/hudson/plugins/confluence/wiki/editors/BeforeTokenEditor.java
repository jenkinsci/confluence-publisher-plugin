
package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.Extension;
import hudson.model.BuildListener;

import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

/**
 * Represents a token-based Wiki markup editor that inserts the new content
 * immediately before the replacement marker token.
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public class BeforeTokenEditor extends MarkupEditor {
    public final String markerToken;

    @DataBoundConstructor
    public BeforeTokenEditor(final MarkupGenerator generator, final String markerToken) {
        super(generator);

        this.markerToken = markerToken;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public String performEdits(BuildListener listener, String content, String generated)
            throws TokenNotFoundException {
        final StringBuffer sb = new StringBuffer(content);

        final int start = content.indexOf(markerToken);

        if (start < 0) {
            throw new TokenNotFoundException(
                    "Marker token could not be located in the page content: " + markerToken);
        }

        // Insert the newline at {start} first, and then {generated}
        // (the newline will appear after {generated})
        sb.insert(start, '\n').insert(start, generated);
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

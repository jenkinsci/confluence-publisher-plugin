
package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.Extension;
import hudson.model.BuildListener;

import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

/**
 * Represents a simple Wiki markup editor that prepends the content to the
 * beginning of the page. This editor requires no replacement tokens.
 *
 * @author Joe Hansche <jhansche@myyearbook.com>
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


package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import org.kohsuke.stapler.DataBoundConstructor;

import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Base markup editor class
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public abstract class MarkupEditor implements Describable<MarkupEditor>, ExtensionPoint {
    /**
     * Markup generator
     */
    public MarkupGenerator generator;

    /**
     * Creates a generic markup editor
     * 
     * @param generator Markup generator
     */
    @DataBoundConstructor
    public MarkupEditor(final MarkupGenerator generator) {
        this.generator = generator;
    }

    /**
     * Returns the descriptor for this class
     * 
     * @return Descriptor
     */
    @SuppressWarnings("unchecked")
    public Descriptor<MarkupEditor> getDescriptor() {
        return (Descriptor<MarkupEditor>) Hudson.getInstance().getDescriptor(getClass());
    }

    /**
     * Returns list descriptors for all MarkupEditor implementations.
     * 
     * @return List of descriptors
     */
    public static DescriptorExtensionList<MarkupEditor, Descriptor<MarkupEditor>> all() {
        return Hudson.getInstance().<MarkupEditor, Descriptor<MarkupEditor>> getDescriptorList(
                MarkupEditor.class);
    }

    /**
     * Perform modifications to the page content. Default implementation makes
     * no modifications.
     * 
     * @param build
     * @param listener
     * @param content
     * @return
     * @throws TokenNotFoundException
     */
    public final String performReplacement(final AbstractBuild<?, ?> build,
            final BuildListener listener, final String content) throws TokenNotFoundException {
        // Generate the new content
        final String generated = generator.generateMarkup(build, listener);

        // Perform the edit
        return this.performEdits(listener, content, generated);
    }

    /**
     * Modify the page markup with the given generated content.
     * 
     * @param listener
     * @param content
     * @param generated
     * @return
     * @throws TokenNotFoundException
     */
    protected abstract String performEdits(final BuildListener listener, final String content,
            final String generated) throws TokenNotFoundException;

    /**
     * Descriptor for markup generators
     * 
     * @author Joe Hansche <jhansche@myyearbook.com>
     */
    public static abstract class MarkupEditorDescriptor extends Descriptor<MarkupEditor> {
        /**
         * Returns all available MarkupGenerator implementations
         * 
         * @return List of MakrupGenerator Descriptors
         */
        public final List<Descriptor<MarkupGenerator>> getGenerators() {
            final List<Descriptor<MarkupGenerator>> generators = new ArrayList<Descriptor<MarkupGenerator>>();

            for (Descriptor<MarkupGenerator> generator : MarkupGenerator.all()) {
                generators.add(generator);
            }

            return generators;
        }
    }

    /**
     * Exception thrown when the configured token cannot be found in the wiki
     * markup.
     * 
     * @author Joe Hansche <jhansche@myyearbook.com>
     */
    public static class TokenNotFoundException extends Exception {
        public TokenNotFoundException(final String message) {
            super(message);
        }

        public TokenNotFoundException(final String message, final Throwable cause) {
            super(message, cause);
        }

        private static final long serialVersionUID = -5759944314599051961L;
    }
}

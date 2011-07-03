
package com.myyearbook.hudson.plugins.confluence.wiki.generators;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.io.IOException;

/**
 * Abstract class representing a method of generating Confluence wiki markup.
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public abstract class MarkupGenerator implements Describable<MarkupGenerator>, ExtensionPoint {
    public MarkupGenerator() {
        super();
    }

    @SuppressWarnings("unchecked")
    public Descriptor<MarkupGenerator> getDescriptor() {
        return (Descriptor<MarkupGenerator>) Hudson.getInstance().getDescriptor(getClass());
    }

    /**
     * Returns all {@link MarkupGenerator} descriptors
     * 
     * @return
     */
    public static DescriptorExtensionList<MarkupGenerator, Descriptor<MarkupGenerator>> all() {
        return Hudson.getInstance().<MarkupGenerator, Descriptor<MarkupGenerator>>
                getDescriptorList(MarkupGenerator.class);
    }

    /**
     * Generates markup to be used for replacement
     * 
     * @param build
     * @param listener
     * @return
     */
    public abstract String generateMarkup(AbstractBuild<?, ?> build, BuildListener listener);

    /**
     * Expands replacement variables in the generated text
     * 
     * @param build
     * @param listener
     * @param generated
     * @return
     */
    protected String expand(final AbstractBuild<?, ?> build, final BuildListener listener,
            final String generated) {
        try {
            return build.getEnvironment(listener).expand(generated);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // The expansion failed, so just return the unexpanded text
        return generated;
    }
}

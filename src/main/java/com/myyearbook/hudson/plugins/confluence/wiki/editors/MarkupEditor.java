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

import jenkins.plugins.confluence.soap.v1.RemoteAttachment;

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
     * Perform modifications to the page content. Default implementation makes no modifications.
     *
     * @param build
     * @param listener
     * @param content
     * @param isNewFormat
     * @return
     * @throws TokenNotFoundException
     */
    public final String performReplacement(final AbstractBuild<?, ?> build,
            final BuildListener listener, final String content, boolean isNewFormat, List<RemoteAttachment> remoteAttachments)
            throws TokenNotFoundException {
        final String generated = generator.generateMarkup(build, listener, remoteAttachments);

        // Perform the edit
        return this.performEdits(listener, content, generated, isNewFormat);
    }

    /**
     * Log helper
     *
     * @param listener
     * @param message
     */
    protected void log(BuildListener listener, String message) {
        listener.getLogger().println("[confluence] " + message);
    }

    /**
     * Stapler seems to wrap {..} values in double quotes, which breaks marker token searching. This
     * will strip the double quotes from those strings.
     *
     * @param token
     * @return token with wrapping double quotes stripped
     */
    protected final String unquoteToken(final String token) {
        if (token == null) {
            return null;
        }

        if (token.startsWith("\"{") && token.endsWith("}\"")) {
            return token.substring(1, token.length() - 1);
        }

        return token;
    }

    /**
     * Modify the page markup with the given generated content.
     *
     * @param listener
     * @param content
     * @param generated
     * @param isNewFormat
     * @return
     * @throws TokenNotFoundException
     */
    protected abstract String performEdits(final BuildListener listener, final String content,
            final String generated, final boolean isNewFormat) throws TokenNotFoundException;

    /**
     * Returns the descriptor for this class
     *
     * @return Descriptor
     */
    @SuppressWarnings("unchecked")
    public Descriptor<MarkupEditor> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
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
     * Exception thrown when the configured token cannot be found in the wiki markup.
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

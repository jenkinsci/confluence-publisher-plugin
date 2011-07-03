
package com.myyearbook.hudson.plugins.confluence.wiki.generators;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * Content generator that reads the markup from a configured workspace file.
 * Build variables will be replaced.
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public class FileGenerator extends MarkupGenerator {
    @Exported
    public final String filename;

    @DataBoundConstructor
    public FileGenerator(final String filename) {
        this.filename = Util.fixEmptyAndTrim(filename);
    }

    @Override
    public Descriptor<MarkupGenerator> getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public String generateMarkup(AbstractBuild<?, ?> build, BuildListener listener) {
        if (this.filename == null) {
            listener.getLogger().println(
                    "[confluence] No file is configured, generating empty markup.");
            return "";
        }

        FilePath markupFile = build.getWorkspace().child(this.filename);

        try {
            if (markupFile.exists()) {
                // Read the file and use its contents
                return expand(build, listener, markupFile.readToString());
            } else {
                listener.getLogger().println(
                        "[confluence] Markup file (" + markupFile.getName() + ") does not exist.");
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("[confluence] Error reading input file "
                    + this.filename));
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error("[confluence] Error reading input file "
                    + this.filename));
        }

        return "";
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MarkupGenerator> {
        @Override
        public String getDisplayName() {
            return "File contents";
        }
    }
}

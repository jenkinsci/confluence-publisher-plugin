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
package com.myyearbook.hudson.plugins.confluence.wiki.generators;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.List;

import jenkins.plugins.confluence.soap.v1.RemoteAttachment;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

/**
 * Content generator that reads the markup from a configured workspace file. Build variables will be
 * replaced.
 *
 * @author Joe Hansche jhansche@myyearbook.com
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
        return super.getDescriptor();
    }

    @Override
	public String generateMarkup(Run<?, ?> build, FilePath filePath,
			TaskListener listener, List<RemoteAttachment> remoteAttachments) {
        if (this.filename == null) {
            listener.getLogger().println(
                    "[confluence] No file is configured, generating empty markup.");
            return "";
        }

        FilePath markupFile = filePath.child(this.filename);

        try {
            if (!markupFile.exists()) {
                listener.getLogger().println(
                        "[confluence] Markup file (" + markupFile.getName() + ") does not exist.");
            } else {
                // Read the file and use its contents
                return expand(build, listener, markupFile.readToString(), remoteAttachments);
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
    @Symbol("confluenceFile")
    public static class DescriptorImpl extends Descriptor<MarkupGenerator> {
        @Override
        public String getDisplayName() {
            return "File contents";
        }
    }
}

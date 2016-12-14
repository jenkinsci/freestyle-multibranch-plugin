/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.cloudbees.jenkins.multibranch.freestyle;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class MarkerFreeStyleSCMSourceCriteria extends FreeStyleSCMSourceCriteria {
    private final String fileName;

    @DataBoundConstructor
    public MarkerFreeStyleSCMSourceCriteria(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
        if (fileName == null) {
            return true;
        }
        listener.getLogger().format("Checking for %s%n", fileName);
        return probe.stat(fileName).exists();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MarkerFreeStyleSCMSourceCriteria that = (MarkerFreeStyleSCMSourceCriteria) o;

        return StringUtils.equals(fileName, that.fileName);
    }

    @Override
    public int hashCode() {
        return fileName.hashCode();
    }

    @Extension
    public static class DescriptorImpl extends FreeStyleSCMSourceCriteriaDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.MarkerFreeStyleSCMSourceCriteria_DisplayName();
        }
    }
}

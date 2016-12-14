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
import hudson.ExtensionList;
import hudson.model.ItemGroup;
import hudson.model.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.MultiBranchProjectFactoryDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class FreeStyleMultiBranchProjectFactory extends MultiBranchProjectFactory.BySCMSourceCriteria {
    private FreeStyleSCMSourceCriteria scmSourceCriteria = new AllFreeStyleSCMSourceCriteria();
    private final BranchProjectFactory<FreeStyleMultiBranchProject.ProjectImpl, FreeStyleMultiBranchProject.BuildImpl> factory;


    @DataBoundConstructor
    public FreeStyleMultiBranchProjectFactory(
            BranchProjectFactory<FreeStyleMultiBranchProject.ProjectImpl, FreeStyleMultiBranchProject.BuildImpl> factory) {
        this.factory = factory;
    }

    public FreeStyleSCMSourceCriteria getScmSourceCriteria() {
        return scmSourceCriteria;
    }

    @DataBoundSetter
    public void setScmSourceCriteria(FreeStyleSCMSourceCriteria scmSourceCriteria) {
        this.scmSourceCriteria = scmSourceCriteria;
    }

    @NonNull
    @Override
    protected SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
        return scmSourceCriteria;
    }

    public BranchProjectFactory<FreeStyleMultiBranchProject.ProjectImpl, FreeStyleMultiBranchProject.BuildImpl> getFactory() {
        return factory;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    protected MultiBranchProject<?, ?> doCreateProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                       @NonNull Map<String, Object> attributes) {
        FreeStyleMultiBranchProject result = new FreeStyleMultiBranchProject(parent, name);
        // NOTE: we need to clone the factory so that each child project can set their factory's owner correctly
        result.setProjectFactory((BranchProjectFactory) Items.XSTREAM.fromXML(Items.XSTREAM.toXML(this.factory)));
        return result;
    }

    @Extension
    public static class DescriptorImpl extends MultiBranchProjectFactoryDescriptor {

        @Override
        public MultiBranchProjectFactory newInstance() {
            return new FreeStyleMultiBranchProjectFactory(new FreeStyleProjectFactory());
        }

        @Override
        public String getDisplayName() {
            return "Freestyle Multibranch";
        }

    }

}

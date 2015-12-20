/*
 * The MIT License
 *
 * Copyright (c) 2011-2014, CloudBees, Inc., Stephen Connolly.
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
 */

package org.cloudbees.jenkins.multibranch.freestyle;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JobProperty;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.security.Permission;
import hudson.slaves.WorkspaceList;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jenkins.branch.BranchProperty;
import jenkins.scm.api.SCMHead;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A multi-branch project that emulates a {@link hudson.model.FreeStyleProject}
 */
public class FreeStyleMultibranchProject extends
        MultiBranchProject<FreeStyleMultibranchProject.ProjectImpl, FreeStyleMultibranchProject.BuildImpl> {
    /**
     * Our constructor
     *
     * @param parent the parent of this project.
     * @param name   the name of this project.
     */
    public FreeStyleMultibranchProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected BranchProjectFactory<ProjectImpl, BuildImpl> newProjectFactory() {
        return new ProjectFactoryImpl();
    }

    /**
     * Provides a URL path which actually points back to {@code this} but interjects some {@link ProjectImpl} in the ancestor path for the benefit of build step descriptors.
     */
    @Restricted(NoExternalUse.class) // for ProjectFactoryImpl/config.jelly
    public String getDescriptorByNameUrlSuffix() {
        Collection<ProjectImpl> projects = getItems();
        if (projects.isEmpty()) {
            return "dummyBranch/parent";
        } else {
            String chosen = projects.iterator().next().getName(); // fallback
            for (ProjectImpl p : projects) {
                String n = p.getName();
                // TODO would appreciate an API in SCMSource to find the main branch
                if (/* Git */ n.equals("master") || /* SVN?? */ n.equals("trunk") || /* Hg */n.equals("default")) {
                    chosen = n;
                    break;
                }
            }
            return getUrlChildPrefix() + "/" + chosen + "/parent";
        }
    }
    @Restricted(NoExternalUse.class) // for Ancestor binding only
    public ProjectImpl getDummyBranch() {
        return new ProjectImpl(this);
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends MultiBranchProjectDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.FreeStyleMultibranchProject_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new FreeStyleMultibranchProject(parent, name);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public List<SCMDescriptor<?>> getSCMDescriptors() {
            List<SCMDescriptor<?>> result = new ArrayList<SCMDescriptor<?>>(SCM.all());
            for (Iterator<SCMDescriptor<?>> iterator = result.iterator(); iterator.hasNext(); ) {
                SCMDescriptor<?> d = iterator.next();
                if (NullSCM.class.equals(d.clazz)) {
                    iterator.remove();
                }
            }
            return result; // todo figure out filtering
        }
    }

    /**
     * The per-branch jobs
     */
    public static class ProjectImpl extends Project<ProjectImpl, BuildImpl> implements TopLevelItem {

        /**
         * HACK to remove the ability to configure from the inherited jelly files
         */
        @SuppressWarnings("unused") // used by stapler
        public static final Permission CONFIGURE = null;

        /**
         * Our branch.
         */
        private Branch branch;

        /**
         * Prevent default constructor.
         */
        private ProjectImpl(FreeStyleMultibranchProject parent) {
            super(parent, "DUMMY");
            branch = new Branch("DUMMY", new SCMHead("DUMMY"), new NullSCM(), Collections.<BranchProperty>emptyList());
        }

        /**
         * Constructor that allows us to initialize the configuration.
         *
         * @param parent        the parent.
         * @param branch        the branch.
         * @param properties    the job properties.
         * @param buildWrappers the build wrappers.
         * @param builders      the builders.
         * @param publishers    the publishers.
         */
        public ProjectImpl(FreeStyleMultibranchProject parent, Branch branch,
                           List<JobProperty<? super ProjectImpl>> properties,
                           Map<Descriptor<BuildWrapper>, BuildWrapper> buildWrappers,
                           DescribableList<Builder, Descriptor<Builder>> builders,
                           Map<Descriptor<Publisher>, Publisher> publishers) {
            super(parent, branch.getEncodedName());
            this.branch = branch;
            this.properties.replaceBy(properties);

            // and now for the ugly hack!!!

            // we don't want to trigger a save, so set a dummy owner, modify the list, and restore us as owner
            this.getBuildWrappersList().setOwner(Saveable.NOOP);
            try {
                this.getBuildWrappersList().replaceBy(buildWrappers.values());
            } catch (IOException e) {
                // ignore, should never happen as owner is Saveable.NOOP
            } finally {
                this.getBuildWrappersList().setOwner(this);
            }
            this.getBuildersList().setOwner(Saveable.NOOP);
            try {
                this.getBuildersList().replaceBy(builders.toList());
            } catch (IOException e) {
                // ignore, should never happen as owner is Saveable.NOOP
            } finally {
                this.getBuildersList().setOwner(this);
            }
            this.getPublishersList().setOwner(Saveable.NOOP);
            try {
                this.getPublishersList().replaceBy(publishers.values());
            } catch (IOException e) {
                // ignore, should never happen as owner is Saveable.NOOP
            } finally {
                this.getPublishersList().setOwner(this);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ProjectImpl asProject() {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public FreeStyleMultibranchProject getParent() {
            return (FreeStyleMultibranchProject) super.getParent();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getName() {
            return branch.getEncodedName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return branch.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCM getScm() {
            return branch.getScm();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isBuildable() {
            return super.isBuildable() && branch.isBuildable();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isNameEditable() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<BuildImpl> getBuildClass() {
            return BuildImpl.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        // TODO - Hack - child items of an item group that is a view container must implement TopLevelItem
        public TopLevelItemDescriptor getDescriptor() {
            return (TopLevelItemDescriptor) Jenkins.getInstance().getDescriptorOrDie(ProjectImpl.class);
        }

        /**
         * Our descriptor.
         */
        // TODO - Hack - child items of an item group that is a view container must implement TopLevelItem
        @Extension
        public static class DescriptorImpl extends AbstractProjectDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                throw new UnsupportedOperationException();
            }

            /**
             * Remove the descriptor from the top level items.
             */
            // TODO - Hack - child items of an item group that is a view container must to implement TopLevelItem
            @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
            @SuppressWarnings("unused") // invoked by Jenkins
            public static void postInitialize() throws Exception {
                final DescriptorExtensionList<TopLevelItem, TopLevelItemDescriptor> all = Items.all();
                all.remove(all.get(DescriptorImpl.class));
            }
        }
    }

    /**
     * A build of a specific branch.
     */
    public static class BuildImpl extends Build<ProjectImpl, BuildImpl> {

        /**
         * {@inheritDoc}
         */
        public BuildImpl(ProjectImpl project) throws IOException {
            super(project);
        }

        /**
         * {@inheritDoc}
         */
        public BuildImpl(ProjectImpl project, File buildDir) throws IOException {
            super(project, buildDir);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Runner createRunner() {
            return new RunnerImpl();
        }

        /**
         * Our runner.
         */
        protected class RunnerImpl extends Build<ProjectImpl, BuildImpl>.RunnerImpl {

            /**
             * {@inheritDoc}
             */
            @Override
            protected WorkspaceList.Lease decideWorkspace(Node n, WorkspaceList wsl)
                    throws InterruptedException, IOException {
                // TODO: this cast is indicative of abstraction problem
                final ProjectImpl project = (ProjectImpl) getProject();
                return wsl.allocate(n.getWorkspaceFor(project.getParent()).child(project.getName()));
            }

        }

    }

    /**
     * The factory that creates the per-branch projects.
     */
    public static class ProjectFactoryImpl extends BranchProjectFactory<ProjectImpl, BuildImpl> {

        /**
         * Hack to let us get some descriptors.
         */
        private static final ProjectImpl DUMMY = new ProjectImpl(null);

        /**
         * List of active {@link Builder}s configured for this project.
         */
        private final DescribableList<Builder, Descriptor<Builder>> builders =
                new DescribableList<Builder, Descriptor<Builder>>(this);

        /**
         * List of active {@link Publisher}s configured for this project.
         */
        private final DescribableList<Publisher, Descriptor<Publisher>> publishers =
                new DescribableList<Publisher, Descriptor<Publisher>>(this);

        /**
         * List of active {@link BuildWrapper}s configured for this project.
         */
        private final DescribableList<BuildWrapper, Descriptor<BuildWrapper>> buildWrappers =
                new DescribableList<BuildWrapper, Descriptor<BuildWrapper>>(this);

        /**
         * Our constructor.
         */
        public ProjectFactoryImpl() {
        }

        /**
         * Stapler's constructor.
         */
        @SuppressWarnings("unused") // used by stapler
        @DataBoundConstructor
        public ProjectFactoryImpl(List<Builder> builders, List<BuildWrapper> buildWrappers, List<Publisher> publishers)
                throws IOException {
            if (builders != null) {
                this.builders.replaceBy(builders);
            }
            if (buildWrappers != null) {
                this.buildWrappers.replaceBy(buildWrappers);
            }
            if (publishers != null) {
                this.publishers.replaceBy(publishers);
            }
        }

        /**
         * Accessor for stapler.
         */
        @SuppressWarnings("unused") // used by stapler
        public DescribableList<Builder, Descriptor<Builder>> getBuilders() {
            return builders;
        }

        /**
         * Accessor for stapler.
         */
        @SuppressWarnings("unused") // used by stapler
        public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappers() {
            return buildWrappers;
        }

        /**
         * Accessor for stapler.
         */
        @SuppressWarnings("unused") // used by stapler
        public DescribableList<Publisher, Descriptor<Publisher>> getPublishers() {
            return publishers;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ProjectImpl newInstance(Branch branch) {
            return new ProjectImpl((FreeStyleMultibranchProject) getOwner(),
                    branch,
                    Collections.<JobProperty<? super ProjectImpl>>emptyList(),
                    Descriptor.toMap(buildWrappers),
                    builders,
                    Descriptor.toMap(publishers));
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public ProjectImpl setBranch(
                @NonNull ProjectImpl project, @NonNull Branch branch) {
            if (!project.branch.equals(branch)) {
                project.branch = branch;
                try {
                    project.save();
                } catch (IOException e) {
                    // TODO log
                }
            } else {
                project.branch = branch;
            }
            return project;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isProject(Item item) {
            return item instanceof ProjectImpl;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Branch getBranch(@NonNull ProjectImpl project) {
            return project.branch;
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends BranchProjectFactoryDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Fixed configuration";
            }

            /**
             * Accessor for stapler.
             */
            @SuppressWarnings("unused") // used by stapler
            public static List<Descriptor<BuildWrapper>> getBuildWrapperDescriptors() {
                return BuildWrappers.getFor(DUMMY);
            }

            /**
             * Accessor for stapler.
             */
            @SuppressWarnings("unused") // used by stapler
            public static List<Descriptor<Builder>> getBuilderDescriptors() {
                return BuildStepDescriptor.filter(Builder.all(), ProjectImpl.class);
            }

            /**
             * Accessor for stapler.
             */
            @SuppressWarnings("unused") // used by stapler
            public static List<Descriptor<Publisher>> getPublisherDescriptors() {
                return BuildStepDescriptor.filter(Publisher.all(), ProjectImpl.class);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
                return FreeStyleMultibranchProject.class.isAssignableFrom(clazz);
            }
        }
    }

    /**
     * Provide a nicer config.xml for jobs.
     */
    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    @SuppressWarnings("unused") // invoked by Jenkins
    public static void registerXStream() {
        Items.XSTREAM.alias("freestyle-multibranch", FreeStyleMultibranchProject.class);
        Items.XSTREAM.alias("freestyle-branch", ProjectImpl.class);
    }
}

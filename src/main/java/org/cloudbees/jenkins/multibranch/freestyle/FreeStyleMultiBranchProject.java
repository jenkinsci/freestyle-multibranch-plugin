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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Build;
import hudson.model.Descriptor;
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
import hudson.security.Permission;
import hudson.slaves.WorkspaceList;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import javax.servlet.ServletException;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import net.sf.json.JSONObject;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.branch.BranchProperty;
import jenkins.scm.api.SCMHead;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A multi-branch project that emulates a {@link hudson.model.FreeStyleProject}
 */
public class FreeStyleMultiBranchProject extends
        MultiBranchProject<FreeStyleMultiBranchProject.ProjectImpl, FreeStyleMultiBranchProject.BuildImpl> {
    private FreeStyleSCMSourceCriteria scmSourceCriteria = new AllFreeStyleSCMSourceCriteria();

    /**
     * Our constructor
     *
     * @param parent the parent of this project.
     * @param name   the name of this project.
     */
    public FreeStyleMultiBranchProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
        return scmSourceCriteria instanceof AllFreeStyleSCMSourceCriteria ? null : scmSourceCriteria;
    }

    @NonNull
    public FreeStyleSCMSourceCriteria getScmSourceCriteria() {
        return scmSourceCriteria == null ? new AllFreeStyleSCMSourceCriteria() : scmSourceCriteria;
    }

    public void setScmSourceCriteria(FreeStyleSCMSourceCriteria scmSourceCriteria) {
        if (this.scmSourceCriteria == null ? scmSourceCriteria != null : !this.scmSourceCriteria.equals(scmSourceCriteria)) {
            this.scmSourceCriteria = scmSourceCriteria;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected BranchProjectFactory<ProjectImpl, BuildImpl> newProjectFactory() {
        return new FreeStyleProjectFactory();
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

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        JSONObject json = req.getSubmittedForm();
        if (json.has("scmSourceCriteria")) {
            FreeStyleSCMSourceCriteria scmSourceCriteria =
                    req.bindJSON(FreeStyleSCMSourceCriteria.class, json.getJSONObject("scmSourceCriteria"));
            if (this.scmSourceCriteria == null
                    ? scmSourceCriteria != null
                    : !this.scmSourceCriteria.equals(scmSourceCriteria)) {
                this.scmSourceCriteria = scmSourceCriteria;
                recalculateAfterSubmitted(true);
            }
        }
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
            return Messages.FreeStyleMultiBranchProject_DisplayName();
        }

        public String getDescription() {
            return Messages.FreeStyleMultiBranchProject_Description();
        }


        public String getIconFilePathPattern() {
            return "plugin/freestyle-multibranch/images/:size/freestyle-multibranch.png";
        }

        @Override
        public String getIconClassName() {
            return "icon-freestyle-multibranch-project";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new FreeStyleMultiBranchProject(parent, name);
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-freestyle-multibranch-project icon-sm",
                            "plugin/freestyle-multibranch/images/16x16/freestyle-multibranch.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-freestyle-multibranch-project icon-md",
                            "plugin/freestyle-multibranch/images/24x24/freestyle-multibranch.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-freestyle-multibranch-project icon-lg",
                            "plugin/freestyle-multibranch/images/32x32/freestyle-multibranch.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-freestyle-multibranch-project icon-xlg",
                            "plugin/freestyle-multibranch/images/48x48/freestyle-multibranch.png",
                            Icon.ICON_XLARGE_STYLE));
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
        /*package*/ ProjectImpl(ComputedFolder<?> parent) {
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
        public ProjectImpl(FreeStyleMultiBranchProject parent, Branch branch,
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
        public FreeStyleMultiBranchProject getParent() {
            return (FreeStyleMultiBranchProject) super.getParent();
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

        public Branch getBranch() {
            return branch;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        // TODO - Hack - child items of an item group that is a view container must implement TopLevelItem
        public TopLevelItemDescriptor getDescriptor() {
            return (TopLevelItemDescriptor) Jenkins.getActiveInstance().getDescriptorOrDie(ProjectImpl.class);
        }

        public void setBranch(Branch branch) {
            this.branch = branch;
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
                final ProjectImpl project = getProject();

                FilePath parentWorkspace = n.getWorkspaceFor(project.getParent());
                if (parentWorkspace == null) {
                    throw new IllegalStateException("node " + n.getNodeName() + "is no longer connected");
                }

                return wsl.allocate(parentWorkspace.child(project.getName()));
            }

        }

    }

    /**
     * Provide a nicer config.xml for jobs.
     */
    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    @SuppressWarnings("unused") // invoked by Jenkins
    public static void registerXStream() {
        Items.XSTREAM.alias("freestyle-multibranch", FreeStyleMultiBranchProject.class);
        Items.XSTREAM.alias("freestyle-branch", ProjectImpl.class);
    }
}

package org.cloudbees.jenkins.multibranch.freestyle;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The factory that creates the per-branch projects.
 */
public class FreeStyleProjectFactory
        extends BranchProjectFactory<FreeStyleMultiBranchProject.ProjectImpl, FreeStyleMultiBranchProject.BuildImpl> {

    /**
     * Hack to let us get some descriptors.
     */
    private static final FreeStyleMultiBranchProject.ProjectImpl DUMMY = new FreeStyleMultiBranchProject.ProjectImpl(null);

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
    public FreeStyleProjectFactory() {
    }

    /**
     * Stapler's constructor.
     */
    @SuppressWarnings("unused") // used by stapler
    @DataBoundConstructor
    public FreeStyleProjectFactory(List<Builder> builders, List<BuildWrapper> buildWrappers, List<Publisher> publishers)
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
    public FreeStyleMultiBranchProject.ProjectImpl newInstance(Branch branch) {
        return new FreeStyleMultiBranchProject.ProjectImpl((FreeStyleMultiBranchProject) getOwner(),
                branch,
                Collections.<JobProperty<? super FreeStyleMultiBranchProject.ProjectImpl>>emptyList(),
                Descriptor.toMap(buildWrappers),
                builders,
                Descriptor.toMap(publishers));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public FreeStyleMultiBranchProject.ProjectImpl setBranch(
            @NonNull FreeStyleMultiBranchProject.ProjectImpl project, @NonNull Branch branch) {
        if (!project.getBranch().equals(branch)) {
            project.setBranch(branch);
            try {
                project.save();
            } catch (IOException e) {
                // TODO log
            }
        } else {
            project.setBranch(branch);
        }
        return project;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isProject(Item item) {
        return item instanceof FreeStyleMultiBranchProject.ProjectImpl;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Branch getBranch(@NonNull FreeStyleMultiBranchProject.ProjectImpl project) {
        return project.getBranch();
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
            return BuildStepDescriptor.filter(Builder.all(), FreeStyleMultiBranchProject.ProjectImpl.class);
        }

        /**
         * Accessor for stapler.
         */
        @SuppressWarnings("unused") // used by stapler
        public static List<Descriptor<Publisher>> getPublisherDescriptors() {
            return BuildStepDescriptor.filter(Publisher.all(), FreeStyleMultiBranchProject.ProjectImpl.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
            return FreeStyleMultiBranchProject.class.isAssignableFrom(clazz);
        }
    }
}

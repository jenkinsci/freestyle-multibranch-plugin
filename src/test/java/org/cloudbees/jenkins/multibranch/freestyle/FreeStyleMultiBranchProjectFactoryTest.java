package org.cloudbees.jenkins.multibranch.freestyle;

import hudson.model.TopLevelItem;
import java.util.Collections;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.instanceOf;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class FreeStyleMultiBranchProjectFactoryTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void smokes() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("smokes");
            c.addFile("smokes", "master", "create marker", "marker.txt", new byte[0]);
            OrganizationFolder instance = r.jenkins.createProject(OrganizationFolder.class, "instance");
            instance.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            FreeStyleMultiBranchProjectFactory factory = new FreeStyleMultiBranchProjectFactory(new FreeStyleProjectFactory());
            factory.setScmSourceCriteria(new MarkerFreeStyleSCMSourceCriteria("marker.txt"));
            instance.getProjectFactories().replaceBy(Collections.singletonList(factory));
            long watermark = SCMEvents.getWatermark();
            SCMHeadEvent.fireNow(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "smokes", "master",
                    c.getRevision("smokes", "master")));
            SCMEvents.awaitAll(watermark);
            r.waitUntilNoActivity();
            assertThat(instance.getItems(), not(containsInAnyOrder()));
            MultiBranchProject<?, ?> item = instance.getItem("smokes");
            assertThat(item, instanceOf(FreeStyleMultiBranchProject.class));
            FreeStyleMultiBranchProject smokes = (FreeStyleMultiBranchProject) item;
            assertThat(smokes.getItems(), not(containsInAnyOrder()));
            FreeStyleMultiBranchProject.ProjectImpl master = smokes.getItem("master");
            assertThat(master, notNullValue());
            assertThat(master.getLastBuild(), notNullValue());
        }
    }
}

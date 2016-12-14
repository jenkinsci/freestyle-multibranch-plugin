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

import hudson.model.TopLevelItem;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class FreeStyleMultiBranchProjectTest {
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
            FreeStyleMultiBranchProject instance =
                    r.jenkins.createProject(FreeStyleMultiBranchProject.class, "instance");
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "smokes", true, false, false));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[0]));
            instance.getSourcesList().add(source);
            instance.setProjectFactory(new FreeStyleProjectFactory());
            instance.setScmSourceCriteria(new MarkerFreeStyleSCMSourceCriteria("marker.txt"));
            long watermark = SCMEvents.getWatermark();
            SCMHeadEvent.fireNow(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "smokes", "master",
                    c.getRevision("smokes", "master")));
            SCMEvents.awaitAll(watermark);
            r.waitUntilNoActivity();
            assertThat(instance.getItems(), not(containsInAnyOrder()));
            FreeStyleMultiBranchProject.ProjectImpl master = instance.getItem("master");
            assertThat(master, notNullValue());
            assertThat(master.getLastBuild(), notNullValue());
        }
    }


}

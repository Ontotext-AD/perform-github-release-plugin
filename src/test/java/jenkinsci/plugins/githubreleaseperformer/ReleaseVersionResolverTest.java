/*
package hudson.plugins.releaseversionresolver;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.githubreleasepreformer.GitHubReleasePreformer;
import hudson.util.VariableResolver;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class ReleaseVersionResolverTest extends JenkinsRule {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Test
	public void testPatchVersionIncrement() throws Exception {
		FreeStyleBuild build = preformBuild("", "", true);
		assertEquals("1.0.1", getReleaseVersion(build));
		assertEquals("1.0.1", getDevelopmentVersion(build));
	}

	private String getReleaseVersion(FreeStyleBuild build) throws Exception {
		return getEnvironmentVariable(build, "${RELEASE_VERSION}");
	}

	private String getDevelopmentVersion(FreeStyleBuild build) throws Exception {
		return getEnvironmentVariable(build, "${DEVELOPMENT_VERSION}");
	}

	private FreeStyleBuild preformBuild(String buildType, String pomFile, boolean snapshots)
			throws Exception {
		URL url = Resources.getResource("pom.xml");
		String pom = Resources.toString(url, Charsets.UTF_8);
		FreeStyleProject project = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "test");

		project.getBuildersList().add(
				new GitHubReleasePreformer("hudson-onto", "f4gs4l1f3", "1.3.0", "master", "RELEASE-NOTESss.md", "Ontotext-AD",
						"release-test"));

		return project.scheduleBuild2(0).get();
	}

	@SuppressWarnings("unchecked")
	private String getEnvironmentVariable(FreeStyleBuild build, String key)
			throws Exception {
		VariableResolver variableResolver = build.getBuildVariableResolver();
		return Util.replaceMacro(key, variableResolver);
	}
}
*/

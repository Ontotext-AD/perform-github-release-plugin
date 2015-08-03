package org.jenkinsci.plugins.githubreleaseperformer;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.net.URL;

public class ReleaseVersionResolverTest extends JenkinsRule {

	private static final String MOCK_SERVER_HOST = "localhost";

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	private FreeStyleProject project;

	@Rule
	public MockServerRule mockServerRule = new MockServerRule(this);

	private MockServerClient mockServerClient =
			new MockServerClient(MOCK_SERVER_HOST, mockServerRule.getHttpPort());

	private String mockServerAddress;

	@Before public void setUp() throws Exception {
		project = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "project");
		mockServerAddress = "http://" + MOCK_SERVER_HOST + ":" + mockServerRule.getHttpPort();
	}

	@Test
	public void testGeneral() throws Exception {
		project.getBuildersList()
				.add(new GitHubReleasePerformer(mockServerAddress, "test-user", "test-pass", "1.0.0", "master",
						"RELEASE-NOTES.md",
						"Ontotext-AD", "release-test"));
		setBaseRepositoryExpectations();
		setGetReleaseNotesExpectations();
		setCreateReleaseExpectations();
		setWriteReleaseNotesExpectations();
		jenkinsRule.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0).get());
		verifyRequest("GET", "/repos/Ontotext-AD/release-test");
		verifyRequest("GET", "/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md");
		verifyRequest("POST", "/Ontotext-AD/release-test/master/RELEASE-NOTES.md");
		verifyRequest("POST", "/repos/Ontotext-AD/release-test/releases");
		verifyRequest("PUT", "/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md");
	}

	@Test
	public void testReleaseNotesFail() throws Exception {
		project.getBuildersList()
				.add(new GitHubReleasePerformer(mockServerAddress, "test-user", "test-pass", "1.0.0", "master",
						"RELEASE-NOTES.md",
						"Ontotext-AD", "release-test"));
		setBaseRepositoryExpectations();
		setCreateReleaseExpectations();
		setWriteReleaseNotesExpectations();
		jenkinsRule.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0).get());
		verifyRequest("GET", "/repos/Ontotext-AD/release-test");
		verifyRequest("GET", "/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md");
		verifyRequest("POST", "/repos/Ontotext-AD/release-test/releases");
		verifyRequest("PUT", "/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md");
	}

	@Test
	public void testCreateReleaseFail() throws Exception {
		project.getBuildersList()
				.add(new GitHubReleasePerformer(mockServerAddress, "test-user", "test-pass", "1.0.0", "master",
						"RELEASE-NOTES.md",
						"Ontotext-AD", "release-test"));
		setBaseRepositoryExpectations();
		setGetReleaseNotesExpectations();
		setWriteReleaseNotesExpectations();
		jenkinsRule.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0).get());
		verifyRequest("GET", "/repos/Ontotext-AD/release-test");
		verifyRequest("GET", "/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md");
		verifyRequest("POST", "/Ontotext-AD/release-test/master/RELEASE-NOTES.md");
		verifyRequest("POST", "/repos/Ontotext-AD/release-test/releases");
	}

	public void setBaseRepositoryExpectations() throws IOException {
		URL url = Resources.getResource("base.json");
		String body = Resources.toString(url, Charsets.UTF_8);

		mockServerClient.when(
				HttpRequest.request().withMethod("GET").withPath("/repos/Ontotext-AD/release-test")
		).respond(HttpResponse.response().withStatusCode(200).withBody(body));
	}

	public void setGetReleaseNotesExpectations() throws IOException, JSONException {
		URL url = Resources.getResource("release-notes.json");
		String body = Resources.toString(url, Charsets.UTF_8);

		JSONObject jsonObject = new JSONObject(body);
		jsonObject.put("download_url", mockServerAddress + "/Ontotext-AD/release-test/master/RELEASE-NOTES.md");

		mockServerClient.when(
				HttpRequest.request()
						.withMethod("GET")
						.withPath("/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md")
						.withQueryStringParameter("ref", "master")
		).respond(HttpResponse.response().withStatusCode(200).withBody(jsonObject.toString()));
	}

	public void setCreateReleaseExpectations() throws IOException, JSONException {
		URL url = Resources.getResource("release-notes-file.md");
		String body = Resources.toString(url, Charsets.UTF_8);

		mockServerClient.when(
				HttpRequest.request()
						.withMethod("POST")
						.withPath("/Ontotext-AD/release-test/master/RELEASE-NOTES.md")
		).respond(HttpResponse.response().withStatusCode(200).withBody(body));

		url = Resources.getResource("release-request-response.json");
		body = Resources.toString(url, Charsets.UTF_8);

		mockServerClient.when(
				HttpRequest.request()
						.withMethod("POST")
						.withPath("/repos/Ontotext-AD/release-test/releases")
		).respond(HttpResponse.response().withStatusCode(200).withBody(body));
	}

	public void setWriteReleaseNotesExpectations() throws IOException, JSONException {
		URL url = Resources.getResource("release-notes-request-response.json");
		String body = Resources.toString(url, Charsets.UTF_8);

		mockServerClient.when(
				HttpRequest.request()
						.withMethod("PUT")
						.withPath("/repos/Ontotext-AD/release-test/contents/RELEASE-NOTES.md")
		).respond(HttpResponse.response().withStatusCode(201).withBody(body));

	}

	public void verifyRequest(String method, String path) {
		mockServerClient.verify(HttpRequest.request().withMethod(method).withPath(path),
				VerificationTimes.exactly(1));
	}
}

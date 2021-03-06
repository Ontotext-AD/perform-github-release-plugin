package org.jenkinsci.plugins.githubreleaseperformer;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHReleaseBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;

public class GitHubReleasePerformer extends Builder {

	private final String apiUrl;
	private final String tag;
	private final String branch;
	private final String releaseNotesFile;
	private final String user;
	private final String password;
	private final String owner;
	private final String repository;

	private GitHub github;
	private GHRepository ghRepository;

	private static final String RELEASE_NOTES_SEPARATOR = "###################";

	private static final String RELEASE_NOTES_TEMPLATE =
			"Next release\n============\n\n### New features\n\n* [JIRA-TICKET](https://jira.ontotext.com/browse/): Some feature\n\n### Improvements\n\n* [JIRA-TICKET](https://jira.ontotext.com/browse/): Some improvement\n\n### Bug fixes\n\n* [JIRA-TICKET](https://jira.ontotext.com/browse/): Some bug fix";

	@SuppressWarnings("unused")
	@DataBoundConstructor
	public GitHubReleasePerformer(String apiUrl, String user, String password, String tag, String branch, String releaseNotesFile,
			String owner,
			String repository) {
		this.apiUrl = apiUrl;
		this.tag = Util.fixEmptyAndTrim(tag);
		this.branch = Util.fixEmptyAndTrim(branch);
		this.releaseNotesFile = Util.fixEmptyAndTrim(releaseNotesFile);
		this.user = user;
		this.password = password;
		this.owner = Util.fixEmptyAndTrim(owner);
		this.repository = Util.fixEmptyAndTrim(repository);
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		String resolvedUser = user;
		String resolvedPassword = password;
		String resolvedRepository = repository;
		String resolvedOwner = owner;
		String resolvedTag = tag;
		String resolvedBranch = branch;
		String resolvedReleaseNotesFile = releaseNotesFile;
		try {
			resolvedUser = TokenMacro.expandAll(build, listener, user);
			resolvedPassword = TokenMacro.expandAll(build, listener, password);
			resolvedRepository = TokenMacro.expandAll(build, listener, repository);
			resolvedOwner = TokenMacro.expandAll(build, listener, owner);
			resolvedTag = TokenMacro.expandAll(build, listener, tag);
			resolvedBranch = TokenMacro.expandAll(build, listener, branch);
			resolvedReleaseNotesFile = TokenMacro.expandAll(build, listener, releaseNotesFile);
		} catch (Exception e) {
			listener.error("Unable to resolve macro [%s]", e.getMessage());
		}
		try {
			if (!StringUtils.isBlank(apiUrl)) {
				github = GitHub.connectToEnterprise(apiUrl, resolvedUser, resolvedPassword);
			} else {
				github = GitHub.connectUsingPassword(resolvedUser, resolvedPassword);
			}
			ghRepository = github.getRepository(resolvedOwner + "/" + resolvedRepository);
			GHContent currentReleaseNotes = getReleaseNotesFile(resolvedReleaseNotesFile, resolvedBranch, listener);
			boolean result =
					createRelease(currentReleaseNotes, resolvedTag, resolvedReleaseNotesFile, resolvedBranch, listener);
			if (result) {
				writeNewReleaseNotes(currentReleaseNotes, resolvedTag, resolvedReleaseNotesFile, resolvedBranch, listener);
			}
		} catch (IOException e) {
			listener.error("Unable to connect to repository [%s], [%s]", apiUrl + "/" + resolvedOwner + "/" + resolvedRepository,
					e.getMessage());
		}

		// Release must not fail if GitHub release fails
		return true;
	}

	private GHContent getReleaseNotesFile(String releaseNotesFile, String branch, BuildListener listener) {
		GHContent result = null;
		try {
			result = ghRepository.getFileContent(releaseNotesFile, branch);
		} catch (IOException e) {
			listener.error("Unable to find file [%s], [%s]", releaseNotesFile, e.getMessage());
		}
		return result;
	}

	private boolean createRelease(GHContent releaseNotes, String tag, String releaseNotesFile, String branch,
			BuildListener listener) {
		boolean result = true;
		StringBuilder stringBuilder = null;
		boolean separatorFound = false;
		if (releaseNotes != null) {
			try {
				InputStream inputStream = releaseNotes.read();
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

				stringBuilder = new StringBuilder();
				String line;
				separatorFound = false;
				while ((line = bufferedReader.readLine()) != null) {
					if (line.trim().equals(RELEASE_NOTES_SEPARATOR) || line.trim().matches("^###[#]+$")) {
						separatorFound = true;
						break;
					}
					stringBuilder.append(line).append("\n");
				}
			} catch (IOException e) {
				listener.error("Unable to read release notes, [%s]", e.getMessage());
			}
		}

		GHReleaseBuilder releaseBuilder = new GHReleaseBuilder(ghRepository, tag);
		if (stringBuilder != null && separatorFound) {
			releaseBuilder.body(stringBuilder.toString().replace("Next release", tag));
		}
		releaseBuilder.commitish(branch);
		releaseBuilder.draft(false);
		releaseBuilder.name(tag);
		releaseBuilder.prerelease(false);
		try {
			releaseBuilder.create();
			String message = String.format("Release created successfully [%s]", tag);
			listener.getLogger().println(message);
		} catch (IOException e) {
			listener.error("Unable to create release, [%s]", e.getMessage());
			result = false;
		}
		return result;
	}

	private void writeNewReleaseNotes(GHContent currentReleaseNotes, String tag, String releaseNotesFile, String branch,
			BuildListener listener) {
		if (currentReleaseNotes != null) {
			try {
				InputStream inputStream = currentReleaseNotes.read();
				StringWriter writer = new StringWriter();
				IOUtils.copy(inputStream, writer, "UTF-8");
				StringBuilder stringBuilder = new StringBuilder(RELEASE_NOTES_TEMPLATE);
				stringBuilder.append("\n\n");
				stringBuilder.append(RELEASE_NOTES_SEPARATOR);
				stringBuilder.append("\n\n");
				stringBuilder.append(writer.toString().replace("Next release", tag));

				currentReleaseNotes
						.update(stringBuilder.toString(), String.format("Updating %s after release %s", releaseNotesFile, tag),
								branch);
			} catch (IOException e) {
				listener.error("Unable to update release note file [%s], [%s]", releaseNotesFile, e.getMessage());
			}

		} else {
			try {
				ghRepository.createContent(RELEASE_NOTES_TEMPLATE + "\n" + RELEASE_NOTES_SEPARATOR + "\n",
						String.format("Updating %s after release %s", releaseNotesFile, tag),
						releaseNotesFile, branch);
			} catch (IOException e) {
				listener.error("Unable to create release note file [%s], [%s]", releaseNotesFile, e.getMessage());
			}
		}
	}

	@SuppressWarnings("unused")
	public String getTag() {
		return tag;
	}

	@SuppressWarnings("unused")
	public String getBranch() {
		return branch;
	}

	@SuppressWarnings("unused")
	public String getReleaseNotesFile() {
		return releaseNotesFile;
	}

	@SuppressWarnings("unused")
	public String getUser() {
		return user;
	}

	@SuppressWarnings("unused")
	public String getPassword() {
		return password;
	}

	@SuppressWarnings("unused")
	public String getOwner() {
		return owner;
	}

	@SuppressWarnings("unused")
	public String getRepository() {
		return repository;
	}

	@SuppressWarnings("unused")
	public String getApiUrl() {
		return apiUrl;
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		public DescriptorImpl() {
			super(GitHubReleasePerformer.class);
		}

		@Override
		public String getDisplayName() {
			return "Perform GitHub release";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public Builder newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			return req.bindJSON(GitHubReleasePerformer.class, formData);
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

}
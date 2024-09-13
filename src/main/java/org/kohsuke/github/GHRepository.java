package org.kohsuke.github;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.net.URL;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import static java.util.Arrays.*;
import static java.util.Objects.requireNonNull;
import static org.kohsuke.github.Previews.*;
import javax.annotation.Nonnull;

@SuppressWarnings(value = { "UnusedDeclaration" }) @SuppressFBWarnings(value = { "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD" }, justification = "JSON API") public class GHRepository extends GHObject {
  private String nodeId, description, homepage, name, full_name;

  private String html_url;

  private GHLicense license;

  private String git_url, ssh_url, clone_url, svn_url, mirror_url;

  private GHUser owner;

  private boolean has_issues, has_wiki, fork, has_downloads, has_pages, archived, has_projects;

  private boolean allow_squash_merge;

  private boolean allow_merge_commit;

  private boolean allow_rebase_merge;

  private boolean delete_branch_on_merge;

  @JsonProperty(value = "private") private boolean _private;

  private int forks_count, stargazers_count, watchers_count, size, open_issues_count, subscribers_count;

  private String pushed_at;

  private Map<Integer, GHMilestone> milestones = new WeakHashMap<Integer, GHMilestone>();

  private String default_branch, language;

  private Map<String, GHCommit> commits = new WeakHashMap<String, GHCommit>();

  @SkipFromToString private GHRepoPermission permissions;

  private GHRepository source, parent;

  private Boolean isTemplate;

  static GHRepository read(GitHub root, String owner, String name) throws IOException {
    return root.createRequest().withUrlPath("/repos/" + owner + '/' + name).fetch(GHRepository.class).wrap(root);
  }

  public GHDeploymentBuilder createDeployment(String ref) {
    return new GHDeploymentBuilder(this, ref);
  }

  @Deprecated public PagedIterable<GHDeploymentStatus> getDeploymentStatuses(final int id) throws IOException {
    return getDeployment(id).listStatuses();
  }

  public PagedIterable<GHDeployment> listDeployments(String sha, String ref, String task, String environment) {
    return root.createRequest().with("sha", sha).with("ref", ref).with("task", task).with("environment", environment).withUrlPath(getApiTailUrl("deployments")).withPreview(ANT_MAN).withPreview(FLASH).toIterable(GHDeployment[].class, (item) -> item.wrap(this));
  }

  public GHDeployment getDeployment(long id) throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("deployments/" + id)).withPreview(ANT_MAN).withPreview(FLASH).fetch(GHDeployment.class).wrap(this);
  }

  @Deprecated public GHDeploymentStatusBuilder createDeployStatus(int deploymentId, GHDeploymentState ghDeploymentState) throws IOException {
    return getDeployment(deploymentId).createStatus(ghDeploymentState);
  }

  private static class GHRepoPermission {
    boolean pull, push, admin;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getDescription() {
    return description;
  }

  public String getHomepage() {
    return homepage;
  }

  public String getGitTransportUrl() {
    return git_url;
  }

  public String getHttpTransportUrl() {
    return clone_url;
  }

  @Deprecated public String gitHttpTransportUrl() {
    return clone_url;
  }

  public String getSvnUrl() {
    return svn_url;
  }

  public String getMirrorUrl() {
    return mirror_url;
  }

  public String getSshUrl() {
    return ssh_url;
  }

  public URL getHtmlUrl() {
    return GitHubClient.parseURL(html_url);
  }

  public String getName() {
    return name;
  }

  public String getFullName() {
    return full_name;
  }

  public boolean hasPullAccess() {
    return permissions != null && permissions.pull;
  }

  public boolean hasPushAccess() {
    return permissions != null && permissions.push;
  }

  public boolean hasAdminAccess() {
    return permissions != null && permissions.admin;
  }

  public String getLanguage() {
    return language;
  }

  public GHUser getOwner() throws IOException {
    return root.isOffline() ? owner : root.getUser(getOwnerName());
  }

  public GHIssue getIssue(int id) throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("issues/" + id)).fetch(GHIssue.class).wrap(this);
  }

  public GHIssueBuilder createIssue(String title) {
    return new GHIssueBuilder(this, title);
  }

  public List<GHIssue> getIssues(GHIssueState state) throws IOException {
    return listIssues(state).toList();
  }

  public List<GHIssue> getIssues(GHIssueState state, GHMilestone milestone) throws IOException {
    Requester requester = root.createRequest().with("state", state).with("milestone", milestone == null ? "none" : "" + milestone.getNumber());
    return requester.withUrlPath(getApiTailUrl("issues")).toIterable(GHIssue[].class, (item) -> item.wrap(this)).toList();
  }

  public PagedIterable<GHIssue> listIssues(final GHIssueState state) {
    return root.createRequest().with("state", state).withUrlPath(getApiTailUrl("issues")).toIterable(GHIssue[].class, (item) -> item.wrap(this));
  }

  public GHReleaseBuilder createRelease(String tag) {
    return new GHReleaseBuilder(this, tag);
  }

  public GHRef createRef(String name, String sha) throws IOException {
    return root.createRequest().method("POST").with("ref", name).with("sha", sha).withUrlPath(getApiTailUrl("git/refs")).fetch(GHRef.class).wrap(root);
  }

  public List<GHRelease> getReleases() throws IOException {
    return listReleases().toList();
  }

  public GHRelease getRelease(long id) throws IOException {
    try {
      return root.createRequest().withUrlPath(getApiTailUrl("releases/" + id)).fetch(GHRelease.class).wrap(this);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public GHRelease getReleaseByTagName(String tag) throws IOException {
    try {
      return root.createRequest().withUrlPath(getApiTailUrl("releases/tags/" + tag)).fetch(GHRelease.class).wrap(this);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public GHRelease getLatestRelease() throws IOException {
    try {
      return root.createRequest().withUrlPath(getApiTailUrl("releases/latest")).fetch(GHRelease.class).wrap(this);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public PagedIterable<GHRelease> listReleases() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("releases")).toIterable(GHRelease[].class, (item) -> item.wrap(this));
  }

  public PagedIterable<GHTag> listTags() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("tags")).toIterable(GHTag[].class, (item) -> item.wrap(this));
  }

  public Map<String, Long> listLanguages() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("languages")).fetch(HashMap.class);
  }

  public String getOwnerName() {
    return owner.login != null ? owner.login : owner.name;
  }

  public boolean hasIssues() {
    return has_issues;
  }

  public boolean hasProjects() {
    return has_projects;
  }

  public boolean hasWiki() {
    return has_wiki;
  }

  public boolean isFork() {
    return fork;
  }

  public boolean isArchived() {
    return archived;
  }

  public boolean isAllowSquashMerge() {
    return allow_squash_merge;
  }

  public boolean isAllowMergeCommit() {
    return allow_merge_commit;
  }

  public boolean isAllowRebaseMerge() {
    return allow_rebase_merge;
  }

  public boolean isDeleteBranchOnMerge() {
    return delete_branch_on_merge;
  }

  @Deprecated public int getForks() {
    return getForksCount();
  }

  public int getForksCount() {
    return forks_count;
  }

  public int getStargazersCount() {
    return stargazers_count;
  }

  public boolean isPrivate() {
    return _private;
  }

  @Deprecated public @Preview(value = BAPTISTE) boolean isTemplate() {
    if (isTemplate == null) {
      try {
        populate();
      } catch (IOException e) {
        throw new GHException("Could not populate the template setting of the repository", e);
      }
      isTemplate = Boolean.TRUE.equals(isTemplate);
    }
    return isTemplate;
  }

  public boolean hasDownloads() {
    return has_downloads;
  }

  public boolean hasPages() {
    return has_pages;
  }

  @Deprecated public int getWatchers() {
    return getWatchersCount();
  }

  public int getWatchersCount() {
    return watchers_count;
  }

  public int getOpenIssueCount() {
    return open_issues_count;
  }

  public int getSubscribersCount() {
    return subscribers_count;
  }

  public Date getPushedAt() {
    return GitHubClient.parseDate(pushed_at);
  }

  public String getDefaultBranch() {
    return default_branch;
  }

  @Deprecated public String getMasterBranch() {
    return default_branch;
  }

  public int getSize() {
    return size;
  }

  @WithBridgeMethods(value = Set.class) public GHPersonSet<GHUser> getCollaborators() throws IOException {
    return new GHPersonSet<GHUser>(listCollaborators().toList());
  }

  public PagedIterable<GHUser> listCollaborators() throws IOException {
    return listUsers("collaborators");
  }

  public PagedIterable<GHUser> listAssignees() throws IOException {
    return listUsers("assignees");
  }

  public boolean hasAssignee(GHUser u) throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("assignees/" + u.getLogin())).fetchHttpStatusCode() / 100 == 2;
  }

  public Set<String> getCollaboratorNames() throws IOException {
    Set<String> r = new HashSet<>();
    PagedIterable<GHUser> users = root.createRequest().withUrlPath(getApiTailUrl("collaborators")).toIterable(GHUser[].class, null);
    for (GHUser u : users.toArray()) {
      r.add(u.login);
    }
    return r;
  }

  public GHPermissionType getPermission(String user) throws IOException {
    GHPermission perm = root.createRequest().withUrlPath(getApiTailUrl("collaborators/" + user + "/permission")).fetch(GHPermission.class);
    perm.wrapUp(root);
    return perm.getPermissionType();
  }

  public GHPermissionType getPermission(GHUser u) throws IOException {
    return getPermission(u.getLogin());
  }

  public Set<GHTeam> getTeams() throws IOException {
    GHOrganization org = root.getOrganization(getOwnerName());
    return root.createRequest().withUrlPath(getApiTailUrl("teams")).toIterable(GHTeam[].class, (item) -> item.wrapUp(org)).toSet();
  }

  public void addCollaborators(GHOrganization.Permission permission, GHUser... users) throws IOException {
    addCollaborators(asList(users), permission);
  }

  public void addCollaborators(GHUser... users) throws IOException {
    addCollaborators(asList(users));
  }

  public void addCollaborators(Collection<GHUser> users) throws IOException {
    modifyCollaborators(users, "PUT", null);
  }

  public void addCollaborators(Collection<GHUser> users, GHOrganization.Permission permission) throws IOException {
    modifyCollaborators(users, "PUT", permission);
  }

  public void removeCollaborators(GHUser... users) throws IOException {
    removeCollaborators(asList(users));
  }

  public void removeCollaborators(Collection<GHUser> users) throws IOException {
    modifyCollaborators(users, "DELETE", null);
  }

  private void modifyCollaborators(@NonNull Collection<GHUser> users, @NonNull String method, @CheckForNull GHOrganization.Permission permission) throws IOException {
    Requester requester = root.createRequest().method(method);
    if (permission != null) {
      requester = requester.with("permission", permission).inBody();
    }
    for (GHUser user : new LinkedHashSet<GHUser>(users)) {
      requester.withUrlPath(getApiTailUrl("collaborators/" + user.getLogin())).send();
    }
  }

  public void setEmailServiceHook(String address) throws IOException {
    Map<String, String> config = new HashMap<String, String>();
    config.put("address", address);
    root.createRequest().method("POST").with("name", "email").with("config", config).with("active", true).withUrlPath(getApiTailUrl("hooks")).send();
  }

  public void enableIssueTracker(boolean v) throws IOException {
    set().issues(v);
  }

  public void enableProjects(boolean v) throws IOException {
    set().projects(v);
  }

  public void enableWiki(boolean v) throws IOException {
    set().wiki(v);
  }

  public void enableDownloads(boolean v) throws IOException {
    set().downloads(v);
  }

  public void renameTo(String name) throws IOException {
    set().name(name);
  }

  public void setDescription(String value) throws IOException {
    set().description(value);
  }

  public void setHomepage(String value) throws IOException {
    set().homepage(value);
  }

  public void setDefaultBranch(String value) throws IOException {
    set().defaultBranch(value);
  }

  public void setPrivate(boolean value) throws IOException {
    set().private_(value);
  }

  public void allowSquashMerge(boolean value) throws IOException {
    set().allowSquashMerge(value);
  }

  public void allowMergeCommit(boolean value) throws IOException {
    set().allowMergeCommit(value);
  }

  public void allowRebaseMerge(boolean value) throws IOException {
    set().allowRebaseMerge(value);
  }

  public void deleteBranchOnMerge(boolean value) throws IOException {
    set().deleteBranchOnMerge(value);
  }

  public void delete() throws IOException {
    try {
      root.createRequest().method("DELETE").withUrlPath(getApiTailUrl("")).send();
    } catch (FileNotFoundException x) {
      throw (FileNotFoundException) new FileNotFoundException("Failed to delete " + getOwnerName() + "/" + name + "; might not exist, or you might need the delete_repo scope in your token: http://stackoverflow.com/a/19327004/12916").initCause(x);
    }
  }

  public void archive() throws IOException {
    set().archive();
    archived = true;
  }

  public enum ForkSort {
    NEWEST,
    OLDEST,
    STARGAZERS
  }

  public PagedIterable<GHRepository> listForks() {
    return listForks(null);
  }

  public PagedIterable<GHRepository> listForks(final ForkSort sort) {
    return root.createRequest().with("sort", sort).withUrlPath(getApiTailUrl("forks")).toIterable(GHRepository[].class, (item) -> item.wrap(root));
  }

  public GHRepository fork() throws IOException {
    root.createRequest().method("POST").withUrlPath(getApiTailUrl("forks")).send();
    for (int i = 0; i < 10; i++) {
      GHRepository r = root.getMyself().getRepository(name);
      if (r != null) {
        return r;
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        throw (IOException) new InterruptedIOException().initCause(e);
      }
    }
    throw new IOException(this + " was forked but can\'t find the new repository");
  }

  public GHRepository forkTo(GHOrganization org) throws IOException {
    root.createRequest().method("POST").with("organization", org.getLogin()).withUrlPath(getApiTailUrl("forks")).send();
    for (int i = 0; i < 10; i++) {
      GHRepository r = org.getRepository(name);
      if (r != null) {
        return r;
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        throw (IOException) new InterruptedIOException().initCause(e);
      }
    }
    throw new IOException(this + " was forked into " + org.getLogin() + " but can\'t find the new repository");
  }

  public GHPullRequest getPullRequest(int i) throws IOException {
    return root.createRequest().withPreview(SHADOW_CAT).withUrlPath(getApiTailUrl("pulls/" + i)).fetch(GHPullRequest.class).wrapUp(this);
  }

  public List<GHPullRequest> getPullRequests(GHIssueState state) throws IOException {
    return queryPullRequests().state(state).list().toList();
  }

  @Deprecated public PagedIterable<GHPullRequest> listPullRequests(GHIssueState state) {
    return queryPullRequests().state(state).list();
  }

  public GHPullRequestQueryBuilder queryPullRequests() {
    return new GHPullRequestQueryBuilder(this);
  }

  public GHPullRequest createPullRequest(String title, String head, String base, String body) throws IOException {
    return createPullRequest(title, head, base, body, true);
  }

  public GHPullRequest createPullRequest(String title, String head, String base, String body, boolean maintainerCanModify) throws IOException {
    return createPullRequest(title, head, base, body, maintainerCanModify, false);
  }

  public GHPullRequest createPullRequest(String title, String head, String base, String body, boolean maintainerCanModify, boolean draft) throws IOException {
    return root.createRequest().method("POST").withPreview(SHADOW_CAT).with("title", title).with("head", head).with("base", base).with("body", body).with("maintainer_can_modify", maintainerCanModify).with("draft", draft).withUrlPath(getApiTailUrl("pulls")).fetch(GHPullRequest.class).wrapUp(this);
  }

  public List<GHHook> getHooks() throws IOException {
    return GHHooks.repoContext(this, owner).getHooks();
  }

  public GHHook getHook(int id) throws IOException {
    return GHHooks.repoContext(this, owner).getHook(id);
  }

  public GHCompare getCompare(String id1, String id2) throws IOException {
    GHCompare compare = root.createRequest().withUrlPath(getApiTailUrl(String.format("compare/%s...%s", id1, id2))).fetch(GHCompare.class);
    return compare.wrap(this);
  }

  public GHCompare getCompare(GHCommit id1, GHCommit id2) throws IOException {
    return getCompare(id1.getSHA1(), id2.getSHA1());
  }

  public GHCompare getCompare(GHBranch id1, GHBranch id2) throws IOException {
    GHRepository owner1 = id1.getOwner();
    GHRepository owner2 = id2.getOwner();
    if (owner1 != null && owner2 != null) {
      String ownerName1 = owner1.getOwnerName();
      String ownerName2 = owner2.getOwnerName();
      if (!StringUtils.equals(ownerName1, ownerName2)) {
        String qualifiedName1 = String.format("%s:%s", ownerName1, id1.getName());
        String qualifiedName2 = String.format("%s:%s", ownerName2, id2.getName());
        return getCompare(qualifiedName1, qualifiedName2);
      }
    }
    return getCompare(id1.getName(), id2.getName());
  }

  public GHRef[] getRefs() throws IOException {
    return listRefs().toArray();
  }

  public PagedIterable<GHRef> listRefs() throws IOException {
    return listRefs("");
  }

  public GHRef[] getRefs(String refType) throws IOException {
    return listRefs(refType).toArray();
  }

  public PagedIterable<GHRef> listRefs(String refType) throws IOException {
    return GHRef.readMatching(this, refType);
  }

  public GHRef getRef(String refName) throws IOException {
    return GHRef.read(this, refName);
  }

  public GHTagObject getTagObject(String sha) throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("git/tags/" + sha)).fetch(GHTagObject.class).wrap(this);
  }

  public GHTree getTree(String sha) throws IOException {
    String url = String.format("/repos/%s/%s/git/trees/%s", getOwnerName(), name, sha);
    return root.createRequest().withUrlPath(url).fetch(GHTree.class).wrap(this);
  }

  public GHTreeBuilder createTree() {
    return new GHTreeBuilder(this);
  }

  public GHTree getTreeRecursive(String sha, int recursive) throws IOException {
    String url = String.format("/repos/%s/%s/git/trees/%s", getOwnerName(), name, sha);
    return root.createRequest().with("recursive", recursive).withUrlPath(url).fetch(GHTree.class).wrap(this);
  }

  public GHBlob getBlob(String blobSha) throws IOException {
    String target = getApiTailUrl("git/blobs/" + blobSha);
    return root.createRequest().withUrlPath(target).fetch(GHBlob.class);
  }

  public GHBlobBuilder createBlob() {
    return new GHBlobBuilder(this);
  }

  public InputStream readBlob(String blobSha) throws IOException {
    String target = getApiTailUrl("git/blobs/" + blobSha);
    return root.createRequest().withHeader("Accept", "application/vnd.github.v3.raw").withUrlPath(target).fetchStream();
  }

  public GHCommit getCommit(String sha1) throws IOException {
    GHCommit c = commits.get(sha1);
    if (c == null) {
      c = root.createRequest().withUrlPath(String.format("/repos/%s/%s/commits/%s", getOwnerName(), name, sha1)).fetch(GHCommit.class).wrapUp(this);
      commits.put(sha1, c);
    }
    return c;
  }

  public GHCommitBuilder createCommit() {
    return new GHCommitBuilder(this);
  }

  public PagedIterable<GHCommit> listCommits() {
    return root.createRequest().withUrlPath(String.format("/repos/%s/%s/commits", getOwnerName(), name)).toIterable(GHCommit[].class, (item) -> item.wrapUp(this));
  }

  public GHCommitQueryBuilder queryCommits() {
    return new GHCommitQueryBuilder(this);
  }

  public PagedIterable<GHCommitComment> listCommitComments() {
    return root.createRequest().withUrlPath(String.format("/repos/%s/%s/comments", getOwnerName(), name)).toIterable(GHCommitComment[].class, (item) -> item.wrap(this));
  }

  public PagedIterable<GHCommitComment> listCommitComments(String commitSha) {
    return root.createRequest().withUrlPath(String.format("/repos/%s/%s/commits/%s/comments", getOwnerName(), name, commitSha)).toIterable(GHCommitComment[].class, (item) -> item.wrap(this));
  }

  public GHLicense getLicense() throws IOException {
    GHContentWithLicense lic = getLicenseContent_();
    return lic != null ? lic.license : null;
  }

  public GHContent getLicenseContent() throws IOException {
    return getLicenseContent_();
  }

  private GHContentWithLicense getLicenseContent_() throws IOException {
    try {
      return root.createRequest().withUrlPath(getApiTailUrl("license")).fetch(GHContentWithLicense.class).wrap(this);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public PagedIterable<GHCommitStatus> listCommitStatuses(final String sha1) throws IOException {
    return root.createRequest().withUrlPath(String.format("/repos/%s/%s/statuses/%s", getOwnerName(), name, sha1)).toIterable(GHCommitStatus[].class, (item) -> item.wrapUp(root));
  }

  public GHCommitStatus getLastCommitStatus(String sha1) throws IOException {
    List<GHCommitStatus> v = listCommitStatuses(sha1).toList();
    return v.isEmpty() ? null : v.get(0);
  }

  @Deprecated public @Preview(value = ANTIOPE) PagedIterable<GHCheckRun> getCheckRuns(String ref) throws IOException {
    GitHubRequest request = root.createRequest().withUrlPath(String.format("/repos/%s/%s/commits/%s/check-runs", getOwnerName(), name, ref)).withPreview(ANTIOPE).build();
    return new GHCheckRunsIterable(root, request);
  }

  public GHCommitStatus createCommitStatus(String sha1, GHCommitState state, String targetUrl, String description, String context) throws IOException {
    return root.createRequest().method("POST").with("state", state).with("target_url", targetUrl).with("description", description).with("context", context).withUrlPath(String.format("/repos/%s/%s/statuses/%s", getOwnerName(), this.name, sha1)).fetch(GHCommitStatus.class).wrapUp(root);
  }

  public GHCommitStatus createCommitStatus(String sha1, GHCommitState state, String targetUrl, String description) throws IOException {
    return createCommitStatus(sha1, state, targetUrl, description, null);
  }

  @Deprecated public @NonNull @Preview(value = ANTIOPE) GHCheckRunBuilder createCheckRun(@NonNull String name, @NonNull String headSHA) {
    return new GHCheckRunBuilder(this, name, headSHA);
  }

  public PagedIterable<GHEventInfo> listEvents() throws IOException {
    return root.createRequest().withUrlPath(String.format("/repos/%s/%s/events", getOwnerName(), name)).toIterable(GHEventInfo[].class, (item) -> item.wrapUp(root));
  }

  public PagedIterable<GHLabel> listLabels() throws IOException {
    return GHLabel.readAll(this);
  }

  public GHLabel getLabel(String name) throws IOException {
    return GHLabel.read(this, name);
  }

  public GHLabel createLabel(String name, String color) throws IOException {
    return GHLabel.create(this).name(name).color(color).description("").done();
  }

  public GHLabel createLabel(String name, String color, String description) throws IOException {
    return GHLabel.create(this).name(name).color(color).description(description).done();
  }

  public PagedIterable<GHInvitation> listInvitations() {
    return root.createRequest().withUrlPath(String.format("/repos/%s/%s/invitations", getOwnerName(), name)).toIterable(GHInvitation[].class, (item) -> item.wrapUp(root));
  }

  public PagedIterable<GHUser> listSubscribers() {
    return listUsers("subscribers");
  }

  public PagedIterable<GHUser> listStargazers() {
    return listUsers("stargazers");
  }

  public PagedIterable<GHStargazer> listStargazers2() {
    return root.createRequest().withPreview("application/vnd.github.v3.star+json").withUrlPath(getApiTailUrl("stargazers")).toIterable(GHStargazer[].class, (item) -> item.wrapUp(this));
  }

  private PagedIterable<GHUser> listUsers(final String suffix) {
    return listUsers(root.createRequest(), suffix);
  }

  public GHHook createHook(String name, Map<String, String> config, Collection<GHEvent> events, boolean active) throws IOException {
    return GHHooks.repoContext(this, owner).createHook(name, config, events, active);
  }

  public GHHook createWebHook(URL url, Collection<GHEvent> events) throws IOException {
    return createHook("web", Collections.singletonMap("url", url.toExternalForm()), events, true);
  }

  public GHHook createWebHook(URL url) throws IOException {
    return createWebHook(url, null);
  }

  @SuppressFBWarnings(value = "DMI_COLLECTION_OF_URLS", justification = "It causes a performance degradation, but we have already exposed it to the API") @Deprecated public Set<URL> getPostCommitHooks() {
    synchronized (this) {
      if (postCommitHooks == null) {
        postCommitHooks = setupPostCommitHooks();
      }
      return postCommitHooks;
    }
  }

  @SuppressFBWarnings(value = "DMI_COLLECTION_OF_URLS", justification = "It causes a performance degradation, but we have already exposed it to the API") @SkipFromToString private transient Set<URL> postCommitHooks = new AbstractSet<URL>() {
    private List<URL> getPostCommitHooks() {
      try {
        List<URL> r = new ArrayList<>();
        for (GHHook h : getHooks()) {
          if (h.getName().equals("web")) {
            r.add(new URL(h.getConfig().get("url")));
          }
        }
        return r;
      } catch (IOException e) {
        throw new GHException("Failed to retrieve post-commit hooks", e);
      }
    }

    @Override public Iterator<URL> iterator() {
      return getPostCommitHooks().iterator();
    }

    @Override public int size() {
      return getPostCommitHooks().size();
    }

    @Override public boolean add(URL url) {
      try {
        createWebHook(url);
        return true;
      } catch (IOException e) {
        throw new GHException("Failed to update post-commit hooks", e);
      }
    }

    @Override public boolean remove(Object url) {
      try {
        String _url = ((URL) url).toExternalForm();
        for (GHHook h : getHooks()) {
          if (h.getName().equals("web") && h.getConfig().get("url").equals(_url)) {
            h.delete();
            return true;
          }
        }
        return false;
      } catch (IOException e) {
        throw new GHException("Failed to update post-commit hooks", e);
      }
    }
  };

  GHRepository wrap(GitHub root) {
    this.root = root;
    if (root.isOffline() && owner != null) {
      owner.wrapUp(root);
    }
    if (source != null) {
      source.wrap(root);
    }
    if (parent != null) {
      parent.wrap(root);
    }
    return this;
  }

  public Map<String, GHBranch> getBranches() throws IOException {
    Map<String, GHBranch> r = new TreeMap<String, GHBranch>();
    for (GHBranch p : root.createRequest().withUrlPath(getApiTailUrl("branches")).toIterable(GHBranch[].class, (item) -> item.wrap(this)).toArray()) {
      r.put(p.getName(), p);
    }
    return r;
  }

  public GHBranch getBranch(String name) throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("branches/" + name)).fetch(GHBranch.class).wrap(this);
  }

  public Map<Integer, GHMilestone> getMilestones() throws IOException {
    Map<Integer, GHMilestone> milestones = new TreeMap<Integer, GHMilestone>();
    for (GHMilestone m : listMilestones(GHIssueState.OPEN)) {
      milestones.put(m.getNumber(), m);
    }
    return milestones;
  }

  public PagedIterable<GHMilestone> listMilestones(final GHIssueState state) {
    return root.createRequest().with("state", state).withUrlPath(getApiTailUrl("milestones")).toIterable(GHMilestone[].class, (item) -> item.wrap(this));
  }

  public GHMilestone getMilestone(int number) throws IOException {
    GHMilestone m = milestones.get(number);
    if (m == null) {
      m = root.createRequest().withUrlPath(getApiTailUrl("milestones/" + number)).fetch(GHMilestone.class);
      m.owner = this;
      m.root = root;
      milestones.put(m.getNumber(), m);
    }
    return m;
  }

  public GHContent getFileContent(String path) throws IOException {
    return getFileContent(path, null);
  }

  public GHContent getFileContent(String path, String ref) throws IOException {
    Requester requester = root.createRequest();
    String target = getApiTailUrl("contents/" + path);
    return requester.with("ref", ref).withUrlPath(target).fetch(GHContent.class).wrap(this);
  }

  public List<GHContent> getDirectoryContent(String path) throws IOException {
    return getDirectoryContent(path, null);
  }

  public List<GHContent> getDirectoryContent(String path, String ref) throws IOException {
    Requester requester = root.createRequest();
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    String target = getApiTailUrl("contents/" + path);
    return requester.with("ref", ref).withUrlPath(target).toIterable(GHContent[].class, (item) -> item.wrap(this)).toList();
  }

  public GHContent getReadme() throws IOException {
    Requester requester = root.createRequest();
    return requester.withUrlPath(getApiTailUrl("readme")).fetch(GHContent.class).wrap(this);
  }

  public GHContentBuilder createContent() {
    return new GHContentBuilder(this);
  }

  @Deprecated public GHContentUpdateResponse createContent(String content, String commitMessage, String path) throws IOException {
    return createContent().content(content).message(commitMessage).path(path).commit();
  }

  @Deprecated public GHContentUpdateResponse createContent(String content, String commitMessage, String path, String branch) throws IOException {
    return createContent().content(content).message(commitMessage).path(path).branch(branch).commit();
  }

  @Deprecated public GHContentUpdateResponse createContent(byte[] contentBytes, String commitMessage, String path) throws IOException {
    return createContent().content(contentBytes).message(commitMessage).path(path).commit();
  }

  @Deprecated public GHContentUpdateResponse createContent(byte[] contentBytes, String commitMessage, String path, String branch) throws IOException {
    return createContent().content(contentBytes).message(commitMessage).path(path).branch(branch).commit();
  }

  public GHMilestone createMilestone(String title, String description) throws IOException {
    return root.createRequest().method("POST").with("title", title).with("description", description).withUrlPath(getApiTailUrl("milestones")).fetch(GHMilestone.class).wrap(this);
  }

  public GHDeployKey addDeployKey(String title, String key) throws IOException {
    return root.createRequest().method("POST").with("title", title).with("key", key).withUrlPath(getApiTailUrl("keys")).fetch(GHDeployKey.class).wrap(this);
  }

  public List<GHDeployKey> getDeployKeys() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("keys")).toIterable(GHDeployKey[].class, (item) -> item.wrap(this)).toList();
  }

  public GHRepository getSource() throws IOException {
    if (fork && source == null) {
      populate();
    }
    if (source == null) {
      return null;
    }
    return source;
  }

  public GHRepository getParent() throws IOException {
    if (fork && parent == null) {
      populate();
    }
    if (parent == null) {
      return null;
    }
    return parent;
  }

  public GHSubscription subscribe(boolean subscribed, boolean ignored) throws IOException {
    return root.createRequest().method("PUT").with("subscribed", subscribed).with("ignored", ignored).withUrlPath(getApiTailUrl("subscription")).fetch(GHSubscription.class).wrapUp(this);
  }

  public GHSubscription getSubscription() throws IOException {
    try {
      return root.createRequest().withUrlPath(getApiTailUrl("subscription")).fetch(GHSubscription.class).wrapUp(this);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public PagedIterable<Contributor> listContributors() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("contributors")).toIterable(Contributor[].class, (item) -> item.wrapUp(root));
  }

  public static class Contributor extends GHUser {
    private int contributions;

    public int getContributions() {
      return contributions;
    }

    @Override public int hashCode() {
      return super.hashCode();
    }

    @Override public boolean equals(Object obj) {
      return super.equals(obj);
    }
  }

  public GHRepositoryStatistics getStatistics() {
    return new GHRepositoryStatistics(this);
  }

  public GHProject createProject(String name, String body) throws IOException {
    return root.createRequest().method("POST").withPreview(INERTIA).with("name", name).with("body", body).withUrlPath(getApiTailUrl("projects")).fetch(GHProject.class).wrap(this);
  }

  public PagedIterable<GHProject> listProjects(final GHProject.ProjectStateFilter status) throws IOException {
    return root.createRequest().withPreview(INERTIA).with("state", status).withUrlPath(getApiTailUrl("projects")).toIterable(GHProject[].class, (item) -> item.wrap(this));
  }

  public PagedIterable<GHProject> listProjects() throws IOException {
    return listProjects(GHProject.ProjectStateFilter.OPEN);
  }

  public Reader renderMarkdown(String text, MarkdownMode mode) throws IOException {
    return new InputStreamReader(root.createRequest().method("POST").with("text", text).with("mode", mode == null ? null : mode.toString()).with("context", getFullName()).withUrlPath("/markdown").fetchStream(), "UTF-8");
  }

  public GHNotificationStream listNotifications() {
    return new GHNotificationStream(root, getApiTailUrl("/notifications"));
  }

  public GHRepositoryViewTraffic getViewTraffic() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("/traffic/views")).fetch(GHRepositoryViewTraffic.class);
  }

  public GHRepositoryCloneTraffic getCloneTraffic() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("/traffic/clones")).fetch(GHRepositoryCloneTraffic.class);
  }

  @Override public int hashCode() {
    return ("Repository:" + getOwnerName() + ":" + name).hashCode();
  }

  @Override public boolean equals(Object obj) {
    if (obj instanceof GHRepository) {
      GHRepository that = (GHRepository) obj;
      return this.getOwnerName().equals(that.getOwnerName()) && this.name.equals(that.name);
    }
    return false;
  }

  String getApiTailUrl(String tail) {
    if (tail.length() > 0 && !tail.startsWith("/")) {
      tail = '/' + tail;
    }
    return "/repos/" + getOwnerName() + "/" + name + tail;
  }

  public PagedIterable<GHIssueEvent> listIssueEvents() throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("issues/events")).toIterable(GHIssueEvent[].class, (item) -> item.wrapUp(root));
  }

  public GHIssueEvent getIssueEvent(long id) throws IOException {
    return root.createRequest().withUrlPath(getApiTailUrl("issues/events/" + id)).fetch(GHIssueEvent.class).wrapUp(root);
  }

  private static class Topics {
    public List<String> names;
  }

  public List<String> listTopics() throws IOException {
    Topics topics = root.createRequest().withPreview(MERCY).withUrlPath(getApiTailUrl("topics")).fetch(Topics.class);
    return topics.names;
  }

  public void setTopics(List<String> topics) throws IOException {
    root.createRequest().method("PUT").with("names", topics).withPreview(MERCY).withUrlPath(getApiTailUrl("topics")).send();
  }

  public GHTagObject createTag(String tag, String message, String object, String type) throws IOException {
    return root.createRequest().method("POST").with("tag", tag).with("message", message).with("object", object).with("type", type).withUrlPath(getApiTailUrl("git/tags")).fetch(GHTagObject.class).wrap(this);
  }

  public void zipball(StreamConsumer sink, String ref) throws IOException {
    downloadArchive("zip", Optional.ofNullable(ref), sink);
  }

  public void tarball(StreamConsumer sink, String ref) throws IOException {
    downloadArchive("tar", Optional.ofNullable(ref), sink);
  }

  @FunctionalInterface public interface StreamConsumer {
    void accept(InputStream stream) throws IOException;
  }

  private void downloadArchive(String type, Optional<String> ref, StreamConsumer sink) throws IOException {
    requireNonNull(sink, "Sink must not be null");
    final String base = getApiTailUrl(requireNonNull(type, "Type must not be null") + "ball");
    final String url = ref.map(base::concat).orElse(base);
    final Requester builder = root.createRequest().method("GET").withUrlPath(url);
    builder.client.sendRequest(builder.build(), (response) -> {
      try (InputStream body = response.bodyStream()) {
        sink.accept(body);
      }
      return null;
    });
  }

  void populate() throws IOException {
    if (root.isOffline()) {
      return;
    }
    final URL url = requireNonNull(getUrl(), "Missing instance URL!");
    try {
      root.createRequest().withPreview(BAPTISTE).setRawUrlPath(url.toString()).fetchInto(this).wrap(root);
    } catch (HttpException e) {
      if (e.getCause() instanceof JsonParseException) {
        root.createRequest().withPreview(BAPTISTE).withUrlPath("/repos/" + full_name).fetchInto(this).wrap(root);
      } else {
        throw e;
      }
    }
  }

  public enum CollaboratorAffiliation {
    ALL,
    DIRECT,
    OUTSIDE
  }

  public PagedIterable<GHUser> listCollaborators(CollaboratorAffiliation affiliation) throws IOException {
    return listUsers(root.createRequest().with("affiliation", affiliation), "collaborators");
  }

  public Set<String> getCollaboratorNames(CollaboratorAffiliation affiliation) throws IOException {
    Set<String> r = new HashSet<>();
    PagedIterable<GHUser> users = root.createRequest().withUrlPath(getApiTailUrl("collaborators")).with("affiliation", affiliation).toIterable(GHUser[].class, null);
    for (GHUser u : users.toArray()) {
      r.add(u.login);
    }
    return r;
  }

  public Updater update() {
    return new Updater(this);
  }

  public Setter set() {
    return new Setter(this);
  }

  @Preview(value = BAPTISTE) @Deprecated public @NonNull GHCheckRunBuilder updateCheckRun(long checkId) {
    return new GHCheckRunBuilder(this, checkId);
  }

  private PagedIterable<GHUser> listUsers(Requester requester, final String suffix) {
    return requester.withUrlPath(getApiTailUrl(suffix)).toIterable(GHUser[].class, (item) -> item.wrapUp(root));
  }

  @SuppressFBWarnings(value = "DMI_COLLECTION_OF_URLS", justification = "It causes a performance degradation, but we have already exposed it to the API") private Set<URL> setupPostCommitHooks() {
    return new AbstractSet<URL>() {
      private List<URL> getPostCommitHooks() {
        try {
          List<URL> r = new ArrayList<>();
          for (GHHook h : getHooks()) {
            if (h.getName().equals("web")) {
              r.add(new URL(h.getConfig().get("url")));
            }
          }
          return r;
        } catch (IOException e) {
          throw new GHException("Failed to retrieve post-commit hooks", e);
        }
      }

      @Override public Iterator<URL> iterator() {
        return getPostCommitHooks().iterator();
      }

      @Override public int size() {
        return getPostCommitHooks().size();
      }

      @Override public boolean add(URL url) {
        try {
          createWebHook(url);
          return true;
        } catch (IOException e) {
          throw new GHException("Failed to update post-commit hooks", e);
        }
      }

      @Override public boolean remove(Object url) {
        try {
          String _url = ((URL) url).toExternalForm();
          for (GHHook h : getHooks()) {
            if (h.getName().equals("web") && h.getConfig().get("url").equals(_url)) {
              h.delete();
              return true;
            }
          }
          return false;
        } catch (IOException e) {
          throw new GHException("Failed to update post-commit hooks", e);
        }
      }
    };
  }

  @BetaApi @Deprecated public static class Updater extends GHRepositoryBuilder<Updater> {
    protected Updater(@Nonnull GHRepository repository) {
      super(Updater.class, repository.root, null);
      requester.with("name", repository.name);
      requester.method("PATCH").withUrlPath(repository.getApiTailUrl(""));
    }
  }

  @BetaApi @Deprecated public static class Setter extends GHRepositoryBuilder<GHRepository> {
    protected Setter(@Nonnull GHRepository repository) {
      super(GHRepository.class, repository.root, null);
      requester.with("name", repository.name);
      requester.method("PATCH").withUrlPath(repository.getApiTailUrl(""));
    }
  }
}
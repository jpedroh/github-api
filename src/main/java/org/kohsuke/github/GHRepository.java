package org.kohsuke.github;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static java.util.Arrays.*;

@SuppressWarnings(value = { "UnusedDeclaration" }) public class GHRepository {
  GitHub root;

  private String description, homepage, name;

  private String url;

  private String html_url;

  private GHUser owner;

  private boolean has_issues, has_wiki, fork, has_downloads;

  @JsonProperty(value = "private") private boolean _private;

  private int watchers, forks, open_issues, size, network_count, subscribers_count;

  private String created_at, pushed_at;

  private Map<Integer, GHMilestone> milestones = new HashMap<Integer, GHMilestone>();

  private String default_branch, language;

  private Map<String, GHCommit> commits = new HashMap<String, GHCommit>();

  private GHRepoPermission permissions;

  private static class GHRepoPermission {
    boolean pull, push, admin;
  }

  public String getDescription() {
    return description;
  }

  public String getHomepage() {
    return homepage;
  }

  public String getUrl() {
    return html_url;
  }

  public String getGitTransportUrl() {
    return "git://github.com/" + getOwnerName() + "/" + name + ".git";
  }

  public String gitHttpTransportUrl() {
    return "https://github.com/" + getOwnerName() + "/" + name + ".git";
  }

  public String getName() {
    return name;
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
    return root.getUser(owner.login);
  }

  public GHIssue getIssue(int id) throws IOException {
    return root.retrieve().to("/repos/" + owner.login + "/" + name + "/issues/" + id, GHIssue.class).wrap(this);
  }

  public GHIssueBuilder createIssue(String title) {
    return new GHIssueBuilder(this, title);
  }

  public List<GHIssue> getIssues(GHIssueState state) throws IOException {
    return listIssues(state).asList();
  }

  public List<GHIssue> getIssues(GHIssueState state, GHMilestone milestone) throws IOException {
    return Arrays.asList(GHIssue.wrap(root.retrieve().to(String.format("/repos/%s/%s/issues?state=%s&milestone=%s", owner.login, name, state.toString().toLowerCase(), milestone == null ? "none" : "" + milestone.getNumber()), GHIssue[].class), this));
  }

  public PagedIterable<GHIssue> listIssues(final GHIssueState state) {
    return new PagedIterable<GHIssue>() {
      public PagedIterator<GHIssue> iterator() {
        return new PagedIterator<GHIssue>(root.retrieve().asIterator(getApiTailUrl("issues?state=" + state.toString().toLowerCase(Locale.ENGLISH)), GHIssue[].class)) {
          @Override protected void wrapUp(GHIssue[] page) {
            for (GHIssue c : page) {
              c.wrap(GHRepository.this);
            }
          }
        };
      }
    };
  }

  public GHReleaseBuilder createRelease(String tag) {
    return new GHReleaseBuilder(this, tag);
  }

  public List<GHRelease> getReleases() throws IOException {
    return listReleases().asList();
  }

  protected String getOwnerName() {
    return owner.login;
  }

  public boolean hasIssues() {
    return has_issues;
  }

  public boolean hasWiki() {
    return has_wiki;
  }

  public boolean isFork() {
    return fork;
  }

  public int getForks() {
    return forks;
  }

  public boolean isPrivate() {
    return _private;
  }

  public boolean hasDownloads() {
    return has_downloads;
  }

  public int getWatchers() {
    return watchers;
  }

  public int getOpenIssueCount() {
    return open_issues;
  }

  public int getNetworkCount() {
    return network_count;
  }

  public int getSubscribersCount() {
    return subscribers_count;
  }

  public Date getPushedAt() {
    return GitHub.parseDate(pushed_at);
  }

  public Date getCreatedAt() {
    return GitHub.parseDate(created_at);
  }

  public String getMasterBranch() {
    return default_branch;
  }

  public int getSize() {
    return size;
  }

  @WithBridgeMethods(value = Set.class) public GHPersonSet<GHUser> getCollaborators() throws IOException {
    return new GHPersonSet<GHUser>(GHUser.wrap(root.retrieve().to("/repos/" + owner.login + "/" + name + "/collaborators", GHUser[].class), root));
  }

  public Set<String> getCollaboratorNames() throws IOException {
    Set<String> r = new HashSet<String>();
    for (GHUser u : GHUser.wrap(root.retrieve().to("/repos/" + owner.login + "/" + name + "/collaborators", GHUser[].class), root)) {
      r.add(u.login);
    }
    return r;
  }

  public Set<GHTeam> getTeams() throws IOException {
    return Collections.unmodifiableSet(new HashSet<GHTeam>(Arrays.asList(GHTeam.wrapUp(root.retrieve().to("/repos/" + owner.login + "/" + name + "/teams", GHTeam[].class), root.getOrganization(owner.login)))));
  }

  public void addCollaborators(GHUser... users) throws IOException {
    addCollaborators(asList(users));
  }

  public void addCollaborators(Collection<GHUser> users) throws IOException {
    modifyCollaborators(users, "PUT");
  }

  public void removeCollaborators(GHUser... users) throws IOException {
    removeCollaborators(asList(users));
  }

  public void removeCollaborators(Collection<GHUser> users) throws IOException {
    modifyCollaborators(users, "DELETE");
  }

  private void modifyCollaborators(Collection<GHUser> users, String method) throws IOException {
    verifyMine();
    for (GHUser user : users) {
      new Requester(root).method(method).to("/repos/" + owner.login + "/" + name + "/collaborators/" + user.getLogin());
    }
  }

  public void setEmailServiceHook(String address) throws IOException {
    Map<String, String> config = new HashMap<String, String>();
    config.put("address", address);
    new Requester(root).method("POST").with("name", "email").with("config", config).with("active", "true").to(String.format("/repos/%s/%s/hooks", owner.login, name));
  }

  private void edit(String key, String value) throws IOException {
    Requester requester = new Requester(root);
    if (!key.equals("name")) {
      requester.with("name", name);
    }
    requester.with(key, value).method("PATCH").to("/repos/" + owner.login + "/" + name);
  }

  public void enableIssueTracker(boolean v) throws IOException {
    edit("has_issues", String.valueOf(v));
  }

  public void enableWiki(boolean v) throws IOException {
    edit("has_wiki", String.valueOf(v));
  }

  public void enableDownloads(boolean v) throws IOException {
    edit("has_downloads", String.valueOf(v));
  }

  public void renameTo(String name) throws IOException {
    edit("name", name);
  }

  public void setDescription(String value) throws IOException {
    edit("description", value);
  }

  public void setHomepage(String value) throws IOException {
    edit("homepage", value);
  }

  public void delete() throws IOException {
    new Requester(root).method("DELETE").to("/repos/" + owner.login + "/" + name);
  }

  public GHRepository fork() throws IOException {
    return new Requester(root).method("POST").to("/repos/" + owner.login + "/" + name + "/forks", GHRepository.class).wrap(root);
  }

  public GHRepository forkTo(GHOrganization org) throws IOException {
    new Requester(root).to(String.format("/repos/%s/%s/forks?org=%s", owner.login, name, org.getLogin()));
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
    return root.retrieve().to("/repos/" + owner.login + '/' + name + "/pulls/" + i, GHPullRequest.class).wrapUp(this);
  }

  public List<GHPullRequest> getPullRequests(GHIssueState state) throws IOException {
    return listPullRequests(state).asList();
  }

  public PagedIterable<GHPullRequest> listPullRequests(final GHIssueState state) {
    return new PagedIterable<GHPullRequest>() {
      public PagedIterator<GHPullRequest> iterator() {
        return new PagedIterator<GHPullRequest>(root.retrieve().asIterator(String.format("/repos/%s/%s/pulls?state=%s", owner.login, name, state.name().toLowerCase(Locale.ENGLISH)), GHPullRequest[].class)) {
          @Override protected void wrapUp(GHPullRequest[] page) {
            for (GHPullRequest pr : page) {
              pr.wrap(GHRepository.this);
            }
          }
        };
      }
    };
  }

  public List<GHHook> getHooks() throws IOException {
    List<GHHook> list = new ArrayList<GHHook>(Arrays.asList(root.retrieve().to(String.format("/repos/%s/%s/hooks", owner.login, name), GHHook[].class)));
    for (GHHook h : list) {
      h.wrap(this);
    }
    return list;
  }

  public GHHook getHook(int id) throws IOException {
    return root.retrieve().to(String.format("/repos/%s/%s/hooks/%d", owner.login, name, id), GHHook.class).wrap(this);
  }

  public GHCompare getCompare(String id1, String id2) throws IOException {
    GHCompare compare = root.retrieve().to(String.format("/repos/%s/%s/compare/%s...%s", owner.login, name, id1, id2), GHCompare.class);
    return compare.wrap(this);
  }

  public GHCompare getCompare(GHCommit id1, GHCommit id2) throws IOException {
    return getCompare(id1.getSHA1(), id2.getSHA1());
  }

  public GHCompare getCompare(GHBranch id1, GHBranch id2) throws IOException {
    return getCompare(id1.getName(), id2.getName());
  }

  public GHRef[] getRefs() throws IOException {
    return root.retrieve().to(String.format("/repos/%s/%s/git/refs", owner.login, name), GHRef[].class);
  }

  public GHRef[] getRefs(String refType) throws IOException {
    return root.retrieve().to(String.format("/repos/%s/%s/git/refs/%s", owner.login, name, refType), GHRef[].class);
  }

  public GHCommit getCommit(String sha1) throws IOException {
    GHCommit c = commits.get(sha1);
    if (c == null) {
      c = root.retrieve().to(String.format("/repos/%s/%s/commits/%s", owner.login, name, sha1), GHCommit.class).wrapUp(this);
      commits.put(sha1, c);
    }
    return c;
  }

  public PagedIterable<GHCommit> listCommits() {
    return new PagedIterable<GHCommit>() {
      public PagedIterator<GHCommit> iterator() {
        return new PagedIterator<GHCommit>(root.retrieve().asIterator(String.format("/repos/%s/%s/commits", owner.login, name), GHCommit[].class)) {
          protected void wrapUp(GHCommit[] page) {
            for (GHCommit c : page) {
              c.wrapUp(GHRepository.this);
            }
          }
        };
      }
    };
  }

  public GHCommitQueryBuilder queryCommits() {
    return new GHCommitQueryBuilder(this);
  }

  public PagedIterable<GHCommitComment> listCommitComments() {
    return new PagedIterable<GHCommitComment>() {
      public PagedIterator<GHCommitComment> iterator() {
        return new PagedIterator<GHCommitComment>(root.retrieve().asIterator(String.format("/repos/%s/%s/comments", owner.login, name), GHCommitComment[].class)) {
          @Override protected void wrapUp(GHCommitComment[] page) {
            for (GHCommitComment c : page) {
              c.wrap(GHRepository.this);
            }
          }
        };
      }
    };
  }

  public PagedIterable<GHCommitStatus> listCommitStatuses(final String sha1) throws IOException {
    return new PagedIterable<GHCommitStatus>() {
      public PagedIterator<GHCommitStatus> iterator() {
        return new PagedIterator<GHCommitStatus>(root.retrieve().asIterator(String.format("/repos/%s/%s/statuses/%s", owner.login, name, sha1), GHCommitStatus[].class)) {
          @Override protected void wrapUp(GHCommitStatus[] page) {
            for (GHCommitStatus c : page) {
              c.wrapUp(root);
            }
          }
        };
      }
    };
  }

  public GHCommitStatus getLastCommitStatus(String sha1) throws IOException {
    List<GHCommitStatus> v = listCommitStatuses(sha1).asList();
    return v.isEmpty() ? null : v.get(0);
  }

  public GHCommitStatus createCommitStatus(String sha1, GHCommitState state, String targetUrl, String description) throws IOException {
    return new Requester(root).with("state", state.name().toLowerCase(Locale.ENGLISH)).with("target_url", targetUrl).with("description", description).to(String.format("/repos/%s/%s/statuses/%s", owner.login, this.name, sha1), GHCommitStatus.class).wrapUp(root);
  }

  public PagedIterable<GHEventInfo> listEvents() throws IOException {
    return new PagedIterable<GHEventInfo>() {
      public PagedIterator<GHEventInfo> iterator() {
        return new PagedIterator<GHEventInfo>(root.retrieve().asIterator(String.format("/repos/%s/%s/events", owner.login, name), GHEventInfo[].class)) {
          @Override protected void wrapUp(GHEventInfo[] page) {
            for (GHEventInfo c : page) {
              c.wrapUp(root);
            }
          }
        };
      }
    };
  }

  public GHHook createHook(String name, Map<String, String> config, Collection<GHEvent> events, boolean active) throws IOException {
    List<String> ea = null;
    if (events != null) {
      ea = new ArrayList<String>();
      for (GHEvent e : events) {
        ea.add(e.name().toLowerCase(Locale.ENGLISH));
      }
    }
    return new Requester(root).with("name", name).with("active", active)._with("config", config)._with("events", ea).to(String.format("/repos/%s/%s/hooks", owner.login, this.name), GHHook.class).wrap(this);
  }

  public GHHook createWebHook(URL url, Collection<GHEvent> events) throws IOException {
    return createHook("web", Collections.singletonMap("url", url.toExternalForm()), events, true);
  }

  public GHHook createWebHook(URL url) throws IOException {
    return createWebHook(url, null);
  }

  private void verifyMine() throws IOException {
    if (!root.login.equals(owner.login)) {
      throw new IOException("Operation not applicable to a repository owned by someone else: " + owner.login);
    }
  }

  public Set<URL> getPostCommitHooks() {
    return postCommitHooks;
  }

  private final Set<URL> postCommitHooks = new AbstractSet<URL>() {
    private List<URL> getPostCommitHooks() {
      try {
        List<URL> r = new ArrayList<URL>();
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
    return this;
  }

  public Map<String, GHBranch> getBranches() throws IOException {
    Map<String, GHBranch> r = new TreeMap<String, GHBranch>();
    for (GHBranch p : root.retrieve().to(getApiTailUrl("branches"), GHBranch[].class)) {
      p.wrap(this);
      r.put(p.getName(), p);
    }
    return r;
  }

  public Map<Integer, GHMilestone> getMilestones() throws IOException {
    Map<Integer, GHMilestone> milestones = new TreeMap<Integer, GHMilestone>();
    for (GHMilestone m : listMilestones(GHIssueState.OPEN)) {
      milestones.put(m.getNumber(), m);
    }
    return milestones;
  }

  public PagedIterable<GHMilestone> listMilestones(final GHIssueState state) {
    return new PagedIterable<GHMilestone>() {
      public PagedIterator<GHMilestone> iterator() {
        return new PagedIterator<GHMilestone>(root.retrieve().asIterator(getApiTailUrl("milestones?state=" + state.toString().toLowerCase(Locale.ENGLISH)), GHMilestone[].class)) {
          @Override protected void wrapUp(GHMilestone[] page) {
            for (GHMilestone c : page) {
              c.wrap(GHRepository.this);
            }
          }
        };
      }
    };
  }

  public GHMilestone getMilestone(int number) throws IOException {
    GHMilestone m = milestones.get(number);
    if (m == null) {
      m = root.retrieve().to(getApiTailUrl("milestones/" + number), GHMilestone.class);
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
    Requester requester = root.retrieve();
    String target = String.format("/repos/%s/%s/contents/%s", owner.login, name, path);
    if (ref != null) {
      target = target + "?ref=" + ref;
    }
    return requester.to(target, GHContent.class).wrap(this);
  }

  public List<GHContent> getDirectoryContent(String path) throws IOException {
    return getDirectoryContent(path, null);
  }

  public List<GHContent> getDirectoryContent(String path, String ref) throws IOException {
    Requester requester = root.retrieve();
    String target = String.format("/repos/%s/%s/contents/%s", owner.login, name, path);
    if (ref != null) {
      target = target + "?ref=" + ref;
    }
    GHContent[] files = requester.to(target, GHContent[].class);
    GHContent.wrap(files, this);
    return Arrays.asList(files);
  }

  public GHContent getReadme() throws Exception {
    return getFileContent("readme");
  }

  public GHContentUpdateResponse createContent(String content, String commitMessage, String path) throws IOException {
    return createContent(content, commitMessage, path, null);
  }

  public GHContentUpdateResponse createContent(String content, String commitMessage, String path, String branch) throws IOException {
    Requester requester = new Requester(root).with("path", path).with("message", commitMessage).with("content", DatatypeConverter.printBase64Binary(content.getBytes())).method("PUT");
    if (branch != null) {
      requester.with("branch", branch);
    }
    GHContentUpdateResponse response = requester.to(getApiTailUrl("contents/" + path), GHContentUpdateResponse.class);
    response.getContent().wrap(this);
    response.getCommit().wrapUp(this);
    return response;
  }

  public GHMilestone createMilestone(String title, String description) throws IOException {
    return new Requester(root).with("title", title).with("description", description).method("POST").to(getApiTailUrl("milestones"), GHMilestone.class).wrap(this);
  }

  @Override public String toString() {
    return "Repository:" + owner.login + ":" + name;
  }

  @Override public int hashCode() {
    return toString().hashCode();
  }

  @Override public boolean equals(Object obj) {
    if (obj instanceof GHRepository) {
      GHRepository that = (GHRepository) obj;
      return this.owner.login.equals(that.owner.login) && this.name.equals(that.name);
    }
    return false;
  }

  String getApiTailUrl(String tail) {
    return "/repos/" + owner.login + "/" + name + '/' + tail;
  }

  public GHRef createRef(String name, String sha) throws IOException {
    return new Requester(root).with("ref", name).with("sha", sha).method("POST").to(getApiTailUrl("git/refs"), GHRef.class);
  }

  public PagedIterable<GHRelease> listReleases() throws IOException {
    return new PagedIterable<GHRelease>() {
      public PagedIterator<GHRelease> iterator() {
        return new PagedIterator<GHRelease>(root.retrieve().asIterator(getApiTailUrl("releases"), GHRelease[].class)) {
          @Override protected void wrapUp(GHRelease[] page) {
            for (GHRelease c : page) {
              c.wrap(GHRepository.this);
            }
          }
        };
      }
    };
  }

  public PagedIterable<GHTag> listTags() throws IOException {
    return new PagedIterable<GHTag>() {
      public PagedIterator<GHTag> iterator() {
        return new PagedIterator<GHTag>(root.retrieve().asIterator(getApiTailUrl("tags"), GHTag[].class)) {
          @Override protected void wrapUp(GHTag[] page) {
            for (GHTag c : page) {
              c.wrap(GHRepository.this);
            }
          }
        };
      }
    };
  }
}
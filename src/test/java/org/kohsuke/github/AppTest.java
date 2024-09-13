package org.kohsuke.github;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.kohsuke.github.GHCommit.File;
import org.kohsuke.github.GHOrganization.Permission;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasProperty;

public class AppTest extends AbstractGitHubWireMockTest {
  static final String GITHUB_API_TEST_REPO = "github-api-test";

  @Test public void testRepoCRUD() throws Exception {
    String targetName = "github-api-test-rename2";
    cleanupUserRepository("github-api-test-rename");
    cleanupUserRepository(targetName);
    GHRepository r = gitHub.createRepository("github-api-test-rename", "a test repository", "http://github-api.kohsuke.org/", true);
    assertThat(r.hasIssues(), is(true));
    r.enableIssueTracker(false);
    r.enableDownloads(false);
    r.enableWiki(false);
    r.renameTo(targetName);
    getUser().getRepository(targetName).delete();
  }

  @Test public void testRepositoryWithAutoInitializationCRUD() throws Exception {
    String name = "github-api-test-autoinit";
    cleanupUserRepository(name);
    GHRepository r = gitHub.createRepository(name).description("a test repository for auto init").homepage("http://github-api.kohsuke.org/").autoInit(true).create();
    r.enableIssueTracker(false);
    r.enableDownloads(false);
    r.enableWiki(false);
    if (mockGitHub.isUseProxy()) {
      Thread.sleep(3000);
    }
    assertNotNull(r.getReadme());
    getUser().getRepository(name).delete();
  }

  private void cleanupUserRepository(final String name) throws IOException {
    if (mockGitHub.isUseProxy()) {
      cleanupRepository(getUser(getGitHubBeforeAfter()).getLogin() + "/" + name);
    }
  }

  @Test public void testCredentialValid() throws IOException {
    assertTrue(gitHub.isCredentialValid());
    GitHub connect = GitHub.connect("totally", "bogus");
    assertFalse(connect.isCredentialValid());
  }

  @Test public void testIssueWithNoComment() throws IOException {
    GHRepository repository = gitHub.getRepository("kohsuke/test");
    List<GHIssueComment> v = repository.getIssue(4).getComments();
    assertTrue(v.isEmpty());
    v = repository.getIssue(3).getComments();
    assertTrue(v.size() == 3);
  }

  @Test public void testCreateIssue() throws IOException {
    GHUser u = getUser();
    GHRepository repository = getTestRepository();
    GHMilestone milestone = repository.createMilestone("Test Milestone Title3", "Test Milestone");
    GHIssue o = repository.createIssue("testing").body("this is body").assignee(u).label("bug").label("question").milestone(milestone).create();
    assertNotNull(o);
    o.close();
  }

  @Test public void testCreateAndListDeployments() throws IOException {
    GHRepository repository = getTestRepository();
    GHDeployment deployment = repository.createDeployment("master").payload("{\"user\":\"atmos\",\"room_id\":123456}").description("question").environment("unittest").create();
    assertNotNull(deployment.getCreator());
    assertNotNull(deployment.getId());
    List<GHDeployment> deployments = repository.listDeployments(null, "master", null, "unittest").asList();
    assertNotNull(deployments);
    assertFalse(Iterables.isEmpty(deployments));
    GHDeployment unitTestDeployment = deployments.get(0);
    assertEquals("unittest", unitTestDeployment.getEnvironment());
    assertEquals("master", unitTestDeployment.getRef());
  }

  @Ignore(value = "Needs mocking check") @Test public void testGetDeploymentStatuses() throws IOException {
    GHRepository repository = getTestRepository();
    GHDeployment deployment = repository.createDeployment("master").description("question").payload("{\"user\":\"atmos\",\"room_id\":123456}").create();
    GHDeploymentStatus ghDeploymentStatus = deployment.createStatus(GHDeploymentState.SUCCESS).description("success").targetUrl("http://www.github.com").create();
    Iterable<GHDeploymentStatus> deploymentStatuses = deployment.listStatuses();
    assertNotNull(deploymentStatuses);
    assertEquals(1, Iterables.size(deploymentStatuses));
    assertEquals(ghDeploymentStatus.getId(), Iterables.get(deploymentStatuses, 0).getId());
  }

  @Test public void testGetIssues() throws Exception {
    List<GHIssue> closedIssues = gitHub.getOrganization("github-api").getRepository("github-api").getIssues(GHIssueState.CLOSED);
    assertTrue(closedIssues.size() > 150);
  }

  private GHRepository getTestRepository() throws IOException {
    return getTempRepository(GITHUB_API_TEST_REPO);
  }

  @Test public void testListIssues() throws IOException {
    Iterable<GHIssue> closedIssues = gitHub.getOrganization("github-api").getRepository("github-api").listIssues(GHIssueState.CLOSED);
    int x = 0;
    for (GHIssue issue : closedIssues) {
      assertNotNull(issue);
      x++;
    }
    assertTrue(x > 150);
  }

  @Test public void testRateLimit() throws IOException {
    assertThat(gitHub.getRateLimit(), notNullValue());
  }

  @Test public void testMyOrganizations() throws IOException {
    Map<String, GHOrganization> org = gitHub.getMyOrganizations();
    assertFalse(org.keySet().contains(null));
  }

  @Test public void testMyOrganizationsContainMyTeams() throws IOException {
    Map<String, Set<GHTeam>> teams = gitHub.getMyTeams();
    Map<String, GHOrganization> myOrganizations = gitHub.getMyOrganizations();
    assertTrue(myOrganizations.keySet().containsAll(teams.keySet()));
  }

  @Test public void testMyTeamsShouldIncludeMyself() throws IOException {
    Map<String, Set<GHTeam>> teams = gitHub.getMyTeams();
    for (Entry<String, Set<GHTeam>> teamsPerOrg : teams.entrySet()) {
      String organizationName = teamsPerOrg.getKey();
      for (GHTeam team : teamsPerOrg.getValue()) {
        String teamName = team.getName();
        assertTrue("Team " + teamName + " in organization " + organizationName + " does not contain myself", shouldBelongToTeam(organizationName, teamName));
      }
    }
  }

  @Test public void testUserPublicOrganizationsWhenThereAreSome() throws IOException {
    GHUser user = new GHUser();
    user.login = "kohsuke";
    Map<String, GHOrganization> orgs = gitHub.getUserPublicOrganizations(user);
    assertFalse(orgs.isEmpty());
  }

  @Test public void testUserPublicOrganizationsWhenThereAreNone() throws IOException {
    GHUser user = new GHUser();
    user.login = "bitwiseman";
    Map<String, GHOrganization> orgs = gitHub.getUserPublicOrganizations(user);
    assertTrue(orgs.isEmpty());
  }

  private boolean shouldBelongToTeam(String organizationName, String teamName) throws IOException {
    GHOrganization org = gitHub.getOrganization(organizationName);
    assertNotNull(org);
    GHTeam team = org.getTeamByName(teamName);
    assertNotNull(team);
    return team.hasMember(gitHub.getMyself());
  }

  @Ignore(value = "Needs mocking check") @Test public void testShouldFetchTeam() throws Exception {
    GHOrganization j = gitHub.getOrganization(GITHUB_API_TEST_ORG);
    GHTeam teamByName = j.getTeams().get("Core Developers");
    GHTeam teamById = gitHub.getTeam(teamByName.getId());
    assertNotNull(teamById);
    assertEquals(teamByName, teamById);
  }

  @Ignore(value = "Needs mocking check") @Test public void testFetchPullRequest() throws Exception {
    GHRepository r = gitHub.getOrganization("jenkinsci").getRepository("jenkins");
    assertEquals("master", r.getMasterBranch());
    r.getPullRequest(1);
    r.getPullRequests(GHIssueState.OPEN);
  }

  @Ignore(value = "Needs mocking check") @Test public void testFetchPullRequestAsList() throws Exception {
    GHRepository r = gitHub.getRepository("github-api/github-api");
    assertEquals("master", r.getMasterBranch());
    PagedIterable<GHPullRequest> i = r.listPullRequests(GHIssueState.CLOSED);
    List<GHPullRequest> prs = i.asList();
    assertNotNull(prs);
    assertTrue(prs.size() > 0);
  }

  @Ignore(value = "Needs mocking check") @Test public void testRepoPermissions() throws Exception {
    kohsuke();
    GHRepository r = gitHub.getOrganization(GITHUB_API_TEST_ORG).getRepository("github-api");
    assertTrue(r.hasPullAccess());
    r = gitHub.getOrganization("github").getRepository("hub");
    assertFalse(r.hasAdminAccess());
  }

  @Test public void testGetMyself() throws Exception {
    GHMyself me = gitHub.getMyself();
    assertNotNull(me);
    assertNotNull(gitHub.getUser("bitwiseman"));
    PagedIterable<GHRepository> ghRepositories = me.listRepositories();
    assertTrue(ghRepositories.iterator().hasNext());
  }

  @Ignore(value = "Needs mocking check") @Test public void testPublicKeys() throws Exception {
    List<GHKey> keys = gitHub.getMyself().getPublicKeys();
    assertFalse(keys.isEmpty());
  }

  @Test public void testOrgFork() throws Exception {
    cleanupRepository(GITHUB_API_TEST_ORG + "/rubywm");
    gitHub.getRepository("kohsuke/rubywm").forkTo(gitHub.getOrganization(GITHUB_API_TEST_ORG));
  }

  @Test public void testGetTeamsForRepo() throws Exception {
    kohsuke();
    assertEquals(2, gitHub.getOrganization(GITHUB_API_TEST_ORG).getRepository("testGetTeamsForRepo").getTeams().size());
  }

  @Test public void testMembership() throws Exception {
    Set<String> members = gitHub.getOrganization(GITHUB_API_TEST_ORG).getRepository("jenkins").getCollaboratorNames();
  }

  @Test public void testMemberOrgs() throws Exception {
    HashSet<GHOrganization> o = gitHub.getUser("kohsuke").getOrganizations();
    assertThat(o, hasItem(hasProperty("name", equalTo("CloudBees"))));
  }

  @Test public void testOrgTeams() throws Exception {
    kohsuke();
    int sz = 0;
    for (GHTeam t : gitHub.getOrganization(GITHUB_API_TEST_ORG).listTeams()) {
      assertNotNull(t.getName());
      sz++;
    }
    assertTrue(sz < 100);
  }

  @Test public void testOrgTeamByName() throws Exception {
    kohsuke();
    GHTeam e = gitHub.getOrganization(GITHUB_API_TEST_ORG).getTeamByName("Core Developers");
    assertNotNull(e);
  }

  @Test public void testOrgTeamBySlug() throws Exception {
    kohsuke();
    GHTeam e = gitHub.getOrganization(GITHUB_API_TEST_ORG).getTeamBySlug("core-developers");
    assertNotNull(e);
  }

  @Test public void testCommit() throws Exception {
    GHCommit commit = gitHub.getUser("jenkinsci").getRepository("jenkins").getCommit("08c1c9970af4d609ae754fbe803e06186e3206f7");
    assertEquals(1, commit.getParents().size());
    assertEquals(1, commit.getFiles().size());
    assertEquals("https://github.com/jenkinsci/jenkins/commit/08c1c9970af4d609ae754fbe803e06186e3206f7", commit.getHtmlUrl().toString());
    File f = commit.getFiles().get(0);
    assertEquals(48, f.getLinesChanged());
    assertEquals("modified", f.getStatus());
    assertEquals("changelog.html", f.getFileName());
    GHTree t = commit.getTree();
    assertThat(IOUtils.toString(t.getEntry("todo.txt").readAsBlob()), containsString("executor rendering"));
    assertNotNull(t.getEntry("war").asTree());
  }

  @Test public void testListCommits() throws Exception {
    List<String> sha1 = new ArrayList<String>();
    for (GHCommit c : gitHub.getUser("kohsuke").getRepository("empty-commit").listCommits()) {
      sha1.add(c.getSHA1());
    }
    assertEquals("fdfad6be4db6f96faea1f153fb447b479a7a9cb7", sha1.get(0));
    assertEquals(1, sha1.size());
  }

  public void testQueryCommits() throws Exception {
    List<String> sha1 = new ArrayList<String>();
    for (GHCommit c : gitHub.getUser("jenkinsci").getRepository("jenkins").queryCommits().since(new Date(1199174400000L)).until(1201852800000L).path("pom.xml").list()) {
      sha1.add(c.getSHA1());
    }
    assertEquals("1cccddb22e305397151b2b7b87b4b47d74ca337b", sha1.get(0));
    assertEquals(29, sha1.size());
  }

  @Ignore(value = "Needs mocking check") @Test public void testBranches() throws Exception {
    Map<String, GHBranch> b = gitHub.getUser("jenkinsci").getRepository("jenkins").getBranches();
  }

  @Test public void testCommitComment() throws Exception {
    GHRepository r = gitHub.getUser("jenkinsci").getRepository("jenkins");
    PagedIterable<GHCommitComment> comments = r.listCommitComments();
    List<GHCommitComment> batch = comments.iterator().nextPage();
    for (GHCommitComment comment : batch) {
      assertSame(comment.getOwner(), r);
    }
  }

  @Test public void testCreateCommitComment() throws Exception {
    GHCommit commit = gitHub.getUser("kohsuke").getRepository("sandbox-ant").getCommit("8ae38db0ea5837313ab5f39d43a6f73de3bd9000");
    GHCommitComment c = commit.createComment("[testing](http://kohsuse.org/)");
    c.update("updated text");
    c.delete();
  }

  @Test public void tryHook() throws Exception {
    kohsuke();
    GHRepository r = gitHub.getOrganization(GITHUB_API_TEST_ORG).getRepository("github-api");
    GHHook hook = r.createWebHook(new URL("http://www.google.com/"));
    if (mockGitHub.isUseProxy()) {
      r = getGitHubBeforeAfter().getOrganization(GITHUB_API_TEST_ORG).getRepository("github-api");
      for (GHHook h : r.getHooks()) {
        h.delete();
      }
    }
  }

  @Test public void testEventApi() throws Exception {
    for (GHEventInfo ev : gitHub.getEvents()) {
      if (ev.getType() == GHEvent.PULL_REQUEST) {
        GHEventPayload.PullRequest pr = ev.getPayload(GHEventPayload.PullRequest.class);
        assertThat(pr.getNumber(), is(pr.getPullRequest().getNumber()));
      }
    }
  }

  @Ignore(value = "Needs mocking check") @Test public void testApp() throws IOException {
  }

  private void tryDisablingIssueTrackers(GitHub gitHub) throws IOException {
    for (GHRepository r : gitHub.getOrganization("jenkinsci").getRepositories().values()) {
      if (r.hasIssues()) {
        if (r.getOpenIssueCount() == 0) {
          r.enableIssueTracker(false);
        } else {
        }
      }
    }
  }

  private void tryDisablingWiki(GitHub gitHub) throws IOException {
    for (GHRepository r : gitHub.getOrganization("jenkinsci").getRepositories().values()) {
      if (r.hasWiki()) {
        r.enableWiki(false);
      }
    }
  }

  private void tryUpdatingIssueTracker(GitHub gitHub) throws IOException {
    GHRepository r = gitHub.getOrganization("jenkinsci").getRepository("lib-task-reactor");
    r.enableIssueTracker(false);
  }

  private void tryRenaming(GitHub gitHub) throws IOException {
    gitHub.getUser("kohsuke").getRepository("test").renameTo("test2");
  }

  private void tryTeamCreation(GitHub gitHub) throws IOException {
    GHOrganization o = gitHub.getOrganization("HudsonLabs");
    GHTeam t = o.createTeam("auto team", Permission.PUSH);
    t.add(o.getRepository("auto-test"));
  }

  private void testPostCommitHook(GitHub gitHub) throws IOException {
    GHRepository r = gitHub.getMyself().getRepository("foo");
    Set<URL> hooks = r.getPostCommitHooks();
    hooks.add(new URL("http://kohsuke.org/test"));
    hooks.remove(new URL("http://kohsuke.org/test"));
  }

  @Test public void testOrgRepositories() throws IOException {
    kohsuke();
    GHOrganization j = gitHub.getOrganization("jenkinsci");
    long start = System.currentTimeMillis();
    Map<String, GHRepository> repos = j.getRepositories();
    long end = System.currentTimeMillis();
  }

  @Test public void testOrganization() throws IOException {
    kohsuke();
    GHOrganization j = gitHub.getOrganization(GITHUB_API_TEST_ORG);
    GHTeam t = j.getTeams().get("Core Developers");
    assertNotNull(j.getRepository("jenkins"));
  }

  @Test public void testCommitStatus() throws Exception {
    GHRepository r = gitHub.getRepository("github-api/github-api");
    GHCommitStatus state;
    List<GHCommitStatus> lst = r.listCommitStatuses("ecbfdd7315ef2cf04b2be7f11a072ce0bd00c396").asList();
    state = lst.get(0);
    assertEquals("testing!", state.getDescription());
    assertEquals("http://kohsuke.org/", state.getTargetUrl());
  }

  @Test public void testCommitShortInfo() throws Exception {
    GHRepository r = gitHub.getRepository("github-api/github-api");
    GHCommit commit = r.getCommit("86a2e245aa6d71d54923655066049d9e21a15f23");
    assertEquals(commit.getCommitShortInfo().getAuthor().getName(), "Kohsuke Kawaguchi");
    assertEquals(commit.getCommitShortInfo().getMessage(), "doc");
  }

  @Ignore(value = "Needs mocking check") @Test public void testPullRequestPopulate() throws Exception {
    GHRepository r = gitHub.getUser("kohsuke").getRepository("github-api");
    GHPullRequest p = r.getPullRequest(17);
    GHUser u = p.getUser();
    assertNotNull(u.getName());
  }

  @Test public void testCheckMembership() throws Exception {
    kohsuke();
    GHOrganization j = gitHub.getOrganization("jenkinsci");
    GHUser kohsuke = gitHub.getUser("kohsuke");
    GHUser b = gitHub.getUser("b");
    assertTrue(j.hasMember(kohsuke));
    assertFalse(j.hasMember(b));
    assertTrue(j.hasPublicMember(kohsuke));
    assertFalse(j.hasPublicMember(b));
  }

  @Ignore(value = "Needs mocking check") @Test public void testCreateRelease() throws Exception {
    kohsuke();
    GHRepository r = gitHub.getRepository("kohsuke2/testCreateRelease");
    String tagName = UUID.randomUUID().toString();
    String releaseName = "release-" + tagName;
    GHRelease rel = r.createRelease(tagName).name(releaseName).prerelease(false).create();
    Thread.sleep(3000);
    try {
      for (GHTag tag : r.listTags()) {
        if (tagName.equals(tag.getName())) {
          String ash = tag.getCommit().getSHA1();
          GHRef ref = r.createRef("refs/heads/" + releaseName, ash);
          assertEquals(ref.getRef(), "refs/heads/" + releaseName);
          for (Map.Entry<String, GHBranch> entry : r.getBranches().entrySet()) {
            if (releaseName.equals(entry.getValue().getName())) {
              return;
            }
          }
          fail("branch not found");
        }
      }
      fail("release creation failed! tag not found");
    }  finally {
      rel.delete();
    }
  }

  @Test public void testRef() throws IOException {
    GHRef masterRef = gitHub.getRepository("jenkinsci/jenkins").getRef("heads/master");
    assertEquals(mockGitHub.apiServer().baseUrl() + "/repos/jenkinsci/jenkins/git/refs/heads/master", masterRef.getUrl().toString());
  }

  @Test public void directoryListing() throws IOException {
    List<GHContent> children = gitHub.getRepository("jenkinsci/jenkins").getDirectoryContent("core");
    for (GHContent c : children) {
      if (c.isDirectory()) {
        for (GHContent d : c.listDirectoryContent()) {
        }
      }
    }
  }

  @Ignore(value = "Needs mocking check") @Test public void testAddDeployKey() throws IOException {
    GHRepository myRepository = getTestRepository();
    final GHDeployKey newDeployKey = myRepository.addDeployKey("test", "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDUt0RAycC5cS42JKh6SecfFZBR1RrF+2hYMctz4mk74/arBE+wFb7fnSHGzdGKX2h5CFOWODifRCJVhB7hlVxodxe+QkQQYAEL/x1WVCJnGgTGQGOrhOMj95V3UE5pQKhsKD608C+u5tSofcWXLToP1/wZ7U4/AHjqYi08OLsWToHCax55TZkvdt2jo0hbIoYU+XI9Q8Uv4ONDN1oabiOdgeKi8+crvHAuvNleiBhWVBzFh8KdfzaH5uNdw7ihhFjEd1vzqACsjCINCjdMfzl6jD9ExuWuE92nZJnucls2cEoNC6k2aPmrZDg9hA32FXVpyseY+bDUWFU6LO2LG6PB kohsuke@atlas");
    try {
      assertNotNull(newDeployKey.getId());
      GHDeployKey k = Iterables.find(myRepository.getDeployKeys(), new Predicate<GHDeployKey>() {
        public boolean apply(GHDeployKey deployKey) {
          return newDeployKey.getId() == deployKey.getId();
        }
      });
      assertNotNull(k);
    }  finally {
      newDeployKey.delete();
    }
  }

  @Ignore(value = "Needs mocking check") @Test public void testCommitStatusContext() throws IOException {
    GHRepository myRepository = getTestRepository();
    GHRef masterRef = myRepository.getRef("heads/master");
    GHCommitStatus commitStatus = myRepository.createCommitStatus(masterRef.getObject().getSha(), GHCommitState.SUCCESS, "http://www.example.com", "test", "test/context");
    assertEquals("test/context", commitStatus.getContext());
  }

  @Ignore(value = "Needs mocking check") @Test public void testMemberPagenation() throws IOException {
    Set<GHUser> all = new HashSet<GHUser>();
    for (GHUser u : gitHub.getOrganization(GITHUB_API_TEST_ORG).getTeamByName("Core Developers").listMembers()) {
      all.add(u);
    }
    assertFalse(all.isEmpty());
  }

  @Test public void testCommitSearch() throws IOException {
    PagedSearchIterable<GHCommit> r = gitHub.searchCommits().org("github-api").repo("github-api").author("kohsuke").sort(GHCommitSearchBuilder.Sort.COMMITTER_DATE).list();
    assertTrue(r.getTotalCount() > 0);
    GHCommit firstCommit = r.iterator().next();
    assertTrue(firstCommit.getFiles().size() > 0);
  }

  @Test public void testIssueSearch() throws IOException {
    PagedSearchIterable<GHIssue> r = gitHub.searchIssues().mentions("kohsuke").isOpen().sort(GHIssueSearchBuilder.Sort.UPDATED).list();
    assertTrue(r.getTotalCount() > 0);
    for (GHIssue issue : r) {
      assertThat(issue.getTitle(), notNullValue());
      PagedIterable<GHIssueComment> comments = issue.listComments();
      for (GHIssueComment comment : comments) {
        assertThat(comment, notNullValue());
      }
    }
  }

  @Test public void testReadme() throws IOException {
    GHContent readme = gitHub.getRepository("github-api-test-org/test-readme").getReadme();
    assertEquals(readme.getName(), "README.md");
    assertEquals(readme.getContent(), "This is a markdown readme.\n");
  }

  @Ignore(value = "Needs mocking check") @Test public void testTrees() throws IOException {
    GHTree masterTree = gitHub.getRepository("github-api/github-api").getTree("master");
    boolean foundReadme = false;
    for (GHTreeEntry e : masterTree.getTree()) {
      if ("readme".equalsIgnoreCase(e.getPath().replaceAll("\\.md", ""))) {
        foundReadme = true;
        break;
      }
    }
    assertTrue(foundReadme);
  }

  @Test public void testTreesRecursive() throws IOException {
    GHTree masterTree = gitHub.getRepository("github-api/github-api").getTreeRecursive("master", 1);
    boolean foundThisFile = false;
    for (GHTreeEntry e : masterTree.getTree()) {
      if (e.getPath().endsWith(AppTest.class.getSimpleName() + ".java")) {
        foundThisFile = true;
        break;
      }
    }
    assertTrue(foundThisFile);
  }

  @Test public void testRepoLabel() throws IOException {
    cleanupLabel("test");
    cleanupLabel("test2");
    GHRepository r = gitHub.getRepository("github-api-test-org/test-labels");
    List<GHLabel> lst = r.listLabels().asList();
    for (GHLabel l : lst) {
    }
    assertTrue(lst.size() > 5);
    GHLabel e = r.getLabel("enhancement");
    assertEquals("enhancement", e.getName());
    assertNotNull(e.getUrl());
    assertTrue(Pattern.matches("[0-9a-fA-F]{6}", e.getColor()));
    GHLabel t = null;
    GHLabel t2 = null;
    try {
      t = r.createLabel("test", "123456");
      t2 = r.getLabel("test");
      assertEquals(t.getName(), t2.getName());
      assertEquals(t.getColor(), "123456");
      assertEquals(t.getColor(), t2.getColor());
      assertEquals(t.getDescription(), "");
      assertEquals(t.getDescription(), t2.getDescription());
      assertEquals(t.getUrl(), t2.getUrl());
      t.setColor("000000");
      assertEquals(t.getColor(), "123456");
      t = r.getLabel("test");
      t.setDescription("this is also a test");
      GHLabel t3 = r.getLabel("test");
      assertEquals(t3.getColor(), "000000");
      assertEquals(t3.getDescription(), "this is also a test");
      t.delete();
      t = r.createLabel("test2", "123457", "this is a different test");
      t2 = r.getLabel("test2");
      assertEquals(t.getName(), t2.getName());
      assertEquals(t.getColor(), "123457");
      assertEquals(t.getColor(), t2.getColor());
      assertEquals(t.getDescription(), "this is a different test");
      assertEquals(t.getDescription(), t2.getDescription());
      assertEquals(t.getUrl(), t2.getUrl());
    }  finally {
      cleanupLabel("test");
      cleanupLabel("test2");
    }
  }

  void cleanupLabel(String name) {
    if (mockGitHub.isUseProxy()) {
      try {
        GHLabel t = getGitHubBeforeAfter().getRepository("github-api-test-org/test-labels").getLabel("test");
        t.delete();
      } catch (IOException e) {
      }
    }
  }

  @Test public void testSubscribers() throws IOException {
    boolean bitwiseman = false;
    GHRepository mr = gitHub.getRepository("bitwiseman/github-api");
    for (GHUser u : mr.listSubscribers()) {
      bitwiseman |= u.getLogin().equals("bitwiseman");
    }
    assertTrue(bitwiseman);
    boolean githubApiFound = false;
    for (GHRepository r : gitHub.getUser("bitwiseman").listRepositories()) {
      githubApiFound |= r.equals(mr);
    }
    assertTrue(githubApiFound);
  }

  @Test public void notifications() throws Exception {
    boolean found = false;
    for (GHThread t : gitHub.listNotifications().nonBlocking(true).read(true)) {
      if (!found) {
        found = true;
        t.markAsRead();
      }
      assertNotNull(t.getTitle());
      assertNotNull(t.getReason());
    }
    assertTrue(found);
    gitHub.listNotifications().markAsRead();
  }

  @Ignore(value = "Needs mocking check") @Test public void checkToString() throws Exception {
    GHUser u = gitHub.getUser("rails");
    GHRepository r = u.getRepository("rails");
  }

  @Test public void reactions() throws Exception {
    GHIssue i = gitHub.getRepository("github-api/github-api").getIssue(311);
    List<GHReaction> l;
    l = i.listReactions().asList();
    assertThat(l.size(), equalTo(1));
    assertThat(l.get(0).getUser().getLogin(), is("kohsuke"));
    assertThat(l.get(0).getContent(), is(ReactionContent.HEART));
    GHReaction a = i.createReaction(ReactionContent.HOORAY);
    a = i.createReaction(ReactionContent.HOORAY);
    assertThat(a.getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(a.getContent(), is(ReactionContent.HOORAY));
    a.delete();
    l = i.listReactions().asList();
    assertThat(l.size(), equalTo(1));
    a = i.createReaction(ReactionContent.PLUS_ONE);
    assertThat(a.getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(a.getContent(), is(ReactionContent.PLUS_ONE));
    a = i.createReaction(ReactionContent.CONFUSED);
    assertThat(a.getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(a.getContent(), is(ReactionContent.CONFUSED));
    a = i.createReaction(ReactionContent.EYES);
    assertThat(a.getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(a.getContent(), is(ReactionContent.EYES));
    a = i.createReaction(ReactionContent.ROCKET);
    assertThat(a.getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(a.getContent(), is(ReactionContent.ROCKET));
    l = i.listReactions().asList();
    assertThat(l.size(), equalTo(5));
    assertThat(l.get(0).getUser().getLogin(), is("kohsuke"));
    assertThat(l.get(0).getContent(), is(ReactionContent.HEART));
    assertThat(l.get(1).getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(l.get(1).getContent(), is(ReactionContent.PLUS_ONE));
    assertThat(l.get(2).getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(l.get(2).getContent(), is(ReactionContent.CONFUSED));
    assertThat(l.get(3).getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(l.get(3).getContent(), is(ReactionContent.EYES));
    assertThat(l.get(4).getUser().getLogin(), is(gitHub.getMyself().getLogin()));
    assertThat(l.get(4).getContent(), is(ReactionContent.ROCKET));
    l.get(1).delete();
    l.get(2).delete();
    l.get(3).delete();
    l.get(4).delete();
    l = i.listReactions().asList();
    assertThat(l.size(), equalTo(1));
  }

  @Test public void listOrgMemberships() throws Exception {
    GHMyself me = gitHub.getMyself();
    for (GHMembership m : me.listOrgMemberships()) {
      assertThat(m.getUser(), is((GHUser) me));
      assertNotNull(m.getState());
      assertNotNull(m.getRole());
    }
  }

  @Test public void blob() throws Exception {
    Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);
    GHRepository r = gitHub.getRepository("github-api/github-api");
    String sha1 = "a12243f2fc5b8c2ba47dd677d0b0c7583539584d";
    assertBlobContent(r.readBlob(sha1));
    GHBlob blob = r.getBlob(sha1);
    assertBlobContent(blob.read());
    assertThat(blob.getSha(), is("a12243f2fc5b8c2ba47dd677d0b0c7583539584d"));
    assertThat(blob.getSize(), is(1104L));
  }

  private void assertBlobContent(InputStream is) throws Exception {
    String content = new String(IOUtils.toByteArray(is), StandardCharsets.UTF_8);
    assertThat(content, containsString("Copyright (c) 2011- Kohsuke Kawaguchi and other contributors"));
    assertThat(content, containsString("FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR"));
    assertThat(content.length(), is(1104));
  }
}
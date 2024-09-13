package org.kohsuke.github;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.kohsuke.github.internal.Previews;
import static org.kohsuke.github.internal.Previews.INERTIA;
import static org.kohsuke.github.internal.Previews.MACHINE_MAN;

public class GitHub {
  @Nonnull private final GitHubClient client;

  @CheckForNull private GHMyself myself;

  private final ConcurrentMap<String, GHUser> users;

  private final ConcurrentMap<String, GHOrganization> orgs;

  public static GitHub connect() throws IOException {
    return GitHubBuilder.fromCredentials().build();
  }

  @Deprecated public static GitHub connectToEnterprise(String apiUrl, String oauthAccessToken) throws IOException {
    return connectToEnterpriseWithOAuth(apiUrl, null, oauthAccessToken);
  }

  public static GitHub connectToEnterpriseWithOAuth(String apiUrl, String login, String oauthAccessToken) throws IOException {
    return new GitHubBuilder().withEndpoint(apiUrl).withOAuthToken(oauthAccessToken, login).build();
  }

  @Deprecated public static GitHub connectToEnterprise(String apiUrl, String login, String password) throws IOException {
    return new GitHubBuilder().withEndpoint(apiUrl).withPassword(login, password).build();
  }

  public static GitHub connect(String login, String oauthAccessToken) throws IOException {
    return new GitHubBuilder().withOAuthToken(oauthAccessToken, login).build();
  }

  @Deprecated public static GitHub connect(String login, String oauthAccessToken, String password) throws IOException {
    return new GitHubBuilder().withOAuthToken(oauthAccessToken, login).withPassword(login, password).build();
  }

  @Deprecated public static GitHub connectUsingPassword(String login, String password) throws IOException {
    return new GitHubBuilder().withPassword(login, password).build();
  }

  public static GitHub connectUsingOAuth(String oauthAccessToken) throws IOException {
    return new GitHubBuilder().withOAuthToken(oauthAccessToken).build();
  }

  public static GitHub connectUsingOAuth(String githubServer, String oauthAccessToken) throws IOException {
    return new GitHubBuilder().withEndpoint(githubServer).withOAuthToken(oauthAccessToken).build();
  }

  public static GitHub connectAnonymously() throws IOException {
    return new GitHubBuilder().build();
  }

  public static GitHub connectToEnterpriseAnonymously(String apiUrl) throws IOException {
    return new GitHubBuilder().withEndpoint(apiUrl).build();
  }

  public static GitHub offline() {
    try {
      return new GitHubBuilder().withEndpoint("https://api.github.invalid").withConnector(HttpConnector.OFFLINE).build();
    } catch (IOException e) {
      throw new IllegalStateException("The offline implementation constructor should not connect", e);
    }
  }

  public boolean isAnonymous() {
    return client.isAnonymous();
  }

  public boolean isOffline() {
    return client.isOffline();
  }

  public HttpConnector getConnector() {
    return client.getConnector();
  }

  @Deprecated public void setConnector(HttpConnector connector) {
    client.setConnector(connector);
  }

  public String getApiUrl() {
    return client.getApiUrl();
  }

  @Nonnull public GHRateLimit getRateLimit() throws IOException {
    return client.getRateLimit();
  }

  @Nonnull @Deprecated public GHRateLimit lastRateLimit() {
    return client.lastRateLimit();
  }

  @Nonnull @Deprecated public GHRateLimit rateLimit() throws IOException {
    return client.rateLimit(RateLimitTarget.CORE);
  }

  @WithBridgeMethods(value = GHUser.class) public GHMyself getMyself() throws IOException {
    client.requireCredential();
    synchronized (this) {
      if (this.myself == null) {
        GHMyself u = createRequest().withUrlPath("/user").fetch(GHMyself.class);
        setMyself(u);
      }
      return myself;
    }
  }

  private void setMyself(GHMyself myself) {
    synchronized (this) {
      myself.wrapUp(this);
      this.myself = myself;
    }
  }

  public GHUser getUser(String login) throws IOException {
    GHUser u = users.get(login);
    if (u == null) {
      u = createRequest().withUrlPath("/users/" + login).fetch(GHUser.class);
      u.root = this;
      users.put(u.getLogin(), u);
    }
    return u;
  }

  public void refreshCache() {
    users.clear();
    orgs.clear();
  }

  protected GHUser getUser(GHUser orig) {
    GHUser u = users.get(orig.getLogin());
    if (u == null) {
      orig.root = this;
      users.put(orig.getLogin(), orig);
      return orig;
    }
    return u;
  }

  public GHOrganization getOrganization(String name) throws IOException {
    GHOrganization o = orgs.get(name);
    if (o == null) {
      o = createRequest().withUrlPath("/orgs/" + name).fetch(GHOrganization.class).wrapUp(this);
      orgs.put(name, o);
    }
    return o;
  }

  public PagedIterable<GHOrganization> listOrganizations() {
    return listOrganizations(null);
  }

  public PagedIterable<GHOrganization> listOrganizations(final String since) {
    return createRequest().with("since", since).withUrlPath("/organizations").toIterable(GHOrganization[].class, (item) -> item.wrapUp(this));
  }

  public GHRepository getRepository(String name) throws IOException {
    String[] tokens = name.split("/");
    if (tokens.length < 2) {
      throw new IllegalArgumentException("Repository name must be in format owner/repo");
    }
    return GHRepository.read(this, tokens[0], tokens[1]);
  }

  public GHRepository getRepositoryById(String id) throws IOException {
    return createRequest().withUrlPath("/repositories/" + id).fetch(GHRepository.class).wrap(this);
  }

  public PagedIterable<GHLicense> listLicenses() throws IOException {
    return createRequest().withUrlPath("/licenses").toIterable(GHLicense[].class, (item) -> item.wrap(this));
  }

  public PagedIterable<GHUser> listUsers() throws IOException {
    return createRequest().withUrlPath("/users").toIterable(GHUser[].class, (item) -> item.wrapUp(this));
  }

  public GHLicense getLicense(String key) throws IOException {
    return createRequest().withUrlPath("/licenses/" + key).fetch(GHLicense.class);
  }

  public PagedIterable<GHMarketplacePlan> listMarketplacePlans() throws IOException {
    return createRequest().withUrlPath("/marketplace_listing/plans").toIterable(GHMarketplacePlan[].class, (item) -> item.wrapUp(this));
  }

  public List<GHInvitation> getMyInvitations() throws IOException {
    return createRequest().withUrlPath("/user/repository_invitations").toIterable(GHInvitation[].class, (item) -> item.wrapUp(this)).toList();
  }

  public Map<String, GHOrganization> getMyOrganizations() throws IOException {
    GHOrganization[] orgs = createRequest().withUrlPath("/user/orgs").toIterable(GHOrganization[].class, (item) -> item.wrapUp(this)).toArray();
    Map<String, GHOrganization> r = new HashMap<>();
    for (GHOrganization o : orgs) {
      r.put(o.getLogin(), o);
    }
    return r;
  }

  public PagedIterable<GHMarketplaceUserPurchase> getMyMarketplacePurchases() throws IOException {
    return createRequest().withUrlPath("/user/marketplace_purchases").toIterable(GHMarketplaceUserPurchase[].class, (item) -> item.wrapUp(this));
  }

  public Map<String, GHOrganization> getUserPublicOrganizations(GHUser user) throws IOException {
    return getUserPublicOrganizations(user.getLogin());
  }

  public Map<String, GHOrganization> getUserPublicOrganizations(String login) throws IOException {
    GHOrganization[] orgs = createRequest().withUrlPath("/users/" + login + "/orgs").toIterable(GHOrganization[].class, (item) -> item.wrapUp(this)).toArray();
    Map<String, GHOrganization> r = new HashMap<>();
    for (GHOrganization o : orgs) {
      r.put(o.getLogin(), o);
    }
    return r;
  }

  public Map<String, Set<GHTeam>> getMyTeams() throws IOException {
    Map<String, Set<GHTeam>> allMyTeams = new HashMap<>();
    for (GHTeam team : createRequest().withUrlPath("/user/teams").toIterable(GHTeam[].class, (item) -> item.wrapUp(this)).toArray()) {
      String orgLogin = team.getOrganization().getLogin();
      Set<GHTeam> teamsPerOrg = allMyTeams.get(orgLogin);
      if (teamsPerOrg == null) {
        teamsPerOrg = new HashSet<>();
      }
      teamsPerOrg.add(team);
      allMyTeams.put(orgLogin, teamsPerOrg);
    }
    return allMyTeams;
  }

  @Deprecated public GHTeam getTeam(int id) throws IOException {
    return createRequest().withUrlPath("/teams/" + id).fetch(GHTeam.class).wrapUp(this);
  }

  public List<GHEventInfo> getEvents() throws IOException {
    return createRequest().withUrlPath("/events").toIterable(GHEventInfo[].class, (item) -> item.wrapUp(this)).toList();
  }

  public GHGist getGist(String id) throws IOException {
    return createRequest().withUrlPath("/gists/" + id).fetch(GHGist.class);
  }

  public GHGistBuilder createGist() {
    return new GHGistBuilder(this);
  }

  public <T extends GHEventPayload> T parseEventPayload(Reader r, Class<T> type) throws IOException {
    T t = GitHubClient.getMappingObjectReader(this).forType(type).readValue(r);
    t.wrapUp(this);
    return t;
  }

  @Deprecated public GHRepository createRepository(String name, String description, String homepage, boolean isPublic) throws IOException {
    return createRepository(name).description(description).homepage(homepage).private_(!isPublic).create();
  }

  public GHCreateRepositoryBuilder createRepository(String name) {
    return new GHCreateRepositoryBuilder(name, this, "/user/repos");
  }

  public GHAuthorization createToken(Collection<String> scope, String note, String noteUrl) throws IOException {
    Requester requester = createRequest().with("scopes", scope).with("note", note).with("note_url", noteUrl);
    return requester.method("POST").withUrlPath("/authorizations").fetch(GHAuthorization.class).wrap(this);
  }

  public GHAuthorization createToken(Collection<String> scope, String note, String noteUrl, Supplier<String> OTP) throws IOException {
    try {
      return createToken(scope, note, noteUrl);
    } catch (GHOTPRequiredException ex) {
      String OTPstring = OTP.get();
      Requester requester = createRequest().with("scopes", scope).with("note", note).with("note_url", noteUrl);
      requester.setHeader("x-github-otp", OTPstring);
      return requester.method("POST").withUrlPath("/authorizations").fetch(GHAuthorization.class).wrap(this);
    }
  }

  public GHAuthorization createOrGetAuth(String clientId, String clientSecret, List<String> scopes, String note, String note_url) throws IOException {
    Requester requester = createRequest().with("client_secret", clientSecret).with("scopes", scopes).with("note", note).with("note_url", note_url);
    return requester.method("PUT").withUrlPath("/authorizations/clients/" + clientId).fetch(GHAuthorization.class);
  }

  public void deleteAuth(long id) throws IOException {
    createRequest().method("DELETE").withUrlPath("/authorizations/" + id).send();
  }

  public GHAuthorization checkAuth(@Nonnull String clientId, @Nonnull String accessToken) throws IOException {
    return createRequest().withUrlPath("/applications/" + clientId + "/tokens/" + accessToken).fetch(GHAuthorization.class);
  }

  public GHAuthorization resetAuth(@Nonnull String clientId, @Nonnull String accessToken) throws IOException {
    return createRequest().method("POST").withUrlPath("/applications/" + clientId + "/tokens/" + accessToken).fetch(GHAuthorization.class);
  }

  public PagedIterable<GHAuthorization> listMyAuthorizations() throws IOException {
    return createRequest().withUrlPath("/authorizations").toIterable(GHAuthorization[].class, (item) -> item.wrap(this));
  }

  @Preview(value = MACHINE_MAN) @Deprecated public GHApp getApp() throws IOException {
    return createRequest().withPreview(MACHINE_MAN).withUrlPath("/app").fetch(GHApp.class).wrapUp(this);
  }

  public boolean isCredentialValid() {
    return client.isCredentialValid();
  }

  public GHMeta getMeta() throws IOException {
    return createRequest().withUrlPath("/meta").fetch(GHMeta.class);
  }

  public GHProject getProject(long id) throws IOException {
    return createRequest().withPreview(INERTIA).withUrlPath("/projects/" + id).fetch(GHProject.class).wrap(this);
  }

  public GHProjectColumn getProjectColumn(long id) throws IOException {
    return createRequest().withPreview(INERTIA).withUrlPath("/projects/columns/" + id).fetch(GHProjectColumn.class).wrap(this);
  }

  public GHProjectCard getProjectCard(long id) throws IOException {
    return createRequest().withPreview(INERTIA).withUrlPath("/projects/columns/cards/" + id).fetch(GHProjectCard.class).wrap(this);
  }

  public void checkApiUrlValidity() throws IOException {
    client.checkApiUrlValidity();
  }

  @Preview(value = Previews.CLOAK) @Deprecated public GHCommitSearchBuilder searchCommits() {
    return new GHCommitSearchBuilder(this);
  }

  public GHIssueSearchBuilder searchIssues() {
    return new GHIssueSearchBuilder(this);
  }

  public GHUserSearchBuilder searchUsers() {
    return new GHUserSearchBuilder(this);
  }

  public GHRepositorySearchBuilder searchRepositories() {
    return new GHRepositorySearchBuilder(this);
  }

  public GHContentSearchBuilder searchContent() {
    return new GHContentSearchBuilder(this);
  }

  public GHNotificationStream listNotifications() {
    return new GHNotificationStream(this, "/notifications");
  }

  public PagedIterable<GHRepository> listAllPublicRepositories() {
    return listAllPublicRepositories(null);
  }

  public PagedIterable<GHRepository> listAllPublicRepositories(final String since) {
    return createRequest().with("since", since).withUrlPath("/repositories").toIterable(GHRepository[].class, (item) -> item.wrap(this));
  }

  public Reader renderMarkdown(String text) throws IOException {
    return new InputStreamReader(createRequest().method("POST").with(new ByteArrayInputStream(text.getBytes("UTF-8"))).contentType("text/plain;charset=UTF-8").withUrlPath("/markdown/raw").fetchStream(Requester::copyInputStream), "UTF-8");
  }

  @Nonnull public static ObjectWriter getMappingObjectWriter() {
    return GitHubClient.getMappingObjectWriter();
  }

  @Nonnull public static ObjectReader getMappingObjectReader() {
    return GitHubClient.getMappingObjectReader(GitHub.offline());
  }

  @Nonnull GitHubClient getClient() {
    return client;
  }

  @Nonnull Requester createRequest() {
    return new Requester(client).injectMappingValue(this);
  }

  GHUser intern(GHUser user) throws IOException {
    if (user == null) {
      return user;
    }
    GHUser u = users.get(user.getLogin());
    if (u != null) {
      return u;
    }
    users.putIfAbsent(user.getLogin(), user);
    return user;
  }

  private static final Logger LOGGER = Logger.getLogger(GitHub.class.getName());

  GitHub(String apiUrl, HttpConnector connector, RateLimitHandler rateLimitHandler, AbuseLimitHandler abuseLimitHandler, GitHubRateLimitChecker rateLimitChecker, AuthorizationProvider authorizationProvider) throws IOException {
    if (authorizationProvider instanceof DependentAuthorizationProvider) {
      ((DependentAuthorizationProvider) authorizationProvider).bind(this);
    }
    this.client = new GitHubHttpUrlConnectionClient(apiUrl, connector, rateLimitHandler, abuseLimitHandler, rateLimitChecker, (myself) -> setMyself(myself), authorizationProvider);
    users = new ConcurrentHashMap<>();
    orgs = new ConcurrentHashMap<>();
  }

  private GitHub(GitHubClient client) {
    this.client = client;
    users = new ConcurrentHashMap<>();
    orgs = new ConcurrentHashMap<>();
  }

  public static abstract class DependentAuthorizationProvider implements AuthorizationProvider {
    private GitHub baseGitHub;

    private GitHub gitHub;

    private final AuthorizationProvider authorizationProvider;

    @BetaApi @Deprecated protected DependentAuthorizationProvider(AuthorizationProvider authorizationProvider) {
      this.authorizationProvider = authorizationProvider;
    }

    synchronized void bind(GitHub github) {
      if (baseGitHub != null) {
        throw new IllegalStateException("Already bound to another GitHub instance.");
      }
      this.baseGitHub = github;
    }

    protected synchronized final GitHub gitHub() {
      if (gitHub == null) {
        gitHub = new GitHub.AuthorizationRefreshGitHubWrapper(this.baseGitHub, authorizationProvider);
      }
      return gitHub;
    }
  }

  private static class AuthorizationRefreshGitHubWrapper extends GitHub {
    private final AuthorizationProvider authorizationProvider;

    AuthorizationRefreshGitHubWrapper(GitHub github, AuthorizationProvider authorizationProvider) {
      super(github.client);
      this.authorizationProvider = authorizationProvider;
      if (authorizationProvider instanceof DependentAuthorizationProvider) {
        ((DependentAuthorizationProvider) authorizationProvider).bind(this);
      }
    }

    @Nonnull @Override Requester createRequest() {
      try {
        return super.createRequest().setHeader("Authorization", authorizationProvider.getEncodedAuthorization()).rateLimit(RateLimitTarget.NONE);
      } catch (IOException e) {
        throw new GHException("Failed to create requester to refresh credentials", e);
      }
    }
  }
}
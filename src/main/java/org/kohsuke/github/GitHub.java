package org.kohsuke.github;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker.Std;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;
import static java.net.HttpURLConnection.*;
import static java.util.logging.Level.*;
import static org.kohsuke.github.Previews.*;

public class GitHub {
  final String login;

  final String encodedAuthorization;

  private final ConcurrentMap<String, GHUser> users;

  private final ConcurrentMap<String, GHOrganization> orgs;

  private GHMyself myself;

  private final String apiUrl;

  final RateLimitHandler rateLimitHandler;

  final AbuseLimitHandler abuseLimitHandler;

  private HttpConnector connector = HttpConnector.DEFAULT;

  private final Object headerRateLimitLock = new Object();

  private GHRateLimit headerRateLimit = null;

  private volatile GHRateLimit rateLimit = null;

  public static GitHub connect() throws IOException {
    return GitHubBuilder.fromCredentials().build();
  }

  public static GitHub connectToEnterprise(String apiUrl, String oauthAccessToken) throws IOException {
    return connectToEnterpriseWithOAuth(apiUrl, null, oauthAccessToken);
  }

  public static GitHub connectToEnterpriseWithOAuth(String apiUrl, String login, String oauthAccessToken) throws IOException {
    return new GitHubBuilder().withEndpoint(apiUrl).withOAuthToken(oauthAccessToken, login).build();
  }

  public static GitHub connectToEnterprise(String apiUrl, String login, String password) throws IOException {
    return new GitHubBuilder().withEndpoint(apiUrl).withPassword(login, password).build();
  }

  public static GitHub connect(String login, String oauthAccessToken) throws IOException {
    return new GitHubBuilder().withOAuthToken(oauthAccessToken, login).build();
  }

  public static GitHub connect(String login, String oauthAccessToken, String password) throws IOException {
    return new GitHubBuilder().withOAuthToken(oauthAccessToken, login).withPassword(login, password).build();
  }

  public static GitHub connectUsingPassword(String login, String password) throws IOException {
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
    return login == null && encodedAuthorization == null;
  }

  public boolean isOffline() {
    return connector == HttpConnector.OFFLINE;
  }

  public HttpConnector getConnector() {
    return connector;
  }

  public String getApiUrl() {
    return apiUrl;
  }

  public void setConnector(HttpConnector connector) {
    this.connector = connector;
  }

  void requireCredential() {
    if (isAnonymous()) {
      throw new IllegalStateException("This operation requires a credential but none is given to the GitHub constructor");
    }
  }

  URL getApiURL(String tailApiUrl) throws IOException {
    if (tailApiUrl.startsWith("/")) {
      if ("github.com".equals(apiUrl)) {
        return new URL(GITHUB_URL + tailApiUrl);
      } else {
        return new URL(apiUrl + tailApiUrl);
      }
    } else {
      return new URL(tailApiUrl);
    }
  }

  Requester retrieve() {
    return new Requester(this).method("GET");
  }

  public GHRateLimit getRateLimit() throws IOException {
    try {
      return rateLimit = retrieve().to("/rate_limit", JsonRateLimit.class).rate;
    } catch (FileNotFoundException e) {
      GHRateLimit r = new GHRateLimit();
      r.limit = r.remaining = 1000000;
      long hour = 60L * 60L;
      r.reset = new Date(System.currentTimeMillis() / 1000L + hour);
      return rateLimit = r;
    }
  }

  void updateRateLimit(@Nonnull GHRateLimit observed) {
    synchronized (headerRateLimitLock) {
      if (headerRateLimit == null || headerRateLimit.getResetDate().getTime() < observed.getResetDate().getTime() || headerRateLimit.remaining > observed.remaining) {
        headerRateLimit = observed;
        LOGGER.log(FINE, "Rate limit now: {0}", headerRateLimit);
      }
    }
  }

  @CheckForNull public GHRateLimit lastRateLimit() {
    synchronized (headerRateLimitLock) {
      return headerRateLimit;
    }
  }

  @Nonnull public GHRateLimit rateLimit() throws IOException {
    synchronized (headerRateLimitLock) {
      if (headerRateLimit != null) {
        return headerRateLimit;
      }
    }
    GHRateLimit rateLimit = this.rateLimit;
    if (rateLimit == null || rateLimit.getResetDate().getTime() < System.currentTimeMillis()) {
      rateLimit = getRateLimit();
    }
    return rateLimit;
  }

  @WithBridgeMethods(value = GHUser.class) public GHMyself getMyself() throws IOException {
    requireCredential();
    synchronized (this) {
      if (this.myself != null) {
        return myself;
      }
      GHMyself u = retrieve().to("/user", GHMyself.class);
      u.root = this;
      this.myself = u;
      return u;
    }
  }

  public GHUser getUser(String login) throws IOException {
    GHUser u = users.get(login);
    if (u == null) {
      u = retrieve().to("/users/" + login, GHUser.class);
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
      o = retrieve().to("/orgs/" + name, GHOrganization.class).wrapUp(this);
      orgs.put(name, o);
    }
    return o;
  }

  public PagedIterable<GHOrganization> listOrganizations() {
    return listOrganizations(null);
  }

  public PagedIterable<GHOrganization> listOrganizations(final String since) {
    return new PagedIterable<GHOrganization>() {
      @Override public PagedIterator<GHOrganization> _iterator(int pageSize) {
        return new PagedIterator<GHOrganization>(retrieve().with("since", since).asIterator("/organizations", GHOrganization[].class, pageSize)) {
          @Override protected void wrapUp(GHOrganization[] page) {
            for (GHOrganization c : page) {
              c.wrapUp(GitHub.this);
            }
          }
        };
      }
    };
  }

  public GHRepository getRepository(String name) throws IOException {
    String[] tokens = name.split("/");
    return retrieve().to("/repos/" + tokens[0] + '/' + tokens[1], GHRepository.class).wrap(this);
  }

  public PagedIterable<GHLicense> listLicenses() throws IOException {
    return new PagedIterable<GHLicense>() {
      public PagedIterator<GHLicense> _iterator(int pageSize) {
        return new PagedIterator<GHLicense>(retrieve().asIterator("/licenses", GHLicense[].class, pageSize)) {
          @Override protected void wrapUp(GHLicense[] page) {
            for (GHLicense c : page) {
              c.wrap(GitHub.this);
            }
          }
        };
      }
    };
  }

  public PagedIterable<GHUser> listUsers() throws IOException {
    return new PagedIterable<GHUser>() {
      public PagedIterator<GHUser> _iterator(int pageSize) {
        return new PagedIterator<GHUser>(retrieve().asIterator("/users", GHUser[].class, pageSize)) {
          @Override protected void wrapUp(GHUser[] page) {
            for (GHUser u : page) {
              u.wrapUp(GitHub.this);
            }
          }
        };
      }
    };
  }

  public GHLicense getLicense(String key) throws IOException {
    return retrieve().to("/licenses/" + key, GHLicense.class);
  }

  public List<GHInvitation> getMyInvitations() throws IOException {
    GHInvitation[] invitations = retrieve().to("/user/repository_invitations", GHInvitation[].class);
    for (GHInvitation i : invitations) {
      i.wrapUp(this);
    }
    return Arrays.asList(invitations);
  }

  public Map<String, GHOrganization> getMyOrganizations() throws IOException {
    GHOrganization[] orgs = retrieve().to("/user/orgs", GHOrganization[].class);
    Map<String, GHOrganization> r = new HashMap<String, GHOrganization>();
    for (GHOrganization o : orgs) {
      r.put(o.getLogin(), o.wrapUp(this));
    }
    return r;
  }

  public Map<String, Set<GHTeam>> getMyTeams() throws IOException {
    Map<String, Set<GHTeam>> allMyTeams = new HashMap<String, Set<GHTeam>>();
    for (GHTeam team : retrieve().to("/user/teams", GHTeam[].class)) {
      team.wrapUp(this);
      String orgLogin = team.getOrganization().getLogin();
      Set<GHTeam> teamsPerOrg = allMyTeams.get(orgLogin);
      if (teamsPerOrg == null) {
        teamsPerOrg = new HashSet<GHTeam>();
      }
      teamsPerOrg.add(team);
      allMyTeams.put(orgLogin, teamsPerOrg);
    }
    return allMyTeams;
  }

  public List<GHEventInfo> getEvents() throws IOException {
    GHEventInfo[] events = retrieve().to("/events", GHEventInfo[].class);
    for (GHEventInfo e : events) {
      e.wrapUp(this);
    }
    return Arrays.asList(events);
  }

  public GHGist getGist(String id) throws IOException {
    return retrieve().to("/gists/" + id, GHGist.class).wrapUp(this);
  }

  public GHGistBuilder createGist() {
    return new GHGistBuilder(this);
  }

  public <T extends GHEventPayload> T parseEventPayload(Reader r, Class<T> type) throws IOException {
    T t = MAPPER.readValue(r, type);
    t.wrapUp(this);
    return t;
  }

  public GHRepository createRepository(String name, String description, String homepage, boolean isPublic) throws IOException {
    return createRepository(name).description(description).homepage(homepage).private_(!isPublic).create();
  }

  public GHCreateRepositoryBuilder createRepository(String name) {
    return new GHCreateRepositoryBuilder(this, "/user/repos", name);
  }

  public GHAuthorization createToken(Collection<String> scope, String note, String noteUrl) throws IOException {
    Requester requester = new Requester(this).with("scopes", scope).with("note", note).with("note_url", noteUrl);
    return requester.method("POST").to("/authorizations", GHAuthorization.class).wrap(this);
  }

  public GHAuthorization createOrGetAuth(String clientId, String clientSecret, List<String> scopes, String note, String note_url) throws IOException {
    Requester requester = new Requester(this).with("client_secret", clientSecret).with("scopes", scopes).with("note", note).with("note_url", note_url);
    return requester.method("PUT").to("/authorizations/clients/" + clientId, GHAuthorization.class);
  }

  public void deleteAuth(long id) throws IOException {
    retrieve().method("DELETE").to("/authorizations/" + id);
  }

  public GHAuthorization checkAuth(@Nonnull String clientId, @Nonnull String accessToken) throws IOException {
    return retrieve().to("/applications/" + clientId + "/tokens/" + accessToken, GHAuthorization.class);
  }

  public GHAuthorization resetAuth(@Nonnull String clientId, @Nonnull String accessToken) throws IOException {
    return retrieve().method("POST").to("/applications/" + clientId + "/tokens/" + accessToken, GHAuthorization.class);
  }

  public PagedIterable<GHAuthorization> listMyAuthorizations() throws IOException {
    return new PagedIterable<GHAuthorization>() {
      public PagedIterator<GHAuthorization> _iterator(int pageSize) {
        return new PagedIterator<GHAuthorization>(retrieve().asIterator("/authorizations", GHAuthorization[].class, pageSize)) {
          @Override protected void wrapUp(GHAuthorization[] page) {
            for (GHAuthorization u : page) {
              u.wrap(GitHub.this);
            }
          }
        };
      }
    };
  }

  public boolean isCredentialValid() {
    try {
      retrieve().to("/user", GHUser.class);
      return true;
    } catch (IOException e) {
      if (LOGGER.isLoggable(FINE)) {
        LOGGER.log(FINE, "Exception validating credentials on " + this.apiUrl + " with login \'" + this.login + "\' " + e, e);
      }
      return false;
    }
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

  private static class GHApiInfo {
    private String rate_limit_url;

    void check(String apiUrl) throws IOException {
      if (rate_limit_url == null) {
        throw new IOException(apiUrl + " doesn\'t look like GitHub API URL");
      }
      new URL(rate_limit_url);
    }
  }

  public void checkApiUrlValidity() throws IOException {
    try {
      retrieve().to("/", GHApiInfo.class).check(apiUrl);
    } catch (IOException e) {
      if (isPrivateModeEnabled()) {
        throw (IOException) new IOException("GitHub Enterprise server (" + apiUrl + ") with private mode enabled").initCause(e);
      }
      throw e;
    }
  }

  private boolean isPrivateModeEnabled() {
    try {
      HttpURLConnection uc = getConnector().connect(getApiURL("/"));
      try {
        return uc.getResponseCode() == HTTP_UNAUTHORIZED && uc.getHeaderField("X-GitHub-Media-Type") != null;
      }  finally {
        try {
          IOUtils.closeQuietly(uc.getInputStream());
        } catch (IOException ignore) {
        }
        IOUtils.closeQuietly(uc.getErrorStream());
      }
    } catch (IOException e) {
      return false;
    }
  }

  @Preview @Deprecated public GHCommitSearchBuilder searchCommits() {
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
    return new PagedIterable<GHRepository>() {
      public PagedIterator<GHRepository> _iterator(int pageSize) {
        return new PagedIterator<GHRepository>(retrieve().with("since", since).asIterator("/repositories", GHRepository[].class, pageSize)) {
          @Override protected void wrapUp(GHRepository[] page) {
            for (GHRepository c : page) {
              c.wrap(GitHub.this);
            }
          }
        };
      }
    };
  }

  public Reader renderMarkdown(String text) throws IOException {
    return new InputStreamReader(new Requester(this).with(new ByteArrayInputStream(text.getBytes("UTF-8"))).contentType("text/plain;charset=UTF-8").asStream("/markdown/raw"), "UTF-8");
  }

  static URL parseURL(String s) {
    try {
      return s == null ? null : new URL(s);
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid URL: " + s);
    }
  }

  static Date parseDate(String timestamp) {
    if (timestamp == null) {
      return null;
    }
    for (String f : TIME_FORMATS) {
      try {
        SimpleDateFormat df = new SimpleDateFormat(f);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.parse(timestamp);
      } catch (ParseException e) {
      }
    }
    throw new IllegalStateException("Unable to parse the timestamp: " + timestamp);
  }

  static String printDate(Date dt) {
    return new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss\'Z\'").format(dt);
  }

  static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String[] TIME_FORMATS = { "yyyy/MM/dd HH:mm:ss ZZZZ", "yyyy-MM-dd\'T\'HH:mm:ss\'Z\'", "yyyy-MM-dd\'T\'HH:mm:ss.S\'Z\'" };

  static {
    MAPPER.setVisibilityChecker(new Std(NONE, NONE, NONE, NONE, ANY));
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    MAPPER.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
  }

  static final String GITHUB_URL = "https://api.github.com";

  private static final Logger LOGGER = Logger.getLogger(GitHub.class.getName());

  GitHub(String apiUrl, String login, String oauthAccessToken, String jwtToken, String password, HttpConnector connector, RateLimitHandler rateLimitHandler, AbuseLimitHandler abuseLimitHandler) throws IOException {
    if (apiUrl.endsWith("/")) {
      apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
    }
    this.apiUrl = apiUrl;
    if (null != connector) {
      this.connector = connector;
    }
    if (oauthAccessToken != null) {
      encodedAuthorization = "token " + oauthAccessToken;
    } else {
      if (jwtToken != null) {
        encodedAuthorization = "Bearer " + jwtToken;
      } else {
        if (password != null) {
          String authorization = (login + ':' + password);
          String charsetName = Charsets.UTF_8.name();
          encodedAuthorization = "Basic " + new String(Base64.encodeBase64(authorization.getBytes(charsetName)), charsetName);
        } else {
          encodedAuthorization = null;
        }
      }
    }
    users = new ConcurrentHashMap<String, GHUser>();
    orgs = new ConcurrentHashMap<String, GHOrganization>();
    this.rateLimitHandler = rateLimitHandler;
    this.abuseLimitHandler = abuseLimitHandler;
    if (login == null && encodedAuthorization != null && jwtToken == null) {
      login = getMyself().getLogin();
    }
    this.login = login;
  }

  public GHRepository getRepositoryById(String id) throws IOException {
    return retrieve().to("/repositories/" + id, GHRepository.class).wrap(this);
  }

  public GHTeam getTeam(int id) throws IOException {
    return retrieve().to("/teams/" + id, GHTeam.class).wrapUp(this);
  }

  @Preview @Deprecated public GHApp getApp() throws IOException {
    return retrieve().withPreview(MACHINE_MAN).to("/app", GHApp.class).wrapUp(this);
  }
}
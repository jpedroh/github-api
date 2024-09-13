package org.kohsuke.github;
import org.apache.commons.io.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Properties;

public class GitHubBuilder {
  String endpoint = GitHub.GITHUB_URL;

  String user;

  String password;

  String oauthToken;

  private HttpConnector connector;

  public GitHubBuilder() {
  }

  public static GitHubBuilder fromCredentials() throws IOException {
    Exception cause = null;
    GitHubBuilder builder;
    try {
      builder = fromPropertyFile();
      if (builder.user != null) {
        return builder;
      } else {
        builder = fromEnvironment();
        if (builder.user != null) {
          return builder;
        } else {
          throw new IOException("Failed to resolve credentials from ~/.github or the environment.");
        }
      }
    } catch (FileNotFoundException e) {
      cause = e;
    }
    builder = fromEnvironment();
    if (builder.user != null) {
      return builder;
    } else {
      throw (IOException) new IOException("Failed to resolve credentials from ~/.github or the environment.").initCause(cause);
    }
  }

  public static GitHubBuilder fromEnvironment(String loginVariableName, String passwordVariableName, String oauthVariableName) throws IOException {
    return fromEnvironment(loginVariableName, passwordVariableName, oauthVariableName, "");
  }

  private static void loadIfSet(String envName, Properties p, String propName) {
    String v = System.getenv(envName);
    if (v != null) {
      p.put(propName, v);
    }
  }

  public static GitHubBuilder fromEnvironment(String loginVariableName, String passwordVariableName, String oauthVariableName, String endpointVariableName) throws IOException {
    Properties env = new Properties();
    loadIfSet(loginVariableName, env, "login");
    loadIfSet(passwordVariableName, env, "password");
    loadIfSet(oauthVariableName, env, "oauth");
    loadIfSet(endpointVariableName, env, "endpoint");
    return fromProperties(env);
  }

  public static GitHubBuilder fromEnvironment() throws IOException {
    Properties props = new Properties();
    for (Entry<String, String> e : System.getenv().entrySet()) {
      String name = e.getKey().toLowerCase(Locale.ENGLISH);
      if (name.startsWith("github_")) {
        name = name.substring(7);
      }
      props.put(name, e.getValue());
    }
    return fromProperties(props);
  }

  public static GitHubBuilder fromPropertyFile() throws IOException {
    File homeDir = new File(System.getProperty("user.home"));
    File propertyFile = new File(homeDir, ".github");
    return fromPropertyFile(propertyFile.getPath());
  }

  public static GitHubBuilder fromPropertyFile(String propertyFileName) throws IOException {
    Properties props = new Properties();
    FileInputStream in = null;
    try {
      in = new FileInputStream(propertyFileName);
      props.load(in);
    }  finally {
      IOUtils.closeQuietly(in);
    }
    return fromProperties(props);
  }

  public static GitHubBuilder fromProperties(Properties props) {
    GitHubBuilder self = new GitHubBuilder();
    self.withOAuthToken(props.getProperty("oauth"), props.getProperty("login"));
    self.withPassword(props.getProperty("login"), props.getProperty("password"));
    self.withEndpoint(props.getProperty("endpoint", GitHub.GITHUB_URL));
    return self;
  }

  public GitHubBuilder withEndpoint(String endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public GitHubBuilder withPassword(String user, String password) {
    this.user = user;
    this.password = password;
    return this;
  }

  public GitHubBuilder withOAuthToken(String oauthToken) {
    return withOAuthToken(oauthToken, null);
  }

  public GitHubBuilder withOAuthToken(String oauthToken, String user) {
    this.oauthToken = oauthToken;
    this.user = user;
    return this;
  }

  public GitHubBuilder withConnector(HttpConnector connector) {
    this.connector = connector;
    return this;
  }

  public GitHubBuilder withProxy(final Proxy p) {
    return withConnector(new HttpConnector() {
      public HttpURLConnection connect(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection(p);
      }
    });
  }

  public GitHub build() throws IOException {
    return new GitHub(endpoint, user, oauthToken, password, connector, rateLimitHandler);
  }

  private RateLimitHandler rateLimitHandler = RateLimitHandler.WAIT;

  public GitHubBuilder withRateLimitHandler(RateLimitHandler handler) {
    this.rateLimitHandler = handler;
    return this;
  }
}
package org.kohsuke.github;
import com.fasterxml.jackson.databind.JsonMappingException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.annotation.WillClose;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import static java.util.Arrays.asList;
import static java.util.logging.Level.*;
import static org.apache.commons.lang.StringUtils.defaultString;
import static org.kohsuke.github.GitHub.MAPPER;

class Requester {
  private final GitHub root;

  private final List<Entry> args = new ArrayList<Entry>();

  private final Map<String, String> headers = new LinkedHashMap<String, String>();

  private String method = "POST";

  private String contentType = null;

  private InputStream body;

  private HttpURLConnection uc;

  private boolean forceBody;

  private static class Entry {
    String key;

    Object value;

    private Entry(String key, Object value) {
      this.key = key;
      this.value = value;
    }
  }

  Requester(GitHub root) {
    this.root = root;
  }

  public void setHeader(String name, String value) {
    headers.put(name, value);
  }

  public Requester withHeader(String name, String value) {
    setHeader(name, value);
    return this;
  }

  Requester withPreview(String name) {
    return withHeader("Accept", name);
  }

  @Deprecated public Requester withCredential() {
    return this;
  }

  public Requester with(String key, int value) {
    return _with(key, value);
  }

  public Requester with(String key, Integer value) {
    if (value != null) {
      _with(key, value);
    }
    return this;
  }

  public Requester with(String key, boolean value) {
    return _with(key, value);
  }

  public Requester with(String key, Boolean value) {
    return _with(key, value);
  }

  public Requester with(String key, Enum e) {
    if (e == null) {
      return _with(key, null);
    }
    return with(key, e.toString().toLowerCase(Locale.ENGLISH).replace('_', '-'));
  }

  public Requester with(String key, String value) {
    return _with(key, value);
  }

  public Requester with(String key, Collection<String> value) {
    return _with(key, value);
  }

  public Requester with(String key, Map<String, String> value) {
    return _with(key, value);
  }

  public Requester with(@WillClose InputStream body) {
    this.body = body;
    return this;
  }

  public Requester withNullable(String key, Object value) {
    args.add(new Entry(key, value));
    return this;
  }

  public Requester _with(String key, Object value) {
    if (value != null) {
      args.add(new Entry(key, value));
    }
    return this;
  }

  public Requester set(String key, Object value) {
    for (Entry e : args) {
      if (e.key.equals(key)) {
        e.value = value;
        return this;
      }
    }
    return _with(key, value);
  }

  public Requester method(String method) {
    this.method = method;
    return this;
  }

  public Requester contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  Requester inBody() {
    forceBody = true;
    return this;
  }

  public void to(String tailApiUrl) throws IOException {
    to(tailApiUrl, null);
  }

  public <T extends java.lang.Object> T to(String tailApiUrl, Class<T> type) throws IOException {
    return _to(tailApiUrl, type, null);
  }

  public <T extends java.lang.Object> T to(String tailApiUrl, T existingInstance) throws IOException {
    return _to(tailApiUrl, null, existingInstance);
  }

  @Deprecated public <T extends java.lang.Object> T to(String tailApiUrl, Class<T> type, String method) throws IOException {
    return method(method).to(tailApiUrl, type);
  }

  @SuppressFBWarnings(value = "SBSC_USE_STRINGBUFFER_CONCATENATION") private <T extends java.lang.Object> T _to(String tailApiUrl, Class<T> type, T instance) throws IOException {
    if (!isMethodWithBody() && !args.isEmpty()) {
      boolean questionMarkFound = tailApiUrl.indexOf('?') != -1;
      tailApiUrl += questionMarkFound ? '&' : '?';
      for (Iterator<Entry> it = args.listIterator(); it.hasNext(); ) {
        Entry arg = it.next();
        tailApiUrl += arg.key + '=' + URLEncoder.encode(arg.value.toString(), "UTF-8");
        if (it.hasNext()) {
          tailApiUrl += '&';
        }
      }
    }
    while (true) {
      setupConnection(root.getApiURL(tailApiUrl));
      buildRequest();
      try {
        T result = parse(type, instance);
        if (type != null && type.isArray()) {
          final String links = uc.getHeaderField("link");
          if (links != null && links.contains("rel=\"next\"")) {
            Pattern nextLinkPattern = Pattern.compile(".*<(.*)>; rel=\"next\"");
            Matcher nextLinkMatcher = nextLinkPattern.matcher(links);
            if (nextLinkMatcher.find()) {
              final String link = nextLinkMatcher.group(1);
              T nextResult = _to(link, type, instance);
              final int resultLength = Array.getLength(result);
              final int nextResultLength = Array.getLength(nextResult);
              T concatResult = (T) Array.newInstance(type.getComponentType(), resultLength + nextResultLength);
              System.arraycopy(result, 0, concatResult, 0, resultLength);
              System.arraycopy(nextResult, 0, concatResult, resultLength, nextResultLength);
              result = concatResult;
            }
          }
        }
        return result;
      } catch (IOException e) {
        handleApiError(e);
      } finally {
        noteRateLimit(tailApiUrl);
      }
    }
  }

  public int asHttpStatusCode(String tailApiUrl) throws IOException {
    while (true) {
      method("GET");
      setupConnection(root.getApiURL(tailApiUrl));
      buildRequest();
      try {
        return uc.getResponseCode();
      } catch (IOException e) {
        handleApiError(e);
      } finally {
        noteRateLimit(tailApiUrl);
      }
    }
  }

  public InputStream asStream(String tailApiUrl) throws IOException {
    while (true) {
      setupConnection(root.getApiURL(tailApiUrl));
      buildRequest();
      try {
        return wrapStream(uc.getInputStream());
      } catch (IOException e) {
        handleApiError(e);
      } finally {
        noteRateLimit(tailApiUrl);
      }
    }
  }

  private void noteRateLimit(String tailApiUrl) {
    if ("/rate_limit".equals(tailApiUrl)) {
      return;
    }
    if (tailApiUrl.startsWith("/search")) {
      return;
    }
    String limit = uc.getHeaderField("X-RateLimit-Limit");
    if (StringUtils.isBlank(limit)) {
      return;
    }
    String remaining = uc.getHeaderField("X-RateLimit-Remaining");
    if (StringUtils.isBlank(remaining)) {
      return;
    }
    String reset = uc.getHeaderField("X-RateLimit-Reset");
    if (StringUtils.isBlank(reset)) {
      return;
    }
    GHRateLimit observed = new GHRateLimit();
    try {
      observed.limit = Integer.parseInt(limit);
    } catch (NumberFormatException e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Malformed X-RateLimit-Limit header value " + limit, e);
      }
      return;
    }
    try {
      observed.remaining = Integer.parseInt(remaining);
    } catch (NumberFormatException e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Malformed X-RateLimit-Remaining header value " + remaining, e);
      }
      return;
    }
    try {
      observed.reset = new Date(Long.parseLong(reset));
      root.updateRateLimit(observed);
    } catch (NumberFormatException e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Malformed X-RateLimit-Reset header value " + reset, e);
      }
    }
  }

  public String getResponseHeader(String header) {
    return uc.getHeaderField(header);
  }

  private void buildRequest() throws IOException {
    if (isMethodWithBody()) {
      uc.setDoOutput(true);
      if (body == null) {
        uc.setRequestProperty("Content-type", defaultString(contentType, "application/json"));
        Map json = new HashMap();
        for (Entry e : args) {
          json.put(e.key, e.value);
        }
        MAPPER.writeValue(uc.getOutputStream(), json);
      } else {
        uc.setRequestProperty("Content-type", defaultString(contentType, "application/x-www-form-urlencoded"));
        try {
          byte[] bytes = new byte[32768];
          int read = 0;
          while ((read = body.read(bytes)) != -1) {
            uc.getOutputStream().write(bytes, 0, read);
          }
        }  finally {
          body.close();
        }
      }
    }
  }

  private boolean isMethodWithBody() {
    return forceBody || !METHODS_WITHOUT_BODY.contains(method);
  }

  <T extends java.lang.Object> Iterator<T> asIterator(String tailApiUrl, Class<T> type, int pageSize) {
    method("GET");
    if (pageSize != 0) {
      args.add(new Entry("per_page", pageSize));
    }
    StringBuilder s = new StringBuilder(tailApiUrl);
    if (!args.isEmpty()) {
      boolean first = true;
      try {
        for (Entry a : args) {
          s.append(first ? '?' : '&');
          first = false;
          s.append(URLEncoder.encode(a.key, "UTF-8"));
          s.append('=');
          s.append(URLEncoder.encode(a.value.toString(), "UTF-8"));
        }
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }
    }
    try {
      return new PagingIterator<T>(type, tailApiUrl, root.getApiURL(s.toString()));
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  class PagingIterator<T extends java.lang.Object> implements Iterator<T> {
    private final Class<T> type;

    private final String tailApiUrl;

    private T next;

    private URL url;

    PagingIterator(Class<T> type, String tailApiUrl, URL url) {
      this.type = type;
      this.tailApiUrl = tailApiUrl;
      this.url = url;
    }

    public boolean hasNext() {
      fetch();
      return next != null;
    }

    public T next() {
      fetch();
      T r = next;
      if (r == null) {
        throw new NoSuchElementException();
      }
      next = null;
      return r;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    private void fetch() {
      if (next != null) {
        return;
      }
      if (url == null) {
        return;
      }
      try {
        while (true) {
          setupConnection(url);
          try {
            next = parse(type, null);
            assert next != null;
            findNextURL();
            return;
          } catch (IOException e) {
            handleApiError(e);
          } finally {
            noteRateLimit(tailApiUrl);
          }
        }
      } catch (IOException e) {
        throw new Error(e);
      }
    }

    private void findNextURL() throws MalformedURLException {
      url = null;
      String link = uc.getHeaderField("Link");
      if (link == null) {
        return;
      }
      for (String token : link.split(", ")) {
        if (token.endsWith("rel=\"next\"")) {
          int idx = token.indexOf('>');
          url = new URL(token.substring(1, idx));
          return;
        }
      }
    }
  }

  private void setupConnection(URL url) throws IOException {
    uc = root.getConnector().connect(url);
    if (root.encodedAuthorization != null) {
      uc.setRequestProperty("Authorization", root.encodedAuthorization);
    }
    for (Map.Entry<String, String> e : headers.entrySet()) {
      String v = e.getValue();
      if (v != null) {
        uc.setRequestProperty(e.getKey(), v);
      }
    }
    setRequestMethod(uc);
    uc.setRequestProperty("Accept-Encoding", "gzip");
  }

  private void setRequestMethod(HttpURLConnection uc) throws IOException {
    try {
      uc.setRequestMethod(method);
    } catch (ProtocolException e) {
      try {
        Field $method = HttpURLConnection.class.getDeclaredField("method");
        $method.setAccessible(true);
        $method.set(uc, method);
      } catch (Exception x) {
        throw (IOException) new IOException("Failed to set the custom verb").initCause(x);
      }
      try {
        Field $delegate = uc.getClass().getDeclaredField("delegate");
        $delegate.setAccessible(true);
        Object delegate = $delegate.get(uc);
        if (delegate instanceof HttpURLConnection) {
          HttpURLConnection nested = (HttpURLConnection) delegate;
          setRequestMethod(nested);
        }
      } catch (NoSuchFieldException x) {
      } catch (IllegalAccessException x) {
        throw (IOException) new IOException("Failed to set the custom verb").initCause(x);
      }
    }
    if (!uc.getRequestMethod().equals(method)) {
      throw new IllegalStateException("Failed to set the request method to " + method);
    }
  }

  private <T extends java.lang.Object> T parse(Class<T> type, T instance) throws IOException {
    return parse(type, instance, 2);
  }

  private <T extends java.lang.Object> T parse(Class<T> type, T instance, int timeouts) throws IOException {
    InputStreamReader r = null;
    int responseCode = -1;
    String responseMessage = null;
    try {
      responseCode = uc.getResponseCode();
      responseMessage = uc.getResponseMessage();
      if (responseCode == 304) {
        return null;
      }
      if (responseCode == 204 && type != null && type.isArray()) {
        return type.cast(Array.newInstance(type.getComponentType(), 0));
      }
      r = new InputStreamReader(wrapStream(uc.getInputStream()), "UTF-8");
      String data = IOUtils.toString(r);
      if (type != null) {
        try {
          return MAPPER.readValue(data, type);
        } catch (JsonMappingException e) {
          throw (IOException) new IOException("Failed to deserialize " + data).initCause(e);
        }
      }
      if (instance != null) {
        return MAPPER.readerForUpdating(instance).<T>readValue(data);
      }
      return null;
    } catch (FileNotFoundException e) {
      throw e;
    } catch (IOException e) {
      if (e instanceof SocketTimeoutException && timeouts > 0) {
        LOGGER.log(Level.INFO, "timed out accessing " + uc.getURL() + "; will try " + timeouts + " more time(s)", e);
        return parse(type, instance, timeouts - 1);
      }
      throw new HttpException(responseCode, responseMessage, uc.getURL(), e);
    } finally {
      IOUtils.closeQuietly(r);
    }
  }

  private InputStream wrapStream(InputStream in) throws IOException {
    String encoding = uc.getContentEncoding();
    if (encoding == null || in == null) {
      return in;
    }
    if (encoding.equals("gzip")) {
      return new GZIPInputStream(in);
    }
    throw new UnsupportedOperationException("Unexpected Content-Encoding: " + encoding);
  }

  void handleApiError(IOException e) throws IOException {
    int responseCode;
    try {
      responseCode = uc.getResponseCode();
    } catch (IOException e2) {
      if (LOGGER.isLoggable(FINE)) {
        LOGGER.log(FINE, "Silently ignore exception retrieving response code for \'" + uc.getURL() + "\'" + " handling exception " + e, e);
      }
      throw e;
    }
    InputStream es = wrapStream(uc.getErrorStream());
    if (es != null) {
      try {
        String error = IOUtils.toString(es, "UTF-8");
        if (e instanceof FileNotFoundException) {
          e = (IOException) new FileNotFoundException(error).initCause(e);
        } else {
          if (e instanceof HttpException) {
            HttpException http = (HttpException) e;
            e = new HttpException(error, http.getResponseCode(), http.getResponseMessage(), http.getUrl(), e);
          } else {
            e = (IOException) new IOException(error).initCause(e);
          }
        }
      }  finally {
        IOUtils.closeQuietly(es);
      }
    }
    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
      throw e;
    }
    if ("0".equals(uc.getHeaderField("X-RateLimit-Remaining"))) {
      root.rateLimitHandler.onError(e, uc);
      return;
    }
    if (responseCode == HttpURLConnection.HTTP_FORBIDDEN && uc.getHeaderField("Retry-After") != null) {
      this.root.abuseLimitHandler.onError(e, uc);
      return;
    }
    throw e;
  }

  private static final List<String> METHODS_WITHOUT_BODY = asList("GET", "DELETE");

  private static final Logger LOGGER = Logger.getLogger(Requester.class.getName());
}
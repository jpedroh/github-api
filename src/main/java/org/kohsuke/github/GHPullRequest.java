package org.kohsuke.github;
import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import static org.kohsuke.github.Previews.*;

@SuppressWarnings(value = { "UnusedDeclaration" }) public class GHPullRequest extends GHIssue {
  private String patch_url, diff_url, issue_url;

  private GHCommitPointer base;

  private String merged_at;

  private GHCommitPointer head;

  private GHUser merged_by;

  private int review_comments, additions;

  private boolean merged;

  private Boolean mergeable;

  private int deletions;

  private String mergeable_state;

  private int changed_files;

  private String merge_commit_sha;

  private transient boolean fetchedIssueDetails;

  GHPullRequest wrapUp(GHRepository owner) {
    this.wrap(owner);
    return wrapUp(owner.root);
  }

  GHPullRequest wrapUp(GitHub root) {
    if (owner != null) {
      owner.wrap(root);
    }
    if (base != null) {
      base.wrapUp(root);
    }
    if (head != null) {
      head.wrapUp(root);
    }
    if (merged_by != null) {
      merged_by.wrapUp(root);
    }
    return this;
  }

  @Override protected String getApiRoute() {
    return "/repos/" + owner.getOwnerName() + "/" + owner.getName() + "/pulls/" + number;
  }

  public URL getPatchUrl() {
    return GitHub.parseURL(patch_url);
  }

  public URL getIssueUrl() {
    return GitHub.parseURL(issue_url);
  }

  public GHCommitPointer getBase() {
    return base;
  }

  public GHCommitPointer getHead() {
    return head;
  }

  @Deprecated public Date getIssueUpdatedAt() throws IOException {
    return super.getUpdatedAt();
  }

  public URL getDiffUrl() {
    return GitHub.parseURL(diff_url);
  }

  public Date getMergedAt() {
    return GitHub.parseDate(merged_at);
  }

  @Override public Collection<GHLabel> getLabels() throws IOException {
    fetchIssue();
    return super.getLabels();
  }

  @Override public GHUser getClosedBy() {
    return null;
  }

  @Override public PullRequest getPullRequest() {
    return null;
  }

  public GHUser getMergedBy() throws IOException {
    populate();
    return merged_by;
  }

  public int getReviewComments() throws IOException {
    populate();
    return review_comments;
  }

  public int getAdditions() throws IOException {
    populate();
    return additions;
  }

  public boolean isMerged() throws IOException {
    populate();
    return merged;
  }

  public Boolean getMergeable() throws IOException {
    populate();
    return mergeable;
  }

  public int getDeletions() throws IOException {
    populate();
    return deletions;
  }

  public String getMergeableState() throws IOException {
    populate();
    return mergeable_state;
  }

  public int getChangedFiles() throws IOException {
    populate();
    return changed_files;
  }

  public String getMergeCommitSha() throws IOException {
    populate();
    return merge_commit_sha;
  }

  private void populate() throws IOException {
    if (mergeable_state != null) {
      return;
    }
    if (root.isOffline()) {
      return;
    }
    root.retrieve().to(url, this).wrapUp(owner);
  }

  public PagedIterable<GHPullRequestFileDetail> listFiles() {
    return new PagedIterable<GHPullRequestFileDetail>() {
      public PagedIterator<GHPullRequestFileDetail> _iterator(int pageSize) {
        return new PagedIterator<GHPullRequestFileDetail>(root.retrieve().asIterator(String.format("%s/files", getApiRoute()), GHPullRequestFileDetail[].class, pageSize)) {
          @Override protected void wrapUp(GHPullRequestFileDetail[] page) {
          }
        };
      }
    };
  }

  public PagedIterable<GHPullRequestReview> listReviews() {
    return new PagedIterable<GHPullRequestReview>() {
      public PagedIterator<GHPullRequestReview> _iterator(int pageSize) {
        return new PagedIterator<GHPullRequestReview>(root.retrieve().withPreview(BLACK_CAT).asIterator(String.format("%s/reviews", getApiRoute()), GHPullRequestReview[].class, pageSize)) {
          @Override protected void wrapUp(GHPullRequestReview[] page) {
            for (GHPullRequestReview r : page) {
              r.wrapUp(GHPullRequest.this);
            }
          }
        };
      }
    };
  }

  public PagedIterable<GHPullRequestReviewComment> listReviewComments() throws IOException {
    return new PagedIterable<GHPullRequestReviewComment>() {
      public PagedIterator<GHPullRequestReviewComment> _iterator(int pageSize) {
        return new PagedIterator<GHPullRequestReviewComment>(root.retrieve().asIterator(getApiRoute() + "/comments", GHPullRequestReviewComment[].class, pageSize)) {
          protected void wrapUp(GHPullRequestReviewComment[] page) {
            for (GHPullRequestReviewComment c : page) {
              c.wrapUp(GHPullRequest.this);
            }
          }
        };
      }
    };
  }

  public PagedIterable<GHPullRequestCommitDetail> listCommits() {
    return new PagedIterable<GHPullRequestCommitDetail>() {
      public PagedIterator<GHPullRequestCommitDetail> _iterator(int pageSize) {
        return new PagedIterator<GHPullRequestCommitDetail>(root.retrieve().asIterator(String.format("%s/commits", getApiRoute()), GHPullRequestCommitDetail[].class, pageSize)) {
          @Override protected void wrapUp(GHPullRequestCommitDetail[] page) {
            for (GHPullRequestCommitDetail c : page) {
              c.wrapUp(GHPullRequest.this);
            }
          }
        };
      }
    };
  }

  @Preview @Deprecated public GHPullRequestReview createReview(String body, @CheckForNull GHPullRequestReviewState event, GHPullRequestReviewComment... comments) throws IOException {
    return createReview(body, event, Arrays.asList(comments));
  }

  @Preview @Deprecated public GHPullRequestReview createReview(String body, @CheckForNull GHPullRequestReviewState event, List<GHPullRequestReviewComment> comments) throws IOException {
    List<DraftReviewComment> draftComments = new ArrayList<DraftReviewComment>(comments.size());
    for (GHPullRequestReviewComment c : comments) {
      draftComments.add(new DraftReviewComment(c.getBody(), c.getPath(), c.getPosition()));
    }
    return new Requester(root).method("POST").with("body", body)._with("comments", draftComments).withPreview(BLACK_CAT).to(getApiRoute() + "/reviews", GHPullRequestReview.class).wrapUp(this);
  }

  public GHPullRequestReviewComment createReviewComment(String body, String sha, String path, int position) throws IOException {
    return new Requester(root).method("POST").with("body", body).with("commit_id", sha).with("path", path).with("position", position).to(getApiRoute() + "/comments", GHPullRequestReviewComment.class).wrapUp(this);
  }

  public void merge(String msg) throws IOException {
    merge(msg, null);
  }

  public void merge(String msg, String sha) throws IOException {
    merge(msg, sha, null);
  }

  public void merge(String msg, String sha, MergeMethod method) throws IOException {
    new Requester(root).method("PUT").with("commit_message", msg).with("sha", sha).with("merge_method", method).to(getApiRoute() + "/merge");
  }

  public enum MergeMethod {
    MERGE,
    SQUASH,
    REBASE
  }

  private void fetchIssue() throws IOException {
    if (!fetchedIssueDetails) {
      new Requester(root).to(getIssuesApiRoute(), this);
      fetchedIssueDetails = true;
    }
  }

  private static class DraftReviewComment {
    private String body;

    private String path;

    private int position;

    public DraftReviewComment(String body, String path, int position) {
      this.body = body;
      this.path = path;
      this.position = position;
    }

    public String getBody() {
      return body;
    }

    public String getPath() {
      return path;
    }

    public int getPosition() {
      return position;
    }
  }
}
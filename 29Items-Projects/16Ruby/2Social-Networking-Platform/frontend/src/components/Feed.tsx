import type { FeedPost } from '../types/graphql';

type FeedProps = {
  posts: FeedPost[];
  loading: boolean;
};

export function Feed({ posts, loading }: FeedProps) {
  if (loading && !posts.length) {
    return <p className="state-message">Loading feed...</p>;
  }

  if (!posts.length) {
    return <p className="state-message">No posts yet.</p>;
  }

  return (
    <ul className="feed-list">
      {posts.map((post) => (
        <li className="feed-item" key={post.id}>
          <div className="feed-author">
            @{post.user.username}
            {post.toxicityScore !== null && post.toxicityScore !== undefined ? (
              <span className="toxicity">toxicity {Math.round(post.toxicityScore * 100)}%</span>
            ) : null}
          </div>
          <p>{post.body}</p>
          <div className="feed-meta">
            {post.visibility} - {new Date(post.createdAt).toLocaleString()}
          </div>
        </li>
      ))}
    </ul>
  );
}

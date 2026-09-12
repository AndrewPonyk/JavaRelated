export type FeedPost = {
  id: string;
  body: string;
  visibility: 'public' | 'followers' | 'private';
  toxicityScore?: number | null;
  createdAt: string;
  user: User;
};

export type User = {
  id: string;
  email?: string;
  username: string;
  displayName?: string | null;
};

export type Message = {
  id: string;
  body: string;
  toxicityScore?: number | null;
  readAt?: string | null;
  createdAt: string;
  sender: User;
  recipient: User;
};

export type AppNotification = {
  id: string;
  kind: string;
  payload: Record<string, unknown>;
  readAt?: string | null;
  createdAt: string;
};

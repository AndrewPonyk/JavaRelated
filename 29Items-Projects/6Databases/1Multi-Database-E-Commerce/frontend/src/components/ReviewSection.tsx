'use client';

import { useCallback, useEffect, useState } from 'react';
import { ApiError, catalogApi } from '@/lib/api';
import { stars } from '@/lib/format';
import type { Review } from '@/types/catalog';

/** Lists a product's reviews and lets a user submit a new one (with validation). */
export default function ReviewSection({ productId }: { productId: string }) {
  const [reviews, setReviews] = useState<Review[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [author, setAuthor] = useState('');
  const [rating, setRating] = useState(5);
  const [text, setText] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    try {
      const page = await catalogApi.listReviews(productId);
      setReviews(page.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load reviews.');
    }
  }, [productId]);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (author.trim().length === 0) return setFormError('Please enter your name.');
    if (text.trim().length < 3) return setFormError('Review text is too short.');
    setSubmitting(true);
    try {
      await catalogApi.addReview(productId, { author: author.trim(), rating, text: text.trim() });
      setAuthor('');
      setText('');
      setRating(5);
      await load();
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Failed to submit review.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section>
      <h3>Reviews</h3>
      {error && <p className="error">{error}</p>}
      {reviews === null ? (
        <p role="status">Loading reviews…</p>
      ) : reviews.length === 0 ? (
        <p className="muted">No reviews yet — be the first!</p>
      ) : (
        <ul className="reviews">
          {reviews.map((r) => (
            <li key={r.id}>
              <strong>{r.author}</strong> <span className="rating">{stars(r.rating)}</span>
              {r.sentimentLabel && (
                <span className={`badge badge-${r.sentimentLabel.toLowerCase()}`}>{r.sentimentLabel}</span>
              )}
              <p>{r.text}</p>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={submit} className="review-form">
        <h4>Write a review</h4>
        {formError && <p className="error">{formError}</p>}
        <input placeholder="Your name" value={author} onChange={(e) => setAuthor(e.target.value)} />
        <label>
          Rating:{' '}
          <select value={rating} onChange={(e) => setRating(Number(e.target.value))}>
            {[5, 4, 3, 2, 1].map((n) => (
              <option key={n} value={n}>
                {n} star{n > 1 ? 's' : ''}
              </option>
            ))}
          </select>
        </label>
        <textarea
          placeholder="What did you think?"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={3}
        />
        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </form>
    </section>
  );
}

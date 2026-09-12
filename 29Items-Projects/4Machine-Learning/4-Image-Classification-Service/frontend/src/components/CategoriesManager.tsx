// Taxonomy management: list, add (with validation), and delete categories.
import { useState, type FormEvent } from 'react';

import { useCategories } from '../hooks/useCategories';

export function CategoriesManager() {
  const { categories, loading, error, add, remove } = useCategories();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setFormError('Name is required.');
      return;
    }
    setFormError(null);
    await add(trimmed, description.trim() || undefined);
    setName('');
    setDescription('');
  };

  return (
    <section className="card">
      <h2>Category taxonomy</h2>

      <form className="cat-form" onSubmit={onSubmit}>
        <input
          type="text"
          placeholder="Category name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={128}
        />
        <input
          type="text"
          placeholder="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={512}
        />
        <button type="submit">Add</button>
      </form>
      {formError && <p className="error">⚠ {formError}</p>}
      {error && <p className="error">⚠ {error}</p>}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : (
        <ul className="cat-list">
          {categories.length === 0 && <li className="muted">No categories yet.</li>}
          {categories.map((c) => (
            <li key={c.id} className="cat-row">
              <span>
                <strong>{c.name}</strong>
                {c.description && <span className="muted"> — {c.description}</span>}
              </span>
              <button
                className="danger"
                onClick={() => remove(c.id)}
                aria-label={`Delete ${c.name}`}
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

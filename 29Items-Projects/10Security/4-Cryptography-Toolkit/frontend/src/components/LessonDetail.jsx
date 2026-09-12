import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { apiFetch, auth } from "../api/client.js";
import { useAsync } from "../hooks/useAsync.js";
import { ErrorText, Loading, Panel } from "./ui.jsx";

/** Render lesson markdown (bold/italic/code/lists — the seeded subset). */
function renderMarkdown(md) {
  const lines = md.split("\n");
  const out = [];
  let inList = false;
  // Full entity escape BEFORE any markdown transform — defence-in-depth even
  // though only admins write content_md (& first so entities aren't re-escaped)
  const inline = (text) =>
    text
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;")
      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
      .replace(/\*(.+?)\*/g, "<em>$1</em>")
      .replace(/`(.+?)`/g, "<code>$1</code>");
  for (const line of lines) {
    const li = line.match(/^\s*[-*]\s+(.*)$/);
    if (li) {
      if (!inList) {
        out.push("<ul>");
        inList = true;
      }
      out.push(`<li>${inline(li[1])}</li>`);
      continue;
    }
    if (inList) {
      out.push("</ul>");
      inList = false;
    }
    if (line.startsWith("### ")) out.push(`<h4>${inline(line.slice(4))}</h4>`);
    else if (line.startsWith("## "))
      out.push(`<h3>${inline(line.slice(3))}</h3>`);
    else if (line.trim()) out.push(`<p>${inline(line)}</p>`);
  }
  if (inList) out.push("</ul>");
  return out.join("");
}

export default function LessonDetail() {
  const { slug } = useParams();
  const lesson = useAsync(() => apiFetch(`/lessons/${slug}`), [slug]);
  const [editMode, setEditMode] = useState(false);
  const [draft, setDraft] = useState(null);
  const [saveError, setSaveError] = useState(null);
  const isAdmin = Boolean(auth.getToken()); // server enforces the real check

  async function save() {
    setSaveError(null);
    try {
      const updated = await apiFetch(`/lessons/${slug}`, {
        method: "PATCH",
        auth: true,
        body: { title: draft.title, content_md: draft.content_md },
      });
      setEditMode(false);
      lesson.run(() => Promise.resolve(updated));
    } catch (err) {
      setSaveError(err.message);
    }
  }

  return (
    <Panel title={lesson.data?.title ?? "Lesson"} status={lesson.status}>
      <p>
        <Link to="/lessons">← all lessons</Link>
      </p>
      {lesson.status === "loading" && <Loading />}
      <ErrorText error={lesson.error} />
      {lesson.data && !editMode && (
        <>
          <span className="chip">{lesson.data.topic}</span>
          {lesson.data.demo_endpoint && (
            <p>
              Live demo: <code>{lesson.data.demo_endpoint}</code>
            </p>
          )}
          <div
            className="markdown"
            data-testid="lesson-body"
            dangerouslySetInnerHTML={{
              __html: renderMarkdown(lesson.data.content_md),
            }}
          />
          {isAdmin && (
            <div className="actions">
              <button
                onClick={() => {
                  setDraft({
                    title: lesson.data.title,
                    content_md: lesson.data.content_md,
                  });
                  setEditMode(true);
                }}
              >
                Edit (admin)
              </button>
            </div>
          )}
        </>
      )}
      {lesson.data && editMode && (
        <>
          <label>
            Title
            <input
              value={draft.title}
              onChange={(e) =>
                setDraft((d) => ({ ...d, title: e.target.value }))
              }
            />
          </label>
          <label>
            Content (markdown subset)
            <textarea
              rows={14}
              value={draft.content_md}
              onChange={(e) =>
                setDraft((d) => ({ ...d, content_md: e.target.value }))
              }
            />
          </label>
          <ErrorText error={saveError} />
          <div className="actions">
            <button onClick={save}>Save</button>
            <button className="secondary" onClick={() => setEditMode(false)}>
              Cancel
            </button>
          </div>
        </>
      )}
    </Panel>
  );
}

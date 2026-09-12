import { useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../api/client.js";
import { useAsync } from "../hooks/useAsync.js";
import { ErrorText, Loading, Panel } from "./ui.jsx";

const TOPICS = ["all", "aes", "rsa", "ecdsa", "sha3", "argon2", "tls"];

/** Lesson index — seeded content pairs each topic with its live demo. */
export default function Lessons() {
  const [topic, setTopic] = useState("all");
  const lessons = useAsync(
    () => apiFetch(`/lessons/${topic !== "all" ? `?topic=${topic}` : ""}`),
    [topic],
  );

  return (
    <Panel
      title="Lessons"
      status={lessons.status}
      subtitle="Each lesson pairs the theory with a live demo endpoint."
    >
      <div className="inline-row">
        <select
          value={topic}
          onChange={(e) => setTopic(e.target.value)}
          aria-label="Filter by topic"
        >
          {TOPICS.map((t) => (
            <option key={t}>{t}</option>
          ))}
        </select>
      </div>
      {lessons.status === "loading" && <Loading />}
      <ErrorText error={lessons.error} />
      <ul className="lesson-list">
        {(lessons.data?.items ?? []).map((lesson) => (
          <li key={lesson.slug}>
            <Link to={`/lessons/${lesson.slug}`}>{lesson.title}</Link>
            <span className="chip">{lesson.topic}</span>
            {lesson.demo_endpoint && (
              <code className="demo-endpoint">{lesson.demo_endpoint}</code>
            )}
          </li>
        ))}
      </ul>
    </Panel>
  );
}

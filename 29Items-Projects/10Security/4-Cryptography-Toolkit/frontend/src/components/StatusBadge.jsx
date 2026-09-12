export default function StatusBadge({ status }) {
  const map = {
    idle: "grey",
    loading: "amber",
    ok: "green",
    error: "red",
  };
  return (
    <span className={`badge badge-${map[status] ?? "grey"}`} role="status">
      {status}
    </span>
  );
}

import { BuilderWorkspace } from "@/components/BuilderWorkspace";

export default function HomePage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-5 py-6">
      <header className="mb-6 flex flex-col gap-2">
        <p className="text-sm font-semibold uppercase text-signal">Builder workspace</p>
        <h1 className="text-4xl font-semibold text-ink">Marketing Website Builder</h1>
        <p className="max-w-3xl text-base text-slate-600">
          Build campaign pages from approved blocks, import Figma selections,
          track conversion events, and generate layout suggestions from performance data.
        </p>
      </header>

      <BuilderWorkspace />
    </main>
  );
}

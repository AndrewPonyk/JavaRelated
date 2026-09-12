import { useCallback, useEffect, useState } from 'react';

import type { EditRecipe } from '../contracts/editor';
import { presetApi, type SavedPreset, type SavedProject } from '../services/presetApi';

function message(error: unknown): string {
  return error instanceof Error ? error.message : 'Saved presets could not be updated.';
}

export function usePresetLibrary() {
  const [projects, setProjects] = useState<readonly SavedProject[]>([]);
  const [presets, setPresets] = useState<readonly SavedPreset[]>([]);
  const [loading, setLoading] = useState(presetApi.enabled());
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!presetApi.enabled()) return;
    setLoading(true);
    setError(null);
    try {
      const [nextProjects, nextPresets] = await Promise.all([
        presetApi.listProjects(),
        presetApi.listPresets(),
      ]);
      setProjects(nextProjects);
      setPresets(nextPresets);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => void reload(), [reload]);

  const createProject = useCallback(async (name: string) => {
    const project = await presetApi.createProject(name);
    setProjects((current) => [project, ...current]);
    return project;
  }, []);

  const updateProject = useCallback(async (id: string, name: string) => {
    const project = await presetApi.updateProject(id, name);
    setProjects((current) => current.map((item) => (item.id === id ? project : item)));
    return project;
  }, []);

  const deleteProject = useCallback(async (id: string) => {
    await presetApi.deleteProject(id);
    setProjects((current) => current.filter((project) => project.id !== id));
    setPresets((current) =>
      current.map((preset) => (preset.projectId === id ? { ...preset, projectId: null } : preset)),
    );
  }, []);

  const savePreset = useCallback(
    async (name: string, recipe: EditRecipe, projectId: string | null) => {
      const preset = await presetApi.createPreset({ name, recipe, projectId });
      setPresets((current) => [preset, ...current]);
      return preset;
    },
    [],
  );

  const deletePreset = useCallback(async (id: string) => {
    await presetApi.deletePreset(id);
    setPresets((current) => current.filter((preset) => preset.id !== id));
  }, []);

  const updatePreset = useCallback(
    async (id: string, input: Partial<Pick<SavedPreset, 'name' | 'projectId' | 'recipe'>>) => {
      const preset = await presetApi.updatePreset(id, input);
      setPresets((current) => current.map((item) => (item.id === id ? preset : item)));
      return preset;
    },
    [],
  );

  return {
    enabled: presetApi.enabled(),
    projects,
    presets,
    loading,
    error,
    reload,
    createProject,
    updateProject,
    deleteProject,
    savePreset,
    updatePreset,
    deletePreset,
  };
}

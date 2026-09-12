// @vitest-environment node

import { describe, expect, it, vi } from 'vitest';

import type { EditorProject, EditorUser, Preset } from './entities';
import type { PresetRepository, ProjectRepository, UserRepository } from './repositories';
import {
  InvalidSessionError,
  PresetService,
  ProjectService,
  ResourceNotFoundError,
  UserService,
} from './services';

const project: EditorProject = {
  id: '7f48fb6c-d65b-4e2e-9d56-4f64fa246d8e',
  ownerId: 'owner',
  name: 'Project',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};
const preset: Preset = {
  id: 'a795a0c1-2bc6-4bf8-ab7b-8e24eafac547',
  ownerId: 'owner',
  projectId: null,
  name: 'Preset',
  recipe: { schemaVersion: 1, filters: [] },
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('application services', () => {
  it('maps repository results and missing project resources consistently', async () => {
    const repository = {
      list: vi.fn().mockResolvedValue([project]),
      find: vi.fn().mockResolvedValue(project),
      create: vi.fn().mockResolvedValue(project),
      update: vi.fn().mockResolvedValue(project),
      delete: vi.fn().mockResolvedValue(true),
    } as unknown as ProjectRepository;
    const service = new ProjectService(repository);
    expect(await service.list('owner', { limit: 50, offset: 0 })).toEqual([project]);
    expect(await service.get('owner', project.id)).toEqual(project);
    expect(await service.create('owner', 'Project')).toEqual(project);
    expect(await service.update('owner', project.id, 'Project')).toEqual(project);
    await expect(service.delete('owner', project.id)).resolves.toBeUndefined();
    vi.mocked(repository.find).mockResolvedValueOnce(null);
    await expect(service.get('owner', project.id)).rejects.toBeInstanceOf(ResourceNotFoundError);
  });

  it('handles presets and required user sessions', async () => {
    const presetRepository = {
      list: vi.fn().mockResolvedValue([preset]),
      find: vi.fn().mockResolvedValue(preset),
      create: vi.fn().mockResolvedValue(preset),
      update: vi.fn().mockResolvedValue(preset),
      delete: vi.fn().mockResolvedValue(true),
    } as unknown as PresetRepository;
    const user: EditorUser = {
      id: 'owner',
      displayName: 'Editor',
      createdAt: '2026-01-01T00:00:00.000Z',
    };
    const userRepository = {
      create: vi.fn().mockResolvedValue(user),
      find: vi.fn().mockResolvedValue(user),
    } as unknown as UserRepository;
    const presets = new PresetService(presetRepository);
    const users = new UserService(userRepository);
    expect(await presets.get('owner', preset.id)).toEqual(preset);
    expect(await presets.create('owner', { name: 'Preset', recipe: preset.recipe })).toEqual(
      preset,
    );
    expect(await presets.update('owner', preset.id, { name: 'Changed' })).toEqual(preset);
    await expect(presets.delete('owner', preset.id)).resolves.toBeUndefined();
    expect(await users.create('owner', 'Editor')).toEqual(user);
    expect(await users.require('owner')).toEqual(user);
    vi.mocked(userRepository.find).mockResolvedValueOnce(null);
    await expect(users.require('owner')).rejects.toBeInstanceOf(InvalidSessionError);
  });
});

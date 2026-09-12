import type { Pagination, PresetCreate, PresetUpdate } from '../src/contracts/editor';
import type { EditorProject, EditorUser, Preset } from './entities';
import type { ProjectRepository, PresetRepository, UserRepository } from './repositories';

export class ResourceNotFoundError extends Error {
  constructor(resource: string) {
    super(`${resource} was not found.`);
    this.name = 'ResourceNotFoundError';
  }
}

export class InvalidSessionError extends Error {
  constructor() {
    super('The session is no longer available.');
    this.name = 'InvalidSessionError';
  }
}

export class ProjectService {
  constructor(private readonly repository: ProjectRepository) {}

  list(ownerId: string, pagination: Pagination): Promise<readonly EditorProject[]> {
    return this.repository.list(ownerId, pagination);
  }

  async get(ownerId: string, id: string): Promise<EditorProject> {
    const project = await this.repository.find(ownerId, id);
    if (!project) throw new ResourceNotFoundError('Project');
    return project;
  }

  create(ownerId: string, name: string): Promise<EditorProject> {
    return this.repository.create(ownerId, name);
  }

  async update(ownerId: string, id: string, name: string): Promise<EditorProject> {
    const project = await this.repository.update(ownerId, id, name);
    if (!project) throw new ResourceNotFoundError('Project');
    return project;
  }

  async delete(ownerId: string, id: string): Promise<void> {
    if (!(await this.repository.delete(ownerId, id))) throw new ResourceNotFoundError('Project');
  }
}

export class PresetService {
  constructor(private readonly repository: PresetRepository) {}

  list(ownerId: string, pagination: Pagination, projectId?: string): Promise<readonly Preset[]> {
    return this.repository.list(ownerId, pagination, projectId);
  }

  async get(ownerId: string, id: string): Promise<Preset> {
    const preset = await this.repository.find(ownerId, id);
    if (!preset) throw new ResourceNotFoundError('Preset');
    return preset;
  }

  create(ownerId: string, input: PresetCreate): Promise<Preset> {
    return this.repository.create(ownerId, input);
  }

  async update(ownerId: string, id: string, input: PresetUpdate): Promise<Preset> {
    const preset = await this.repository.update(ownerId, id, input);
    if (!preset) throw new ResourceNotFoundError('Preset');
    return preset;
  }

  async delete(ownerId: string, id: string): Promise<void> {
    if (!(await this.repository.delete(ownerId, id))) throw new ResourceNotFoundError('Preset');
  }
}

export class UserService {
  constructor(private readonly repository: UserRepository) {}

  create(id: string, displayName: string): Promise<EditorUser> {
    return this.repository.create(id, displayName);
  }

  async require(id: string): Promise<EditorUser> {
    const user = await this.repository.find(id);
    if (!user) throw new InvalidSessionError();
    return user;
  }
}

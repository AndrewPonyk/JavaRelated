import {
  editRecipeSchema,
  type Pagination,
  type PresetCreate,
  type PresetUpdate,
} from '../src/contracts/editor';
import type { Database } from './database';
import type { EditorProject, EditorUser, Preset } from './entities';

interface UserRow {
  id: string;
  display_name: string;
  created_at: Date;
}

interface ProjectRow {
  id: string;
  owner_id: string;
  name: string;
  created_at: Date;
  updated_at: Date;
}

interface PresetRow extends ProjectRow {
  project_id: string | null;
  recipe: unknown;
}

function iso(date: Date | string): string {
  return typeof date === 'string' ? new Date(date).toISOString() : date.toISOString();
}

function projectFromRow(row: ProjectRow): EditorProject {
  return {
    id: row.id,
    ownerId: row.owner_id,
    name: row.name,
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at),
  };
}

function presetFromRow(row: PresetRow): Preset {
  return {
    ...projectFromRow(row),
    projectId: row.project_id,
    recipe: editRecipeSchema.parse(row.recipe),
  };
}

export class UserRepository {
  constructor(private readonly database: Database) {}

  async create(id: string, displayName: string): Promise<EditorUser> {
    const result = await this.database.query<UserRow>(
      `INSERT INTO editor_users (id, display_name)
       VALUES ($1, $2)
       RETURNING id, display_name, created_at`,
      [id, displayName],
    );
    const row = result.rows[0];
    if (!row) throw new Error('User insert did not return a row.');
    return { id: row.id, displayName: row.display_name, createdAt: iso(row.created_at) };
  }

  async find(id: string): Promise<EditorUser | null> {
    const result = await this.database.query<UserRow>(
      'SELECT id, display_name, created_at FROM editor_users WHERE id = $1',
      [id],
    );
    const row = result.rows[0];
    return row
      ? { id: row.id, displayName: row.display_name, createdAt: iso(row.created_at) }
      : null;
  }
}

export class ProjectRepository {
  constructor(private readonly database: Database) {}

  list(ownerId: string, pagination: Pagination): Promise<readonly EditorProject[]> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<ProjectRow>(
        `SELECT id, owner_id, name, created_at, updated_at
         FROM editor_projects WHERE owner_id = $1 ORDER BY updated_at DESC, id DESC
         LIMIT $2 OFFSET $3`,
        [ownerId, pagination.limit, pagination.offset],
      );
      return result.rows.map(projectFromRow);
    });
  }

  async find(ownerId: string, id: string): Promise<EditorProject | null> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<ProjectRow>(
        `SELECT id, owner_id, name, created_at, updated_at
         FROM editor_projects WHERE id = $1 AND owner_id = $2`,
        [id, ownerId],
      );
      return result.rows[0] ? projectFromRow(result.rows[0]) : null;
    });
  }

  async create(ownerId: string, name: string): Promise<EditorProject> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<ProjectRow>(
        `INSERT INTO editor_projects (owner_id, name) VALUES ($1, $2)
         RETURNING id, owner_id, name, created_at, updated_at`,
        [ownerId, name],
      );
      const row = result.rows[0];
      if (!row) throw new Error('Project insert did not return a row.');
      return projectFromRow(row);
    });
  }

  async update(ownerId: string, id: string, name: string): Promise<EditorProject | null> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<ProjectRow>(
        `UPDATE editor_projects SET name = $3, updated_at = now()
         WHERE id = $1 AND owner_id = $2
         RETURNING id, owner_id, name, created_at, updated_at`,
        [id, ownerId, name],
      );
      return result.rows[0] ? projectFromRow(result.rows[0]) : null;
    });
  }

  async delete(ownerId: string, id: string): Promise<boolean> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query(
        'DELETE FROM editor_projects WHERE id = $1 AND owner_id = $2',
        [id, ownerId],
      );
      return result.rowCount === 1;
    });
  }
}

export class PresetRepository {
  constructor(private readonly database: Database) {}

  list(ownerId: string, pagination: Pagination, projectId?: string): Promise<readonly Preset[]> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<PresetRow>(
        `SELECT id, owner_id, project_id, name, recipe, created_at, updated_at
         FROM edit_presets
         WHERE owner_id = $1 AND ($2::uuid IS NULL OR project_id = $2::uuid)
         ORDER BY updated_at DESC, id DESC
         LIMIT $3 OFFSET $4`,
        [ownerId, projectId ?? null, pagination.limit, pagination.offset],
      );
      return result.rows.map(presetFromRow);
    });
  }

  async find(ownerId: string, id: string): Promise<Preset | null> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<PresetRow>(
        `SELECT id, owner_id, project_id, name, recipe, created_at, updated_at
         FROM edit_presets WHERE id = $1 AND owner_id = $2`,
        [id, ownerId],
      );
      return result.rows[0] ? presetFromRow(result.rows[0]) : null;
    });
  }

  async create(ownerId: string, input: PresetCreate): Promise<Preset> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<PresetRow>(
        `INSERT INTO edit_presets (owner_id, project_id, name, recipe, schema_version)
         VALUES ($1, $2, $3, $4::jsonb, $5)
         RETURNING id, owner_id, project_id, name, recipe, created_at, updated_at`,
        [
          ownerId,
          input.projectId ?? null,
          input.name,
          JSON.stringify(input.recipe),
          input.recipe.schemaVersion,
        ],
      );
      const row = result.rows[0];
      if (!row) throw new Error('Preset insert did not return a row.');
      return presetFromRow(row);
    });
  }

  async update(ownerId: string, id: string, input: PresetUpdate): Promise<Preset | null> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query<PresetRow>(
        `UPDATE edit_presets
         SET name = COALESCE($3, name),
             project_id = CASE WHEN $4::boolean THEN $5::uuid ELSE project_id END,
             recipe = COALESCE($6::jsonb, recipe),
             schema_version = COALESCE($7::smallint, schema_version),
             updated_at = now()
         WHERE id = $1 AND owner_id = $2
         RETURNING id, owner_id, project_id, name, recipe, created_at, updated_at`,
        [
          id,
          ownerId,
          input.name ?? null,
          Object.hasOwn(input, 'projectId'),
          input.projectId ?? null,
          input.recipe ? JSON.stringify(input.recipe) : null,
          input.recipe?.schemaVersion ?? null,
        ],
      );
      return result.rows[0] ? presetFromRow(result.rows[0]) : null;
    });
  }

  async delete(ownerId: string, id: string): Promise<boolean> {
    return this.database.withOwner(ownerId, async (client) => {
      const result = await client.query(
        'DELETE FROM edit_presets WHERE id = $1 AND owner_id = $2',
        [id, ownerId],
      );
      return result.rowCount === 1;
    });
  }
}

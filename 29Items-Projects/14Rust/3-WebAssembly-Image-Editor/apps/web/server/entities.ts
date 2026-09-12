import type { EditRecipe } from '../src/contracts/editor';

export interface EditorUser {
  readonly id: string;
  readonly displayName: string;
  readonly createdAt: string;
}

export interface EditorProject {
  readonly id: string;
  readonly ownerId: string;
  readonly name: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface Preset {
  readonly id: string;
  readonly ownerId: string;
  readonly projectId: string | null;
  readonly name: string;
  readonly recipe: EditRecipe;
  readonly createdAt: string;
  readonly updatedAt: string;
}

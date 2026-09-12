import { create } from 'zustand';

import { IDENTITY, type Mat2 } from '@/core/math/matrix2';
import { vec2, type Vec2 } from '@/core/math/vector2';

/**
 * The app's "event bus" (see ARCHITECTURE.md §2.2): React components mutate it
 * through actions and re-render via selectors; the Three.js SceneManager
 * subscribes OUTSIDE React and watches `transformVersion` to start tweens.
 */

export type LayerToggle = 'showGrid' | 'showEigenvectors' | 'showUnitSquare';

export interface LabeledVector {
  id: string;
  label: string;
  color: string;
  /** Coordinates in INPUT space — the scene draws A(t)·v. */
  v: Vec2;
}

const VECTOR_PALETTE = ['#e879f9', '#4ade80', '#f472b6', '#facc15', '#22d3ee'];
export const MAX_USER_VECTORS = VECTOR_PALETTE.length;

export interface VisualizerState {
  /** The transform the scene animates toward. */
  targetMatrix: Mat2;
  /** Monotonic counter — bumping it re-triggers the tween (even for the same matrix). */
  transformVersion: number;
  showGrid: boolean;
  showEigenvectors: boolean;
  showUnitSquare: boolean;
  /** Tween speed multiplier, clamped to [0.25, 3]. */
  animationSpeed: number;
  /** User-defined vectors, draggable on the canvas. */
  userVectors: LabeledVector[];
  vectorCounter: number;

  setMatrix: (matrix: Mat2) => void;
  resetTransform: () => void;
  /** Re-run the current animation — a teaching affordance. */
  replay: () => void;
  toggle: (key: LayerToggle) => void;
  setAnimationSpeed: (speed: number) => void;
  addVector: () => void;
  updateVector: (id: string, v: Vec2) => void;
  removeVector: (id: string) => void;
}

export const useVisualizerStore = create<VisualizerState>()((set) => ({
  targetMatrix: IDENTITY,
  transformVersion: 0,
  showGrid: true,
  showEigenvectors: true,
  showUnitSquare: true,
  animationSpeed: 1,
  userVectors: [],
  vectorCounter: 0,

  setMatrix: (matrix) =>
    set((s) => ({ targetMatrix: matrix, transformVersion: s.transformVersion + 1 })),
  resetTransform: () =>
    set((s) => ({ targetMatrix: IDENTITY, transformVersion: s.transformVersion + 1 })),
  replay: () => set((s) => ({ transformVersion: s.transformVersion + 1 })),
  toggle: (key) => set((s) => ({ [key]: !s[key] }) as Partial<VisualizerState>),
  setAnimationSpeed: (speed) => set({ animationSpeed: Math.min(3, Math.max(0.25, speed)) }),

  addVector: () =>
    set((s) => {
      if (s.userVectors.length >= MAX_USER_VECTORS) return {};
      const n = s.vectorCounter + 1;
      const vector: LabeledVector = {
        id: `vec-${n}`,
        label: `v${n}`,
        color: VECTOR_PALETTE[s.userVectors.length],
        // Staggered spawn positions so consecutive vectors don't overlap.
        v: vec2(2, 1 + s.userVectors.length),
      };
      return { userVectors: [...s.userVectors, vector], vectorCounter: n };
    }),
  updateVector: (id, v) =>
    set((s) => ({
      userVectors: s.userVectors.map((entry) => (entry.id === id ? { ...entry, v } : entry)),
    })),
  removeVector: (id) =>
    set((s) => ({ userVectors: s.userVectors.filter((entry) => entry.id !== id) })),
}));

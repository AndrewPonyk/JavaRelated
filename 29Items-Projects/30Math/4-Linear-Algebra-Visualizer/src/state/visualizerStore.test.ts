import { beforeEach, describe, expect, it } from 'vitest';

import { IDENTITY, mat2 } from '@/core/math/matrix2';
import { vec2 } from '@/core/math/vector2';
import { MAX_USER_VECTORS, useVisualizerStore } from './visualizerStore';

describe('visualizerStore', () => {
  beforeEach(() => {
    useVisualizerStore.setState({
      targetMatrix: IDENTITY,
      transformVersion: 0,
      showGrid: true,
      showEigenvectors: true,
      showUnitSquare: true,
      animationSpeed: 1,
      userVectors: [],
      vectorCounter: 0,
    });
  });

  it('setMatrix updates the target and bumps the transform version', () => {
    const m = mat2(2, 0, 0, 2);
    useVisualizerStore.getState().setMatrix(m);
    expect(useVisualizerStore.getState().targetMatrix).toEqual(m);
    expect(useVisualizerStore.getState().transformVersion).toBe(1);
  });

  it('replay bumps the version without touching the matrix', () => {
    const before = useVisualizerStore.getState().targetMatrix;
    useVisualizerStore.getState().replay();
    expect(useVisualizerStore.getState().targetMatrix).toEqual(before);
    expect(useVisualizerStore.getState().transformVersion).toBe(1);
  });

  it('resetTransform restores the identity', () => {
    useVisualizerStore.getState().setMatrix(mat2(3, 1, 1, 3));
    useVisualizerStore.getState().resetTransform();
    expect(useVisualizerStore.getState().targetMatrix).toEqual(IDENTITY);
  });

  it('toggle flips exactly the named layer', () => {
    useVisualizerStore.getState().toggle('showGrid');
    expect(useVisualizerStore.getState().showGrid).toBe(false);
    expect(useVisualizerStore.getState().showUnitSquare).toBe(true);
  });

  it('setAnimationSpeed clamps to [0.25, 3]', () => {
    useVisualizerStore.getState().setAnimationSpeed(99);
    expect(useVisualizerStore.getState().animationSpeed).toBe(3);
    useVisualizerStore.getState().setAnimationSpeed(0.01);
    expect(useVisualizerStore.getState().animationSpeed).toBe(0.25);
  });

  it('adds, updates and removes user vectors with unique ids', () => {
    const store = useVisualizerStore.getState();
    store.addVector();
    store.addVector();
    const [first, second] = useVisualizerStore.getState().userVectors;
    expect(first.id).not.toBe(second.id);
    expect(first.color).not.toBe(second.color);

    useVisualizerStore.getState().updateVector(first.id, vec2(-3, 4));
    expect(useVisualizerStore.getState().userVectors.find((v) => v.id === first.id)?.v).toEqual(
      vec2(-3, 4),
    );

    useVisualizerStore.getState().removeVector(first.id);
    expect(useVisualizerStore.getState().userVectors.map((v) => v.id)).toEqual([second.id]);
  });

  it('caps user vectors at the palette size', () => {
    for (let i = 0; i < MAX_USER_VECTORS + 3; i++) {
      useVisualizerStore.getState().addVector();
    }
    expect(useVisualizerStore.getState().userVectors).toHaveLength(MAX_USER_VECTORS);
  });
});

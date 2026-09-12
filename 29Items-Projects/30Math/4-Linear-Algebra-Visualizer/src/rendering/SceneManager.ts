import * as THREE from 'three';

import { eigen } from '@/core/math/eigen';
import { apply, determinant, inverse, type Mat2 } from '@/core/math/matrix2';
import { scale, sub, lengthOf, vec2, type Vec2 } from '@/core/math/vector2';
import { useVisualizerStore, type VisualizerState } from '@/state/visualizerStore';
import { TransformAnimator } from './TransformAnimator';
import { createAxes, createGrid } from './primitives/GridPlane';
import { VectorArrow } from './primitives/VectorArrow';

const VIEW_HEIGHT_WORLD = 12; // world units visible vertically
const BASE_TWEEN_SECONDS = 1.2;
const GRID_EXTENT = 20;
const DRAG_PICK_RADIUS = 0.55; // world units around a vector tip

const COLORS = {
  background: 0x0b1120,
  staticGrid: 0x1e293b,
  dynamicGrid: 0x38bdf8,
  basisI: 0x38bdf8,
  basisJ: 0xfb923c,
  eigen: 0xa3e635,
  unitSquare: 0xfbbf24,
  unitSquareFlipped: 0xf87171, // det < 0 → orientation reversed
} as const;

const E1: Vec2 = vec2(1, 0);
const E2: Vec2 = vec2(0, 1);

/**
 * Owns the WebGL renderer, orthographic camera and render loop — deliberately
 * OUTSIDE React (see ARCHITECTURE.md §2.2). React mounts it once via
 * VectorCanvas and communicates exclusively through the Zustand store.
 *
 * Scene structure:
 *   scene
 *   ├─ static grid + axes                (never transforms — the reference frame)
 *   ├─ transformedGroup (matrix = A(t))  (animated: bright grid + unit square)
 *   ├─ basis / user arrows (world space) (tips recomputed per frame as A(t)·v —
 *   │                                     rigid heads, no shearing)
 *   └─ eigen arrows (world space)        (invariant directions of the TARGET matrix)
 */
export class SceneManager {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera: THREE.OrthographicCamera;
  private readonly clock = new THREE.Clock();
  private readonly animator = new TransformAnimator();

  private readonly transformedGroup = new THREE.Group();
  private readonly dynamicGrid: THREE.LineSegments;
  private readonly unitSquare: THREE.Mesh;
  private readonly unitSquareMaterial: THREE.MeshBasicMaterial;
  private readonly basisI = new VectorArrow(COLORS.basisI);
  private readonly basisJ = new VectorArrow(COLORS.basisJ);
  private readonly eigenArrows = [new VectorArrow(COLORS.eigen), new VectorArrow(COLORS.eigen)];
  private readonly userArrows = new Map<string, VectorArrow>();

  private readonly resizeObserver: ResizeObserver;
  private readonly unsubscribeStore: () => void;
  private lastTransformVersion: number;
  private userVectors: VisualizerState['userVectors'] = [];
  private draggingVectorId: string | null = null;
  private disposed = false;

  constructor(private readonly canvas: HTMLCanvasElement) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setClearColor(COLORS.background);

    this.camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 10);
    this.camera.position.z = 5;

    // Static reference frame.
    this.scene.add(createGrid(GRID_EXTENT, 1, COLORS.staticGrid));
    this.scene.add(createAxes(GRID_EXTENT));

    // Everything the matrix shears/stretches directly.
    this.transformedGroup.matrixAutoUpdate = false;
    this.dynamicGrid = createGrid(GRID_EXTENT, 1, COLORS.dynamicGrid, 0.25);
    this.transformedGroup.add(this.dynamicGrid);

    const squareGeometry = new THREE.PlaneGeometry(1, 1);
    squareGeometry.translate(0.5, 0.5, 0); // unit square spans [0,1]², not centered
    this.unitSquareMaterial = new THREE.MeshBasicMaterial({
      color: COLORS.unitSquare,
      transparent: true,
      opacity: 0.35,
      side: THREE.DoubleSide,
    });
    this.unitSquare = new THREE.Mesh(squareGeometry, this.unitSquareMaterial);
    this.transformedGroup.add(this.unitSquare);
    this.scene.add(this.transformedGroup);

    // Arrows live in world space; their tips are recomputed per frame.
    this.scene.add(this.basisI.object, this.basisJ.object);
    for (const arrow of this.eigenArrows) this.scene.add(arrow.object);

    // Initial sync + store subscription (the React ⇄ WebGL bridge).
    const state = useVisualizerStore.getState();
    this.lastTransformVersion = state.transformVersion;
    this.animator.snapTo(state.targetMatrix);
    this.applyMatrix(state.targetMatrix);
    this.updateEigenArrows(state.targetMatrix);
    this.syncUserVectors(state);
    this.syncVisibility(state);
    this.unsubscribeStore = useVisualizerStore.subscribe((s) => this.onStoreChange(s));

    this.resizeObserver = new ResizeObserver(() => this.handleResize());
    this.resizeObserver.observe(canvas.parentElement ?? canvas);
    this.handleResize();

    canvas.addEventListener('webglcontextlost', this.onContextLost, false);
    canvas.addEventListener('webglcontextrestored', this.onContextRestored, false);
    canvas.addEventListener('pointerdown', this.onPointerDown);
    canvas.addEventListener('pointermove', this.onPointerMove);
    canvas.addEventListener('pointerup', this.onPointerUp);
    canvas.addEventListener('pointercancel', this.onPointerUp);
  }

  start(): void {
    this.renderer.setAnimationLoop(() => this.frame());
  }

  private frame(): void {
    const dt = this.clock.getDelta();
    if (this.animator.isRunning) {
      this.applyMatrix(this.animator.update(dt));
    }
    this.syncWorldArrows(this.animator.current);
    this.renderer.render(this.scene, this.camera);
  }

  private onStoreChange(state: VisualizerState): void {
    if (state.transformVersion !== this.lastTransformVersion) {
      this.lastTransformVersion = state.transformVersion;
      if (prefersReducedMotion()) {
        this.animator.snapTo(state.targetMatrix);
        this.applyMatrix(state.targetMatrix);
      } else {
        this.animator.begin(
          this.animator.current,
          state.targetMatrix,
          BASE_TWEEN_SECONDS / state.animationSpeed,
        );
      }
      this.updateEigenArrows(state.targetMatrix);
      this.unitSquareMaterial.color.setHex(
        determinant(state.targetMatrix) < 0 ? COLORS.unitSquareFlipped : COLORS.unitSquare,
      );
    }
    this.syncUserVectors(state);
    this.syncVisibility(state);
  }

  /** Embed the 2×2 matrix into the group's 4×4 world matrix. */
  private applyMatrix(m: Mat2): void {
    // Matrix4.set takes ROW-major arguments — matches our Mat2 convention directly.
    this.transformedGroup.matrix.set(m.a, m.b, 0, 0, m.c, m.d, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1);
  }

  /** Recompute rigid arrow tips from the CURRENT animated matrix. */
  private syncWorldArrows(current: Mat2): void {
    this.basisI.setVector(apply(current, E1));
    this.basisJ.setVector(apply(current, E2));
    for (const entry of this.userVectors) {
      this.userArrows.get(entry.id)?.setVector(apply(current, entry.v));
    }
  }

  /** Create/remove arrow objects to mirror the store's user-vector list. */
  private syncUserVectors(state: VisualizerState): void {
    this.userVectors = state.userVectors;
    const liveIds = new Set(state.userVectors.map((v) => v.id));

    for (const [id, arrow] of this.userArrows) {
      if (!liveIds.has(id)) {
        this.scene.remove(arrow.object);
        arrow.dispose();
        this.userArrows.delete(id);
      }
    }
    for (const entry of state.userVectors) {
      if (!this.userArrows.has(entry.id)) {
        const arrow = new VectorArrow(new THREE.Color(entry.color).getHex());
        this.scene.add(arrow.object);
        this.userArrows.set(entry.id, arrow);
      }
    }
  }

  /** Show invariant directions (scaled by their eigenvalue) for real eigen cases. */
  private updateEigenArrows(target: Mat2): void {
    const result = eigen(target);
    const [first, second] = this.eigenArrows;
    if (result.kind === 'realDistinct') {
      first.setVector(scale(result.pairs[0].vector, result.pairs[0].value));
      second.setVector(scale(result.pairs[1].vector, result.pairs[1].value));
    } else if (result.kind === 'realRepeated') {
      first.setVector(scale(result.vector, result.value));
      second.setVisible(false);
    } else {
      // Complex pair: no real invariant line to draw; the readout explains the rotation.
      first.setVisible(false);
      second.setVisible(false);
    }
  }

  private syncVisibility(state: VisualizerState): void {
    this.dynamicGrid.visible = state.showGrid;
    this.unitSquare.visible = state.showUnitSquare;
    if (!state.showEigenvectors) {
      for (const arrow of this.eigenArrows) arrow.setVisible(false);
    } else {
      this.updateEigenArrows(state.targetMatrix);
    }
  }

  /* ── Drag interaction: move user vectors directly on the plane ─────────── */

  private readonly onPointerDown = (event: PointerEvent): void => {
    if (event.button !== 0 || this.userVectors.length === 0) return;
    const world = this.pointerToWorld(event);
    const current = this.animator.current;

    let best: { id: string; distance: number } | null = null;
    for (const entry of this.userVectors) {
      const tip = apply(current, entry.v);
      const distance = lengthOf(sub(tip, world));
      if (distance <= DRAG_PICK_RADIUS && (!best || distance < best.distance)) {
        best = { id: entry.id, distance };
      }
    }
    if (best) {
      this.draggingVectorId = best.id;
      this.canvas.setPointerCapture(event.pointerId);
    }
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (!this.draggingVectorId) return;
    // Dragging edits the vector in INPUT space: v = A(t)⁻¹ · pointer, so the
    // drawn arrow A(t)·v tracks the pointer exactly. Singular A: no inverse, ignore.
    const inv = inverse(this.animator.current);
    if (!inv) return;
    const world = this.pointerToWorld(event);
    useVisualizerStore.getState().updateVector(this.draggingVectorId, apply(inv, world));
  };

  private readonly onPointerUp = (event: PointerEvent): void => {
    if (this.draggingVectorId && this.canvas.hasPointerCapture(event.pointerId)) {
      this.canvas.releasePointerCapture(event.pointerId);
    }
    this.draggingVectorId = null;
  };

  private pointerToWorld(event: PointerEvent): Vec2 {
    const rect = this.canvas.getBoundingClientRect();
    const ndcX = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    const ndcY = -(((event.clientY - rect.top) / rect.height) * 2 - 1);
    // Camera is centered on the origin, so NDC scales directly by the frustum.
    return vec2(ndcX * this.camera.right, ndcY * this.camera.top);
  }

  /* ── WebGL context loss (TECH-NOTES §3.6) ──────────────────────────────── */

  private readonly onContextLost = (event: Event): void => {
    // preventDefault signals the browser we want the context restored.
    event.preventDefault();
  };

  private readonly onContextRestored = (): void => {
    // three re-uploads GPU resources lazily; nudge a fresh frame.
    this.renderer.resetState();
  };

  private handleResize(): void {
    const host = this.canvas.parentElement ?? this.canvas;
    const width = Math.max(1, host.clientWidth);
    const height = Math.max(1, host.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setSize(width, height, false);

    const halfHeight = VIEW_HEIGHT_WORLD / 2;
    const halfWidth = halfHeight * (width / height);
    this.camera.left = -halfWidth;
    this.camera.right = halfWidth;
    this.camera.top = halfHeight;
    this.camera.bottom = -halfHeight;
    this.camera.updateProjectionMatrix();
  }

  /** Exhaustive teardown — GPU resources don't garbage-collect (TECH-NOTES §3.6 pitfall 2). */
  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.renderer.setAnimationLoop(null);
    this.unsubscribeStore();
    this.resizeObserver.disconnect();

    this.canvas.removeEventListener('webglcontextlost', this.onContextLost, false);
    this.canvas.removeEventListener('webglcontextrestored', this.onContextRestored, false);
    this.canvas.removeEventListener('pointerdown', this.onPointerDown);
    this.canvas.removeEventListener('pointermove', this.onPointerMove);
    this.canvas.removeEventListener('pointerup', this.onPointerUp);
    this.canvas.removeEventListener('pointercancel', this.onPointerUp);

    this.basisI.dispose();
    this.basisJ.dispose();
    for (const arrow of this.eigenArrows) arrow.dispose();
    for (const arrow of this.userArrows.values()) arrow.dispose();
    this.userArrows.clear();
    this.scene.traverse((obj) => {
      if (
        obj instanceof THREE.Mesh ||
        obj instanceof THREE.LineSegments ||
        obj instanceof THREE.Line
      ) {
        obj.geometry.dispose();
        const material = obj.material;
        if (Array.isArray(material)) material.forEach((m) => m.dispose());
        else material.dispose();
      }
    });
    this.renderer.dispose();
  }
}

function prefersReducedMotion(): boolean {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

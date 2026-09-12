import * as THREE from 'three';

import type { Vec2 } from '@/core/math/vector2';

const HEAD_LENGTH = 0.35;
const HEAD_WIDTH = 0.22;
const SHAFT_WIDTH = 0.06;
const MIN_VISIBLE_LENGTH = 1e-4;

/**
 * A 2D arrow: thick quad shaft + triangle head, updated in place with zero
 * per-frame allocation. Arrows live in WORLD space and receive their already-
 * transformed tip via `setVector`, so heads stay rigid (no shearing) no matter
 * what the current matrix does.
 */
export class VectorArrow {
  readonly object = new THREE.Group();

  private readonly shaft: THREE.Mesh;
  private readonly head: THREE.Mesh;
  private readonly shaftGeometry: THREE.PlaneGeometry;
  private readonly headGeometry: THREE.BufferGeometry;
  private readonly material: THREE.MeshBasicMaterial;

  constructor(color: THREE.ColorRepresentation) {
    this.material = new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide });

    // Unit-length shaft along +x, spanning [0, 1] so scale.x = shaft length.
    this.shaftGeometry = new THREE.PlaneGeometry(1, SHAFT_WIDTH);
    this.shaftGeometry.translate(0.5, 0, 0);
    this.shaft = new THREE.Mesh(this.shaftGeometry, this.material);
    this.shaft.position.z = 0.005;

    // Head triangle: tip at local origin, pointing +x; placed at the arrow tip.
    this.headGeometry = new THREE.BufferGeometry();
    this.headGeometry.setAttribute(
      'position',
      new THREE.Float32BufferAttribute(
        [0, 0, 0, -HEAD_LENGTH, HEAD_WIDTH / 2, 0, -HEAD_LENGTH, -HEAD_WIDTH / 2, 0],
        3,
      ),
    );
    this.head = new THREE.Mesh(this.headGeometry, this.material);
    this.head.position.z = 0.01;

    this.object.add(this.shaft, this.head);
  }

  /** Point the arrow from the origin to `v` (world coordinates). */
  setVector(v: Vec2): void {
    const length = Math.hypot(v.x, v.y);
    this.object.visible = length > MIN_VISIBLE_LENGTH;
    if (!this.object.visible) return;

    this.object.rotation.z = Math.atan2(v.y, v.x);
    // Shaft stops where the head begins so the tip stays crisp.
    this.shaft.scale.x = Math.max(length - HEAD_LENGTH, MIN_VISIBLE_LENGTH);
    this.head.position.x = length;
  }

  setVisible(visible: boolean): void {
    this.object.visible = visible;
  }

  dispose(): void {
    this.shaftGeometry.dispose();
    this.headGeometry.dispose();
    this.material.dispose();
  }
}

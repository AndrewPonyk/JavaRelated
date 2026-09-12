export type { EditRecipe, FilterName, FilterRecipe, NormalizedCrop } from '../contracts/editor';

export interface CropRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export interface ImageMetadata {
  readonly fileName: string;
  readonly mimeType: string;
  readonly width: number;
  readonly height: number;
}

export interface ExportSettings {
  readonly mimeType: 'image/jpeg' | 'image/png' | 'image/webp';
  readonly quality: number;
  readonly maxWidth: number | null;
}

export type EditorStatus = 'idle' | 'decoding' | 'ready' | 'processing' | 'exporting' | 'error';

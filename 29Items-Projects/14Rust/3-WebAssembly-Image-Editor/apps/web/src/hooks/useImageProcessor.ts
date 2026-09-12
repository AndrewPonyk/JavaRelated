import { useCallback, useEffect, useRef, useState } from 'react';

import {
  editRecipeSchema,
  type EditRecipe,
  type FilterRecipe,
  type NormalizedCrop,
} from '../contracts/editor';
import { suggestCrop } from '../services/autoCrop';
import { decodeImage } from '../services/canvas';
import { fetchModelManifest } from '../services/modelManifest';
import { applyWasmFilter, cropWithWasm, disposeImageProcessor } from '../services/wasmClient';
import type { CropRect, EditorStatus, ImageMetadata } from '../types/image';

interface EditorState {
  readonly status: EditorStatus;
  readonly sourceImageData: ImageData | null;
  readonly imageData: ImageData | null;
  readonly metadata: ImageMetadata | null;
  readonly recipe: EditRecipe;
  readonly history: readonly EditRecipe[];
  readonly historyIndex: number;
  readonly error: string | null;
}

const emptyRecipe: EditRecipe = { schemaVersion: 1, filters: [] };

const initialState: EditorState = {
  status: 'idle',
  sourceImageData: null,
  imageData: null,
  metadata: null,
  recipe: emptyRecipe,
  history: [],
  historyIndex: -1,
  error: null,
};

function safeMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected image-processing error occurred.';
}

function cropToPixels(crop: NormalizedCrop, width: number, height: number): CropRect {
  const x = Math.floor(crop.x * width);
  const y = Math.floor(crop.y * height);
  const cropWidth = Math.max(1, Math.min(width - x, Math.round(crop.width * width)));
  const cropHeight = Math.max(1, Math.min(height - y, Math.round(crop.height * height)));
  return { x, y, width: cropWidth, height: cropHeight };
}

function pixelsToCrop(crop: CropRect, width: number, height: number): NormalizedCrop {
  return {
    x: crop.x / width,
    y: crop.y / height,
    width: crop.width / width,
    height: crop.height / height,
  };
}

async function renderRecipe(source: ImageData, recipe: EditRecipe): Promise<ImageData> {
  let result = source;
  for (const filter of recipe.filters) result = await applyWasmFilter(result, filter);
  if (recipe.crop)
    result = await cropWithWasm(result, cropToPixels(recipe.crop, source.width, source.height));
  return result;
}

export function useImageProcessor() {
  const [state, setState] = useState<EditorState>(initialState);
  const operationId = useRef(0);

  useEffect(() => () => disposeImageProcessor(), []);

  const renderAndCommit = useCallback(
    async (recipe: EditRecipe, saveHistory: boolean) => {
      if (!state.sourceImageData) return;
      const current = ++operationId.current;
      setState((previous) => ({ ...previous, status: 'processing', error: null }));
      try {
        const imageData = await renderRecipe(state.sourceImageData, recipe);
        if (operationId.current !== current) return;
        setState((previous) => {
          const history = saveHistory
            ? [...previous.history.slice(0, previous.historyIndex + 1), recipe].slice(-20)
            : previous.history;
          const historyIndex = saveHistory ? history.length - 1 : previous.historyIndex;
          return {
            ...previous,
            status: 'ready',
            imageData,
            recipe,
            history,
            historyIndex,
            error: null,
          };
        });
      } catch (error) {
        if (operationId.current !== current) return;
        setState((previous) => ({ ...previous, status: 'error', error: safeMessage(error) }));
      }
    },
    [state.sourceImageData],
  );

  const loadFile = useCallback(async (file: File) => {
    const current = ++operationId.current;
    setState({ ...initialState, status: 'decoding' });
    try {
      const decoded = await decodeImage(file);
      if (operationId.current !== current) return;
      setState({
        status: 'ready',
        sourceImageData: decoded.imageData,
        imageData: decoded.imageData,
        metadata: decoded.metadata,
        recipe: emptyRecipe,
        history: [emptyRecipe],
        historyIndex: 0,
        error: null,
      });
    } catch (error) {
      if (operationId.current !== current) return;
      setState({ ...initialState, status: 'error', error: safeMessage(error) });
    }
  }, []);

  const applyFilter = useCallback(
    async (filter: FilterRecipe) => {
      const valid = editRecipeSchema.parse({
        ...state.recipe,
        filters: [...state.recipe.filters, filter],
      });
      await renderAndCommit(valid, true);
    },
    [renderAndCommit, state.recipe],
  );

  const crop = useCallback(
    async (rect: CropRect) => {
      if (!state.sourceImageData) return;
      const normalized = pixelsToCrop(
        rect,
        state.sourceImageData.width,
        state.sourceImageData.height,
      );
      const valid = editRecipeSchema.parse({ ...state.recipe, crop: normalized });
      await renderAndCommit(valid, true);
    },
    [renderAndCommit, state.recipe, state.sourceImageData],
  );

  const applyRecipe = useCallback(
    async (recipe: EditRecipe) => renderAndCommit(editRecipeSchema.parse(recipe), true),
    [renderAndCommit],
  );

  const autoCrop = useCallback(async () => {
    if (!state.sourceImageData) return;
    const current = ++operationId.current;
    setState((previous) => ({ ...previous, status: 'processing', error: null }));
    try {
      const manifest = await fetchModelManifest();
      const suggestion = await suggestCrop(state.sourceImageData, manifest);
      if (operationId.current !== current) return;
      const recipe = editRecipeSchema.parse({ ...state.recipe, crop: suggestion.crop });
      const imageData = await renderRecipe(state.sourceImageData, recipe);
      if (operationId.current !== current) return;
      setState((previous) => {
        const history = [...previous.history.slice(0, previous.historyIndex + 1), recipe].slice(
          -20,
        );
        return {
          ...previous,
          status: 'ready',
          imageData,
          recipe,
          history,
          historyIndex: history.length - 1,
        };
      });
    } catch (error) {
      if (operationId.current !== current) return;
      setState((previous) => ({ ...previous, status: 'error', error: safeMessage(error) }));
    }
  }, [state.recipe, state.sourceImageData]);

  const moveHistory = useCallback(
    async (index: number) => {
      const recipe = state.history[index];
      if (!recipe || !state.sourceImageData) return;
      const current = ++operationId.current;
      setState((previous) => ({ ...previous, status: 'processing', error: null }));
      try {
        const imageData = await renderRecipe(state.sourceImageData, recipe);
        if (operationId.current !== current) return;
        setState((previous) => ({
          ...previous,
          status: 'ready',
          imageData,
          recipe,
          historyIndex: index,
        }));
      } catch (error) {
        if (operationId.current !== current) return;
        setState((previous) => ({ ...previous, status: 'error', error: safeMessage(error) }));
      }
    },
    [state.history, state.sourceImageData],
  );

  const undo = useCallback(
    async () => moveHistory(state.historyIndex - 1),
    [moveHistory, state.historyIndex],
  );
  const redo = useCallback(
    async () => moveHistory(state.historyIndex + 1),
    [moveHistory, state.historyIndex],
  );
  const reset = useCallback(async () => renderAndCommit(emptyRecipe, true), [renderAndCommit]);

  const clear = useCallback(() => {
    operationId.current += 1;
    setState(initialState);
  }, []);

  return {
    ...state,
    loadFile,
    applyFilter,
    crop,
    applyRecipe,
    autoCrop,
    undo,
    redo,
    reset,
    clear,
    canUndo: state.historyIndex > 0,
    canRedo: state.historyIndex >= 0 && state.historyIndex < state.history.length - 1,
  };
}

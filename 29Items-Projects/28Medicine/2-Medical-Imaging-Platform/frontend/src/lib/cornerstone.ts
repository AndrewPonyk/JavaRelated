// Cornerstone3D initialisation + stack rendering helpers.
//
// Centralises the one-time init of the rendering core, the DICOM (wadouri)
// image loader, and the tooling layer, plus a helper that renders a stack of
// imageIds into a DOM element and wires the standard radiology tools.
//
// dicom-image-loader@1.x uses the classic `external` / `configure` API (it has
// no `init()` and ships no types — see types/cornerstone-dicom-image-loader.d.ts).

import * as cornerstoneCore from '@cornerstonejs/core';
import {
  Enums,
  RenderingEngine,
  imageLoader,
  init as coreInit,
  metaData,
  type Types,
} from '@cornerstonejs/core';
import dicomImageLoader from '@cornerstonejs/dicom-image-loader';
import {
  PanTool,
  StackScrollMouseWheelTool,
  ToolGroupManager,
  WindowLevelTool,
  ZoomTool,
  addTool,
  Enums as ToolsEnums,
  init as toolsInit,
} from '@cornerstonejs/tools';

const RENDERING_ENGINE_ID = 'medimaging-engine';
const TOOL_GROUP_ID = 'medimaging-tools';
const VIEWPORT_ID = 'medimaging-viewport';

let initialized = false;

export async function ensureInitialized(): Promise<void> {
  if (initialized) return;
  await coreInit();
  await toolsInit();

  // Wire the DICOM image loader (classic API) and register the wadouri scheme.
  dicomImageLoader.external.cornerstone = cornerstoneCore;
  dicomImageLoader.configure({ useWebWorkers: false });
  imageLoader.registerImageLoader('wadouri', dicomImageLoader.wadouri.loadImage);
  metaData.addProvider(dicomImageLoader.wadouri.metaData.metaDataProvider);

  addTool(WindowLevelTool);
  addTool(PanTool);
  addTool(ZoomTool);
  addTool(StackScrollMouseWheelTool);
  initialized = true;
}

export interface ViewerHandle {
  destroy: () => void;
}

/** Render an ordered list of imageIds as a scrollable stack with tools wired. */
export async function renderStack(
  element: HTMLDivElement,
  imageIds: string[],
): Promise<ViewerHandle> {
  await ensureInitialized();

  const renderingEngine = new RenderingEngine(RENDERING_ENGINE_ID);
  renderingEngine.enableElement({
    viewportId: VIEWPORT_ID,
    type: Enums.ViewportType.STACK,
    element,
  });

  const viewport = renderingEngine.getViewport(VIEWPORT_ID) as Types.IStackViewport;
  await viewport.setStack(imageIds);
  viewport.render();

  // Tool group: left-drag window/level, middle-drag pan, right-drag zoom,
  // mouse-wheel scrolls the stack.
  ToolGroupManager.destroyToolGroup(TOOL_GROUP_ID);
  const toolGroup = ToolGroupManager.createToolGroup(TOOL_GROUP_ID);
  if (toolGroup) {
    toolGroup.addTool(WindowLevelTool.toolName);
    toolGroup.addTool(PanTool.toolName);
    toolGroup.addTool(ZoomTool.toolName);
    toolGroup.addTool(StackScrollMouseWheelTool.toolName);
    toolGroup.addViewport(VIEWPORT_ID, RENDERING_ENGINE_ID);

    toolGroup.setToolActive(WindowLevelTool.toolName, {
      bindings: [{ mouseButton: ToolsEnums.MouseBindings.Primary }],
    });
    toolGroup.setToolActive(PanTool.toolName, {
      bindings: [{ mouseButton: ToolsEnums.MouseBindings.Auxiliary }],
    });
    toolGroup.setToolActive(ZoomTool.toolName, {
      bindings: [{ mouseButton: ToolsEnums.MouseBindings.Secondary }],
    });
    toolGroup.setToolActive(StackScrollMouseWheelTool.toolName);
  }

  return {
    destroy: () => {
      ToolGroupManager.destroyToolGroup(TOOL_GROUP_ID);
      renderingEngine.destroy();
    },
  };
}

/** Build a wadouri imageId from a presigned object URL. */
export function toWadoUriImageId(presignedUrl: string): string {
  return `wadouri:${presignedUrl}`;
}

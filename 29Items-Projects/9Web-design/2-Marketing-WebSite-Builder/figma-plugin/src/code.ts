/// <reference types="@figma/plugin-typings" />

figma.showUI(__html__, { width: 360, height: 420 });

figma.ui.onmessage = async (message) => {
  if (message.type !== "export-selection") {
    return;
  }

  const selectedNodes = figma.currentPage.selection;

  const payload = selectedNodes.map((node) => ({
    id: node.id,
    name: node.name,
    type: node.type
  }));

  figma.ui.postMessage({
    type: "selection-exported",
    payload
  });
};

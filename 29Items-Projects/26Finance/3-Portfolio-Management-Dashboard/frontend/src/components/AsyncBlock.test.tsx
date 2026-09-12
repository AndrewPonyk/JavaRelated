import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AsyncBlock } from "./AsyncBlock";

describe("AsyncBlock", () => {
  it("renders a loading state", () => {
    render(
      <AsyncBlock loading error={null}>
        <span>content</span>
      </AsyncBlock>,
    );
    expect(screen.getByText("Loading…")).toBeTruthy();
  });

  it("renders an error state", () => {
    render(
      <AsyncBlock loading={false} error="boom">
        <span>content</span>
      </AsyncBlock>,
    );
    expect(screen.getByText(/boom/)).toBeTruthy();
  });

  it("renders children when loaded", () => {
    render(
      <AsyncBlock loading={false} error={null}>
        <span>hello</span>
      </AsyncBlock>,
    );
    expect(screen.getByText("hello")).toBeTruthy();
  });
});

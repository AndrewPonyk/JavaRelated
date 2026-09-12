import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { DatasetForm } from "./DatasetForm";

function fill(labelText: string, value: string) {
  fireEvent.change(screen.getByLabelText(labelText), { target: { value } });
}

const VALID = {
  Name: "sales.orders",
  "Owner email": "eng@example.com",
  "S3 location": "s3://bucket/sales/orders",
};

describe("DatasetForm", () => {
  it("blocks submission and shows field errors for invalid input", async () => {
    const onCreated = vi.fn();
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    render(<DatasetForm onCreated={onCreated} onClose={vi.fn()} />);
    fill("Name", "Not Valid Name!");
    fill("Owner email", "not-an-email");
    fill("S3 location", "http://nope");
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText(/lowercase identifiers/i)).toBeTruthy();
    expect(screen.getByText(/valid email/i)).toBeTruthy();
    expect(screen.getByText(/start with s3:/i)).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("submits a valid dataset and reports it back", async () => {
    const onCreated = vi.fn();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "d-1", name: "sales.orders" }), { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<DatasetForm onCreated={onCreated} onClose={vi.fn()} />);
    for (const [label, value] of Object.entries(VALID)) fill(label, value);
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith({ id: "d-1", name: "sales.orders" }));
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toMatchObject({
      name: "sales.orders",
      layer: "bronze",
      owner_email: "eng@example.com",
    });
  });

  it("surfaces API conflicts as a form error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ detail: "dataset 'sales.orders' already exists" }), {
          status: 409,
        }),
      ),
    );

    render(<DatasetForm onCreated={vi.fn()} onClose={vi.fn()} />);
    for (const [label, value] of Object.entries(VALID)) fill(label, value);
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("alert")).toHaveProperty(
      "textContent",
      "dataset 'sales.orders' already exists",
    );
  });
});

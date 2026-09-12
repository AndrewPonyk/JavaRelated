import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { api } from "../api/client";
import { DeviceList } from "../components/DeviceList";
import type { Device } from "../types";

vi.mock("../api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api/client")>();
  return { ...original, api: { ...original.api, listDevices: vi.fn() } };
});

const DEVICES: Device[] = [
  {
    device_id: "dev-1",
    name: "Boiler sensor",
    site: "lviv-lab",
    device_type: "thermo",
    enabled: true,
    created_at: null,
  },
  {
    device_id: "dev-2",
    name: "Roof anemometer",
    site: "lviv-lab",
    device_type: "wind",
    enabled: false,
    created_at: null,
  },
];

describe("DeviceList", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows a loading state, then the devices with status labels", async () => {
    (api.listDevices as Mock).mockResolvedValue(DEVICES);
    render(<DeviceList selected={null} onSelect={vi.fn()} canManage={false} />);

    expect(screen.getByText(/loading devices/i)).toBeTruthy();
    expect(await screen.findByText("Boiler sensor")).toBeTruthy();
    expect(screen.getByText(/● enabled/)).toBeTruthy();
    expect(screen.getByText(/■ disabled/)).toBeTruthy();
    // no manage buttons for viewers
    expect(screen.queryByRole("button", { name: /delete/i })).toBeNull();
  });

  it("shows manage actions for operators", async () => {
    (api.listDevices as Mock).mockResolvedValue(DEVICES);
    render(<DeviceList selected={null} onSelect={vi.fn()} canManage={true} />);

    await screen.findByText("Boiler sensor");
    expect(screen.getAllByRole("button", { name: /delete/i })).toHaveLength(2);
    expect(screen.getByRole("button", { name: /^disable$/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: /^enable$/i })).toBeTruthy();
  });

  it("surfaces fetch errors", async () => {
    (api.listDevices as Mock).mockRejectedValue(new Error("network down"));
    render(<DeviceList selected={null} onSelect={vi.fn()} canManage={false} />);

    expect(await screen.findByText(/could not load devices/i)).toBeTruthy();
  });

  it("shows the empty state", async () => {
    (api.listDevices as Mock).mockResolvedValue([]);
    render(<DeviceList selected={null} onSelect={vi.fn()} canManage={false} />);

    expect(await screen.findByText(/no devices registered/i)).toBeTruthy();
  });
});

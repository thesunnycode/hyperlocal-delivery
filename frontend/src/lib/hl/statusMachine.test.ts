import { describe, expect, it } from "vitest";
import {
  STATUSES,
  STATUS_META,
  canCancel,
  canReassignAgent,
  canRestoreToAssigned,
  isTerminal,
} from "./statusMachine";

describe("statusMachine", () => {
  it("has metadata for every status", () => {
    for (const status of STATUSES) {
      expect(STATUS_META[status]).toBeDefined();
    }
  });

  it("marks only delivered, returned and cancelled as terminal", () => {
    expect(isTerminal("delivered")).toBe(true);
    expect(isTerminal("returned")).toBe(true);
    expect(isTerminal("cancelled")).toBe(true);
    expect(isTerminal("assigned")).toBe(false);
    expect(isTerminal("failed")).toBe(false);
  });

  it("allows reassignment and cancellation only on non-terminal shipments", () => {
    expect(canReassignAgent("in_transit")).toBe(true);
    expect(canReassignAgent("delivered")).toBe(false);
    expect(canCancel("out_for_delivery")).toBe(true);
    expect(canCancel("cancelled")).toBe(false);
  });

  it("only lets a failed shipment be restored to assigned", () => {
    expect(canRestoreToAssigned("failed")).toBe(true);
    expect(canRestoreToAssigned("assigned")).toBe(false);
  });
});

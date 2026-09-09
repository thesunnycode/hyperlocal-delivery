import { describe, it, expect } from 'vitest';
import { isTerminal, canReassignAgent, canRestoreToAssigned, STATUSES, STATUS_META, AGENT_NEXT } from './statusMachine';

describe('statusMachine', () => {
  describe('isTerminal', () => {
    it('returns true for delivered', () => {
      expect(isTerminal('delivered')).toBe(true);
    });

    it('returns true for returned', () => {
      expect(isTerminal('returned')).toBe(true);
    });

    it('returns false for failed (owner can reassign)', () => {
      expect(isTerminal('failed')).toBe(false);
    });

    it('returns false for all in-flight statuses', () => {
      expect(isTerminal('assigned')).toBe(false);
      expect(isTerminal('picked_up')).toBe(false);
      expect(isTerminal('in_transit')).toBe(false);
      expect(isTerminal('out_for_delivery')).toBe(false);
    });
  });

  describe('canReassignAgent', () => {
    it('returns true for all non-terminal statuses', () => {
      expect(canReassignAgent('assigned')).toBe(true);
      expect(canReassignAgent('picked_up')).toBe(true);
      expect(canReassignAgent('in_transit')).toBe(true);
      expect(canReassignAgent('out_for_delivery')).toBe(true);
      expect(canReassignAgent('failed')).toBe(true);
    });

    it('returns false for terminal statuses', () => {
      expect(canReassignAgent('delivered')).toBe(false);
      expect(canReassignAgent('returned')).toBe(false);
    });
  });

  describe('canRestoreToAssigned', () => {
    it('returns true only for failed', () => {
      expect(canRestoreToAssigned('failed')).toBe(true);
    });

    it('returns false for all other statuses', () => {
      expect(canRestoreToAssigned('assigned')).toBe(false);
      expect(canRestoreToAssigned('delivered')).toBe(false);
      expect(canRestoreToAssigned('returned')).toBe(false);
      expect(canRestoreToAssigned('picked_up')).toBe(false);
    });
  });

  describe('STATUSES constant', () => {
    it('contains all 7 statuses', () => {
      expect(STATUSES).toHaveLength(7);
    });

    it('does not include CREATED', () => {
      expect(STATUSES).not.toContain('created');
    });
  });

  describe('STATUS_META', () => {
    it('has metadata for every status', () => {
      for (const s of STATUSES) {
        expect(STATUS_META[s]).toBeDefined();
        expect(STATUS_META[s].label).toBeTruthy();
        expect(STATUS_META[s].icon).toBeTruthy();
        expect(STATUS_META[s].stateClass).toBeTruthy();
      }
    });

    it('marks delivered and returned as systemTerminal', () => {
      expect(STATUS_META.delivered.systemTerminal).toBe(true);
      expect(STATUS_META.returned.systemTerminal).toBe(true);
    });

    it('marks failed as agentTerminal (not systemTerminal)', () => {
      expect(STATUS_META.failed.agentTerminal).toBe(true);
      expect(STATUS_META.failed.systemTerminal).toBeUndefined();
    });
  });

  describe('AGENT_NEXT', () => {
    it('defines forward transitions for non-terminal agent statuses', () => {
      expect(AGENT_NEXT.assigned?.next).toBe('picked_up');
      expect(AGENT_NEXT.picked_up?.next).toBe('in_transit');
      expect(AGENT_NEXT.in_transit?.next).toBe('out_for_delivery');
      expect(AGENT_NEXT.out_for_delivery?.next).toBe('delivered');
    });

    it('does not define next for terminal or owner-only statuses', () => {
      expect(AGENT_NEXT.delivered).toBeUndefined();
      expect(AGENT_NEXT.returned).toBeUndefined();
      expect(AGENT_NEXT.failed).toBeUndefined();
    });
  });
});

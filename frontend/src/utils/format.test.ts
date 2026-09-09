import { describe, it, expect } from 'vitest';
import { initials, shortToken, telHref, mapsHref, cx, isActionableError, formatTime, formatDateTime } from './format';

describe('format utilities', () => {
  describe('initials', () => {
    it('returns first letters of first two words', () => {
      expect(initials('Rahul Sharma')).toBe('RS');
    });

    it('handles single name', () => {
      expect(initials('Priya')).toBe('P');
    });

    it('handles three names (takes first two)', () => {
      expect(initials('John Michael Smith')).toBe('JM');
    });

    it('handles empty string', () => {
      expect(initials('')).toBe('');
    });

    it('handles undefined', () => {
      expect(initials()).toBe('');
    });

    it('uppercases the result', () => {
      expect(initials('alice bob')).toBe('AB');
    });
  });

  describe('shortToken', () => {
    it('truncates UUIDs to 8 chars + ellipsis', () => {
      const uuid = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
      expect(shortToken(uuid)).toBe('a1b2c3d4…');
    });

    it('returns short tokens as-is', () => {
      expect(shortToken('abc')).toBe('abc');
    });

    it('handles empty string', () => {
      expect(shortToken('')).toBe('');
    });

    it('handles undefined', () => {
      expect(shortToken()).toBe('');
    });
  });

  describe('telHref', () => {
    it('strips non-numeric chars except +', () => {
      expect(telHref('+91-9876-543210')).toBe('tel:+919876543210');
    });

    it('handles plain number', () => {
      expect(telHref('9876543210')).toBe('tel:9876543210');
    });

    it('handles empty', () => {
      expect(telHref('')).toBe('tel:');
    });
  });

  describe('cx', () => {
    it('joins truthy class names', () => {
      expect(cx('a', 'b', 'c')).toBe('a b c');
    });

    it('filters out falsy values', () => {
      expect(cx('a', null, undefined, false, '', 'b')).toBe('a b');
    });

    it('returns empty string for no truthy values', () => {
      expect(cx(null, false)).toBe('');
    });
  });

  describe('isActionableError', () => {
    it('returns true for 4xx errors (not 401/403)', () => {
      expect(isActionableError({ status: 400 })).toBe(true);
      expect(isActionableError({ status: 404 })).toBe(true);
      expect(isActionableError({ status: 422 })).toBe(true);
    });

    it('returns false for 401 and 403', () => {
      expect(isActionableError({ status: 401 })).toBe(false);
      expect(isActionableError({ status: 403 })).toBe(false);
    });

    it('returns false for 5xx errors', () => {
      expect(isActionableError({ status: 500 })).toBe(false);
      expect(isActionableError({ status: 502 })).toBe(false);
    });

    it('returns false for null/undefined', () => {
      expect(isActionableError(null)).toBe(false);
      expect(isActionableError(undefined)).toBe(false);
    });

    it('returns false if status is missing', () => {
      expect(isActionableError({ message: 'oops' })).toBe(false);
    });
  });

  // These two had no coverage, which is how the clock stayed locale-dependent
  // without anyone noticing: the same shipment read "4:30 PM" on one phone and
  // "16:30" on the next. The ISO strings below carry no zone, so they parse as
  // local time and these assertions hold in any timezone.
  describe('formatTime', () => {
    it('renders a 12-hour clock with AM/PM regardless of locale', () => {
      // Case-sensitive on purpose: the meridiem is normalised to upper case
      // so "pm" from a UK-style locale cannot slip through.
      expect(formatTime('2026-09-05T16:30:00')).toMatch(/^4:30\s?PM$/);
      expect(formatTime('2026-09-05T09:05:00')).toMatch(/^9:05\s?AM$/);
    });
    it('renders midnight and noon unambiguously', () => {
      expect(formatTime('2026-09-05T00:00:00')).toMatch(/^12:00\s?AM$/);
      expect(formatTime('2026-09-05T12:00:00')).toMatch(/^12:00\s?PM$/);
    });
    it('returns an em dash for null and undefined', () => {
      expect(formatTime(null)).toBe('—');
      expect(formatTime(undefined)).toBe('—');
    });
    it('returns the raw input when it is not a date', () => {
      expect(formatTime('not-a-date')).toBe('not-a-date');
    });
  });

  describe('formatDateTime', () => {
    it('uses the same 12-hour clock as formatTime', () => {
      expect(formatDateTime('2026-09-05T16:30:00')).toMatch(/4:30\s?PM/);
    });
    it('returns an em dash for null', () => {
      expect(formatDateTime(null)).toBe('—');
    });
  });

  describe('mapsHref', () => {
    it('percent-encodes the address into a universal Maps URL', () => {
      expect(mapsHref('55/1, Jayanagar 4th Block, Bengaluru 560011')).toBe(
        'https://www.google.com/maps/dir/?api=1&destination=55%2F1%2C%20Jayanagar%204th%20Block%2C%20Bengaluru%20560011'
      );
    });
    it('returns an empty string for a blank address, so callers can skip the link', () => {
      expect(mapsHref('')).toBe('');
      expect(mapsHref('   ')).toBe('');
      expect(mapsHref(undefined)).toBe('');
    });
  });
});

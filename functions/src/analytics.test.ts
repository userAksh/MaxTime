/**
 * Pure unit tests, no emulator needed — trackBackendEvent's only job is to never let a Mixpanel
 * outage or a missing token affect the caller (see analytics.ts's doc comment). Each test
 * re-requires the module after jest.resetModules() so the module-level client/initializedToken
 * cache never bleeds between cases.
 */
describe("trackBackendEvent", () => {
  const ORIGINAL_ENV = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...ORIGINAL_ENV };
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  it("is a no-op when MIXPANEL_TOKEN is unset — a blank token must never throw", () => {
    delete process.env.MIXPANEL_TOKEN;
    const init = jest.fn();
    jest.doMock("mixpanel", () => ({ init }));
    const { trackBackendEvent } = require("./analytics");

    expect(() => trackBackendEvent("payment_succeeded", "uid-1", {})).not.toThrow();
    expect(init).not.toHaveBeenCalled();
  });

  it("forwards distinct_id, schema_version, platform, and the given properties when a token is configured", () => {
    process.env.MIXPANEL_TOKEN = "test-token";
    const track = jest.fn();
    jest.doMock("mixpanel", () => ({ init: jest.fn(() => ({ track })) }));
    const { trackBackendEvent } = require("./analytics");

    trackBackendEvent("payment_succeeded", "uid-1", { booking_id: "b1", amount_minor: 5000 });

    expect(track).toHaveBeenCalledTimes(1);
    const [name, props] = track.mock.calls[0];
    expect(name).toBe("payment_succeeded");
    expect(props).toMatchObject({
      distinct_id: "uid-1",
      schema_version: 1,
      platform: "backend",
      booking_id: "b1",
      amount_minor: 5000,
    });
  });

  it("swallows a Mixpanel client error instead of throwing — the caller's transaction already committed", () => {
    process.env.MIXPANEL_TOKEN = "test-token";
    const track = jest.fn(() => {
      throw new Error("network down");
    });
    jest.doMock("mixpanel", () => ({ init: jest.fn(() => ({ track })) }));
    const { trackBackendEvent } = require("./analytics");

    expect(() => trackBackendEvent("payment_failed", "uid-1", {})).not.toThrow();
    expect(track).toHaveBeenCalledTimes(1);
  });

  it("reuses the same client across calls while the token stays unchanged", () => {
    process.env.MIXPANEL_TOKEN = "test-token";
    const init = jest.fn(() => ({ track: jest.fn() }));
    jest.doMock("mixpanel", () => ({ init }));
    const { trackBackendEvent } = require("./analytics");

    trackBackendEvent("payment_succeeded", "uid-1", {});
    trackBackendEvent("booking_confirmed", "uid-1", {});

    expect(init).toHaveBeenCalledTimes(1);
  });
});

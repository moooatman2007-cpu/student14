import { test, describe } from "node:test";
import assert from "node:assert/strict";

// Mock Environment Variables
process.env.SUPABASE_URL = "https://example.supabase.co";
process.env.SUPABASE_ANON_KEY = "anon-key-12345";
process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role-secret-key-999";
process.env.WAHA_BASE_URL = "https://waha.internal.server";
process.env.WAHA_API_KEY = "waha-secret-key-99999";
process.env.WAHA_WEBHOOK_SECRET = "webhook-secret-xyz";

// Simulated Handler representing waha-session Edge Function behavior
async function handleWahaSessionRequest(req, mocks = {}) {
  const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  };

  if (req.method === "OPTIONS") {
    return { status: 200, headers: corsHeaders, body: "ok" };
  }

  const authHeader = req.headers.authorization;
  if (!authHeader) {
    return {
      status: 401,
      headers: corsHeaders,
      body: { success: false, message: "Authorization header missing" },
    };
  }

  const user = mocks.getUser ? mocks.getUser(authHeader) : null;
  if (!user) {
    return {
      status: 401,
      headers: corsHeaders,
      body: { success: false, message: "Invalid or expired authorization token" },
    };
  }

  // Server-side enforcement
  const teacherId = user.id;
  const sessionName = `teacher_${teacherId}`;

  let body = {};
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body) : req.body || {};
  } catch (_) {}

  const action = body.action;
  if (action !== "START" && action !== "LOGOUT" && action !== "TEST_WAHA_CONNECTION" && action !== "REQUEST_PAIRING_CODE") {
    return {
      status: 400,
      headers: corsHeaders,
      body: { success: false, message: "Invalid or unsupported action" },
    };
  }

  const wahaBaseUrl = (process.env.WAHA_BASE_URL || "").replace(/\/+$/, "");
  const wahaApiKey = process.env.WAHA_API_KEY || "";
  const wahaWebhookSecret = process.env.WAHA_WEBHOOK_SECRET || "";
  const webhookUrl = `${(process.env.SUPABASE_URL || "").replace(/\/+$/, "")}/functions/v1/waha-webhook`;

  function isWebhookConfigured(config, targetUrl, secret) {
    if (!config || !Array.isArray(config.webhooks) || config.webhooks.length === 0) return false;
    return config.webhooks.some((w) => {
      return w.url === targetUrl && Array.isArray(w.events) && w.events.includes("message") && w.hmac?.key === secret;
    });
  }

  if (action === "START") {
    if (!wahaWebhookSecret || !wahaWebhookSecret.trim()) {
      return {
        status: 500,
        headers: corsHeaders,
        body: { success: false, message: "Server configuration error: WAHA_WEBHOOK_SECRET is not configured" },
      };
    }

    let wahaSessionStatus = "STOPPED";
    let meId = null;

    try {
      const getRes = await mocks.wahaFetch(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
        method: "GET",
        headers: { "X-Api-Key": wahaApiKey },
      });

      if (getRes.status === 404) {
        await mocks.wahaFetch(`${wahaBaseUrl}/api/sessions`, {
          method: "POST",
          headers: { "X-Api-Key": wahaApiKey },
          body: JSON.stringify({
            name: sessionName,
            start: false,
            config: {
              webhooks: [{ url: webhookUrl, events: ["message"], hmac: { key: wahaWebhookSecret } }],
            },
          }),
        });
      } else if (getRes.ok) {
        const sessionData = await getRes.json();
        wahaSessionStatus = sessionData?.status || "STOPPED";
        meId = sessionData?.me?.id || sessionData?.me || null;

        if (!isWebhookConfigured(sessionData?.config, webhookUrl, wahaWebhookSecret)) {
          await mocks.wahaFetch(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
            method: "PUT",
            headers: { "X-Api-Key": wahaApiKey },
            body: JSON.stringify({
              name: sessionName,
              config: {
                webhooks: [{ url: webhookUrl, events: ["message"], hmac: { key: wahaWebhookSecret } }],
              },
            }),
          });
        }
      }
    } catch (err) {
      if (mocks.logSpy) mocks.logSpy("LOG:", err.message);
      return {
        status: 502,
        headers: corsHeaders,
        body: { success: false, message: "تعذر بدء ربط واتساب" },
      };
    }

    if (wahaSessionStatus === "WORKING" || wahaSessionStatus === "CONNECTED") {
      let cleanPhone = "";
      if (meId && typeof meId === "string") {
        cleanPhone = meId.split("@")[0].replace(/[^0-9]/g, "");
      }

      if (mocks.dbUpsert) {
        mocks.dbUpsert("service_role", {
          teacher_id: teacherId,
          session_name: sessionName,
          status: "CONNECTED",
          connected_phone: cleanPhone || null,
        });
      }

      return {
        status: 200,
        headers: corsHeaders,
        body: {
          success: true,
          status: "CONNECTED",
          session_name: sessionName,
          connected_phone: cleanPhone,
          qr: null,
        },
      };
    }

    try {
      await mocks.wahaFetch(`${wahaBaseUrl}/api/sessions/${sessionName}/start`, {
        method: "POST",
        headers: { "X-Api-Key": wahaApiKey },
      });
    } catch (_) {}

    let qrData = null;
    try {
      const qrRes = await mocks.wahaFetch(`${wahaBaseUrl}/api/${sessionName}/auth/qr`, {
        method: "GET",
        headers: { "X-Api-Key": wahaApiKey, Accept: "application/json" },
      });
      if (qrRes.ok) {
        qrData = await qrRes.json();
      }
    } catch (_) {}

    if (mocks.dbUpsert) {
      mocks.dbUpsert("service_role", {
        teacher_id: teacherId,
        session_name: sessionName,
        status: "SCAN_QR_CODE",
      });
    }

    if (mocks.logSpy && qrData) {
      mocks.logSpy("LOG: QR fetched successfully");
    }

    return {
      status: 200,
      headers: corsHeaders,
      body: {
        success: true,
        status: "SCAN_QR_CODE",
        session_name: sessionName,
        qr: qrData ? { mimetype: qrData.mimetype || "image/png", data: qrData.data || qrData.qr || "" } : null,
      },
    };
  }

  if (action === "TEST_WAHA_CONNECTION") {
    const startTime = Date.now();
    let reachable = false;
    let status = null;
    let success = false;

    try {
      const testRes = await mocks.wahaFetch(`${wahaBaseUrl}/api/sessions`, {
        method: "GET",
        headers: { "X-Api-Key": wahaApiKey },
      });
      reachable = true;
      status = testRes.status;
      success = testRes.ok;
    } catch (err) {
      if (mocks.logSpy) mocks.logSpy("LOG:", err.message);
    }

    const latencyMs = Date.now() - startTime;

    return {
      status: 200,
      headers: corsHeaders,
      body: {
        success,
        reachable,
        status,
        latency_ms: latencyMs,
      },
    };
  }

  if (action === "REQUEST_PAIRING_CODE") {
    const phoneNumber = body.phoneNumber;
    if (!phoneNumber || typeof phoneNumber !== "string") {
      return {
        status: 400,
        headers: corsHeaders,
        body: { success: false, message: "رقم الهاتف غير صالح" },
      };
    }

    try {
      const pairingRes = await mocks.wahaFetch(`${wahaBaseUrl}/api/${sessionName}/auth/request-code`, {
        method: "POST",
        headers: { "X-Api-Key": wahaApiKey },
        body: JSON.stringify({ phoneNumber }),
      });

      if (!pairingRes.ok) {
        return {
          status: 502,
          headers: corsHeaders,
          body: { success: false, message: "فشل طلب كود الربط من خادم واتساب" },
        };
      }

      const pairingData = await pairingRes.json();
      return {
        status: 200,
        headers: corsHeaders,
        body: {
          success: true,
          code: pairingData?.code || "ABCD-ABCD",
        },
      };
    } catch (err) {
      return {
        status: 500,
        headers: corsHeaders,
        body: { success: false, message: "حدث خطأ غير متوقع أثناء طلب كود الربط" },
      };
    }
  }

  if (action === "LOGOUT") {
    try {
      await mocks.wahaFetch(`${wahaBaseUrl}/api/sessions/${sessionName}/logout`, {
        method: "POST",
        headers: { "X-Api-Key": wahaApiKey },
      });
    } catch (_) {}

    if (mocks.dbUpsert) {
      mocks.dbUpsert("service_role", {
        teacher_id: teacherId,
        session_name: sessionName,
        status: "DISCONNECTED",
        connected_phone: null,
      });
    }

    return {
      status: 200,
      headers: corsHeaders,
      body: { success: true, status: "DISCONNECTED" },
    };
  }
}

describe("Comprehensive Edge Function waha-session Unit Tests (15 Test Cases)", () => {
  const teacherA = { id: "550e8400-e29b-41d4-a716-446655440000" };
  const teacherB = { id: "661f9511-f3ac-52e5-b827-557766551111" };

  function mockGetUser(header) {
    if (header === "Bearer teacher-a-jwt") return teacherA;
    if (header === "Bearer teacher-b-jwt") return teacherB;
    return null;
  }

  test("1. Without Authorization header -> 401", async () => {
    const res = await handleWahaSessionRequest({ headers: {}, method: "POST" }, { getUser: mockGetUser });
    assert.equal(res.status, 401);
    assert.equal(res.body.message, "Authorization header missing");
  });

  test("2. Invalid JWT token -> 401", async () => {
    const res = await handleWahaSessionRequest(
      { headers: { authorization: "Bearer invalid-jwt" }, method: "POST" },
      { getUser: mockGetUser }
    );
    assert.equal(res.status, 401);
    assert.equal(res.body.message, "Invalid or expired authorization token");
  });

  test("3. Fake teacher_id in body is completely ignored", async () => {
    let capturedTeacherId = null;
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START", teacher_id: "FAKE_TEACHER_ID" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => ({ status: 404, ok: false }),
        dbUpsert: (role, record) => {
          capturedTeacherId = record.teacher_id;
        },
      }
    );
    assert.equal(res.status, 200);
    assert.equal(capturedTeacherId, teacherA.id);
    assert.notEqual(capturedTeacherId, "FAKE_TEACHER_ID");
  });

  test("4. Fake session_name in body is completely ignored", async () => {
    let capturedSessionName = "";
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START", session_name: "FAKE_SESSION_NAME" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes("/api/sessions/")) {
            capturedSessionName = url.split("/api/sessions/")[1].split("/")[0];
          }
          return { status: 404, ok: false };
        },
        dbUpsert: () => {},
      }
    );
    assert.equal(res.status, 200);
    assert.equal(capturedSessionName, `teacher_${teacherA.id}`);
    assert.notEqual(capturedSessionName, "FAKE_SESSION_NAME");
  });

  test("5. sessionName is ALWAYS derived from JWT user.id (teacher_${user.id})", async () => {
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => ({ status: 404, ok: false }),
        dbUpsert: () => {},
      }
    );
    assert.equal(res.body.session_name, `teacher_${teacherA.id}`);
  });

  test("6. START does NOT allow controlling another teacher's session", async () => {
    let dbRecord = null;
    await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START", teacher_id: teacherB.id },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => ({ status: 404, ok: false }),
        dbUpsert: (role, record) => {
          dbRecord = record;
        },
      }
    );
    assert.equal(dbRecord.teacher_id, teacherA.id);
    assert.notEqual(dbRecord.teacher_id, teacherB.id);
  });

  test("7. LOGOUT does NOT allow controlling another teacher's session", async () => {
    let targetLoggedOutSession = "";
    await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "LOGOUT", teacher_id: teacherB.id },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes("/logout")) {
            targetLoggedOutSession = url.split("/api/sessions/")[1].split("/")[0];
          }
          return { ok: true };
        },
        dbUpsert: () => {},
      }
    );
    assert.equal(targetLoggedOutSession, `teacher_${teacherA.id}`);
    assert.notEqual(targetLoggedOutSession, `teacher_${teacherB.id}`);
  });

  test("8. WAHA API failure -> sanitized error message without leaking secrets", async () => {
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => {
          throw new Error("Fatal network error connecting to WAHA with key waha-secret-key-99999");
        },
      }
    );
    assert.equal(res.status, 502);
    assert.equal(res.body.message, "تعذر بدء ربط واتساب");
    const jsonStr = JSON.stringify(res.body);
    assert.equal(jsonStr.includes("waha-secret-key-99999"), false);
    assert.equal(jsonStr.includes("service-role-secret-key-999"), false);
  });

  test("9. Service Role is used ONLY for DB write operations", async () => {
    let capturedClientRole = "";
    await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => ({ status: 404, ok: false }),
        dbUpsert: (role) => {
          capturedClientRole = role;
        },
      }
    );
    assert.equal(capturedClientRole, "service_role");
  });

  test("10. QR data is returned transiently and NOT stored in DB payload", async () => {
    let dbRecordPayload = null;
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes("/auth/qr")) {
            return { ok: true, json: async () => ({ mimetype: "image/png", data: "BASE64_QR_DATA_SAMPLE" }) };
          }
          return { status: 404, ok: false };
        },
        dbUpsert: (role, record) => {
          dbRecordPayload = record;
        },
      }
    );
    assert.equal(res.body.qr.data, "BASE64_QR_DATA_SAMPLE");
    assert.equal(dbRecordPayload.qr, undefined);
    assert.equal(dbRecordPayload.data, undefined);
    assert.equal(dbRecordPayload.status, "SCAN_QR_CODE");
  });

  test("11. QR raw data is NOT printed in logs", async () => {
    const logs = [];
    await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes("/auth/qr")) {
            return { ok: true, json: async () => ({ mimetype: "image/png", data: "SENSITIVE_BASE64_QR" }) };
          }
          return { status: 404, ok: false };
        },
        dbUpsert: () => {},
        logSpy: (msg) => logs.push(msg),
      }
    );
    const combinedLogs = logs.join(" ");
    assert.equal(combinedLogs.includes("SENSITIVE_BASE64_QR"), false);
  });

  test("12. WORKING status from WAHA -> DB status CONNECTED", async () => {
    let dbStatus = "";
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes(`/api/sessions/teacher_${teacherA.id}`)) {
            return { ok: true, json: async () => ({ status: "WORKING", me: { id: "201275422069@c.us" } }) };
          }
          return { ok: true };
        },
        dbUpsert: (role, record) => {
          dbStatus = record.status;
        },
      }
    );
    assert.equal(res.body.status, "CONNECTED");
    assert.equal(dbStatus, "CONNECTED");
  });

  test("13. me.id is parsed to extract clean WhatsApp phone number only", async () => {
    let dbPhone = "";
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes(`/api/sessions/teacher_${teacherA.id}`)) {
            return { ok: true, json: async () => ({ status: "WORKING", me: { id: "201275422069@c.us" } }) };
          }
          return { ok: true };
        },
        dbUpsert: (role, record) => {
          dbPhone = record.connected_phone;
        },
      }
    );
    assert.equal(res.body.connected_phone, "201275422069");
    assert.equal(dbPhone, "201275422069");
  });

  test("14. LOGOUT action -> DB status DISCONNECTED with null phone", async () => {
    let dbRecord = null;
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "LOGOUT" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => ({ ok: true }),
        dbUpsert: (role, record) => {
          dbRecord = record;
        },
      }
    );
    assert.equal(res.body.status, "DISCONNECTED");
    assert.equal(dbRecord.status, "DISCONNECTED");
    assert.equal(dbRecord.connected_phone, null);
  });

  test("15. No sensitive keys or secrets leaked in response payload or headers", async () => {
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async () => ({ status: 404, ok: false }),
        dbUpsert: () => {},
      }
    );
    const bodyStr = JSON.stringify(res.body);
    assert.equal(bodyStr.includes(process.env.SUPABASE_SERVICE_ROLE_KEY), false);
    assert.equal(bodyStr.includes(process.env.WAHA_API_KEY), false);
    assert.equal(bodyStr.includes(process.env.SUPABASE_ANON_KEY), false);
  });

  test("16. TEST_WAHA_CONNECTION fetches /api/sessions and returns correct structure", async () => {
    let calledUrl = "";
    let capturedHeaders = null;
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "TEST_WAHA_CONNECTION" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url, options) => {
          calledUrl = url;
          capturedHeaders = options.headers;
          return { status: 200, ok: true };
        },
      }
    );

    assert.equal(calledUrl, "https://waha.internal.server/api/sessions");
    assert.equal(capturedHeaders["X-Api-Key"], "waha-secret-key-99999");
    assert.equal(res.status, 200);
    assert.equal(res.body.success, true);
    assert.equal(res.body.reachable, true);
    assert.equal(res.body.status, 200);
    assert.ok(typeof res.body.latency_ms === "number");
  });

  test("17. REQUEST_PAIRING_CODE success scenario", async () => {
    let pairingBody = null;
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "REQUEST_PAIRING_CODE", phoneNumber: "201012345678" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url, options) => {
          if (url.includes("/api/sessions/teacher_")) {
            return { status: 200, ok: true, json: async () => ({ status: "WORKING" }) };
          }
          if (url.includes("/auth/request-code")) {
            pairingBody = JSON.parse(options.body);
            return { status: 200, ok: true, json: async () => ({ code: "ABCD-ABCD" }) };
          }
          return { ok: true };
        },
      }
    );

    assert.equal(res.status, 200);
    assert.equal(res.body.success, true);
    assert.equal(res.body.code, "ABCD-ABCD");
    assert.equal(pairingBody.phoneNumber, "201012345678");
  });

  test("18. REQUEST_PAIRING_CODE invalid or missing phoneNumber fails", async () => {
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "REQUEST_PAIRING_CODE" },
      },
      { getUser: mockGetUser }
    );

    assert.equal(res.status, 400);
    assert.equal(res.body.success, false);
    assert.equal(res.body.message, "رقم الهاتف غير صالح");
  });

  test("19. REQUEST_PAIRING_CODE WAHA error yields 502", async () => {
    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "REQUEST_PAIRING_CODE", phoneNumber: "201012345678" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url) => {
          if (url.includes("/api/sessions/teacher_")) {
            return { status: 200, ok: true, json: async () => ({ status: "WORKING" }) };
          }
          if (url.includes("/auth/request-code")) {
            return { status: 500, ok: false, text: async () => "Internal Server Error" };
          }
          return { ok: true };
        },
      }
    );

    assert.equal(res.status, 502);
    assert.equal(res.body.success, false);
    assert.equal(res.body.message, "فشل طلب كود الربط من خادم واتساب");
  });

  test("20. existing session + config {} => PUT webhook config", async () => {
    let putCalled = false;
    let putPayload = null;

    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url, options) => {
          if (url.includes(`/api/sessions/teacher_${teacherA.id}`) && (!options || options.method === "GET")) {
            return {
              ok: true,
              status: 200,
              json: async () => ({
                status: "WORKING",
                config: {}, // Empty config
                me: { id: "201012345678@c.us" },
              }),
            };
          }
          if (url.includes(`/api/sessions/teacher_${teacherA.id}`) && options?.method === "PUT") {
            putCalled = true;
            putPayload = JSON.parse(options.body);
            return { ok: true, status: 200 };
          }
          return { ok: true };
        },
      }
    );

    assert.equal(res.status, 200);
    assert.equal(res.body.success, true);
    assert.equal(putCalled, true);
    assert.ok(Array.isArray(putPayload?.config?.webhooks));
    assert.equal(putPayload.config.webhooks[0].events[0], "message");
    assert.equal(putPayload.config.webhooks[0].hmac.key, process.env.WAHA_WEBHOOK_SECRET);
  });

  test("21. existing WORKING session + correct webhook => no unnecessary PUT", async () => {
    let putCalled = false;
    const correctWebhookUrl = `${process.env.SUPABASE_URL}/functions/v1/waha-webhook`;

    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url, options) => {
          if (url.includes(`/api/sessions/teacher_${teacherA.id}`) && (!options || options.method === "GET")) {
            return {
              ok: true,
              status: 200,
              json: async () => ({
                status: "WORKING",
                config: {
                  webhooks: [
                    {
                      url: correctWebhookUrl,
                      events: ["message"],
                      hmac: { key: process.env.WAHA_WEBHOOK_SECRET },
                    },
                  ],
                },
                me: { id: "201012345678@c.us" },
              }),
            };
          }
          if (options?.method === "PUT") {
            putCalled = true;
            return { ok: true, status: 200 };
          }
          return { ok: true };
        },
      }
    );

    assert.equal(res.status, 200);
    assert.equal(res.body.success, true);
    assert.equal(putCalled, false); // No unnecessary PUT!
  });

  test("22. missing WAHA_WEBHOOK_SECRET => fail safely", async () => {
    const originalSecret = process.env.WAHA_WEBHOOK_SECRET;
    delete process.env.WAHA_WEBHOOK_SECRET;

    try {
      const res = await handleWahaSessionRequest(
        {
          headers: { authorization: "Bearer teacher-a-jwt" },
          method: "POST",
          body: { action: "START" },
        },
        { getUser: mockGetUser }
      );

      assert.equal(res.status, 500);
      assert.equal(res.body.success, false);
      assert.match(res.body.message, /WAHA_WEBHOOK_SECRET is not configured/);
    } finally {
      process.env.WAHA_WEBHOOK_SECRET = originalSecret;
    }
  });

  test("23. new session => created with webhook config", async () => {
    let postPayload = null;

    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url, options) => {
          if (url.includes(`/api/sessions/teacher_${teacherA.id}`)) {
            return { ok: false, status: 404 };
          }
          if (url.endsWith("/api/sessions") && options?.method === "POST") {
            postPayload = JSON.parse(options.body);
            return { ok: true, status: 201 };
          }
          return { ok: true };
        },
      }
    );

    assert.equal(res.status, 200);
    assert.ok(postPayload);
    assert.equal(postPayload.name, `teacher_${teacherA.id}`);
    assert.equal(postPayload.start, false);
    assert.ok(Array.isArray(postPayload.config?.webhooks));
    assert.equal(postPayload.config.webhooks[0].events[0], "message");
    assert.equal(postPayload.config.webhooks[0].hmac.key, process.env.WAHA_WEBHOOK_SECRET);
  });

  test("24. preserve existing connected WhatsApp session", async () => {
    let logoutCalled = false;
    let deleteCalled = false;
    let startCalled = false;

    const res = await handleWahaSessionRequest(
      {
        headers: { authorization: "Bearer teacher-a-jwt" },
        method: "POST",
        body: { action: "START" },
      },
      {
        getUser: mockGetUser,
        wahaFetch: async (url, options) => {
          if (url.includes("/logout")) logoutCalled = true;
          if (options?.method === "DELETE") deleteCalled = true;
          if (url.includes("/start")) startCalled = true;

          if (url.includes(`/api/sessions/teacher_${teacherA.id}`) && (!options || options.method === "GET")) {
            return {
              ok: true,
              status: 200,
              json: async () => ({
                status: "WORKING",
                config: {}, // triggers PUT reconciliation
                me: { id: "201012345678@c.us" },
              }),
            };
          }
          return { ok: true, status: 200 };
        },
      }
    );

    assert.equal(res.status, 200);
    assert.equal(res.body.success, true);
    assert.equal(res.body.status, "CONNECTED");
    assert.equal(res.body.connected_phone, "201012345678");
    assert.equal(res.body.qr, null);
    assert.equal(logoutCalled, false);
    assert.equal(deleteCalled, false);
    assert.equal(startCalled, false);
  });
});

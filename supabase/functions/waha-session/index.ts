import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface RequestBody {
  action?: string;
  phoneNumber?: string;
  teacher_id?: string; // Ignored: derived from JWT
  session_name?: string; // Ignored: derived from JWT
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

function normalizeEgyptianPhone(value: string): string | null {
  if (typeof value !== "string") return null;
  const digits = value.replace(/\D/g, "");
  if (/^01[0125][0-9]{8}$/.test(digits)) return `20${digits.slice(1)}`;
  if (/^201[0125][0-9]{8}$/.test(digits)) return digits;
  if (/^00201[0125][0-9]{8}$/.test(digits)) return digits.slice(2);
  return digits.length >= 8 ? digits : null;
}

function isWorkingStatus(status: unknown): boolean {
  return status === "WORKING" || status === "CONNECTED";
}

function normalizeStatus(status: unknown): string {
  if (typeof status !== "string") return "DISCONNECTED";
  const s = status.toUpperCase();
  if (s === "WORKING" || s === "CONNECTED") return s;
  if (s === "SCAN_QR_CODE" || s === "STARTING") return "SCAN_QR_CODE";
  if (s === "STOPPED" || s === "DISCONNECTED") return "DISCONNECTED";
  if (s === "FAILED") return "FAILED";
  return "DISCONNECTED";
}

async function fetchWithTimeout(
  url: string,
  options: RequestInit = {},
  timeoutMs = 12000
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    clearTimeout(timeoutId);
    return res;
  } catch (err) {
    clearTimeout(timeoutId);
    throw err;
  }
}

async function readResponseBody(res: Response): Promise<unknown> {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function extractPhoneFromMePayload(meData: any): string | null {
  if (!meData) return null;
  let rawStr = "";

  if (typeof meData === "string") {
    rawStr = meData;
  } else if (typeof meData === "object") {
    if (typeof meData.id === "string") {
      rawStr = meData.id;
    } else if (meData.id && typeof meData.id === "object") {
      rawStr = meData.id._serialized || meData.id.user || meData.id.id || "";
    } else if (typeof meData.user === "string") {
      rawStr = meData.user;
    } else if (typeof meData._serialized === "string") {
      rawStr = meData._serialized;
    }
  }

  if (!rawStr) return null;
  const digits = rawStr.split("@")[0].replace(/\D/g, "");
  return digits.length >= 8 ? digits : null;
}

async function fetchWahaMePhone(
  wahaBaseUrl: string,
  sessionName: string,
  wahaHeaders: Record<string, string>
): Promise<string | null> {
  try {
    const res = await fetchWithTimeout(
      `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/me`,
      { method: "GET", headers: wahaHeaders },
      5000
    );
    if (res.ok) {
      const meData = await readResponseBody(res);
      return extractPhoneFromMePayload(meData);
    }
  } catch (_err) {
    // Ignore error
  }
  return null;
}

Deno.serve(async (req: Request) => {
  // 1. Handle CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ success: false, error: "Method not allowed" }, 405);
  }

  try {
    // 2. Parse request body
    let body: RequestBody = {};
    try {
      body = await req.json();
    } catch {
      body = {};
    }

    const action = body.action || "TEST_WAHA_CONNECTION";
    const supportedActions = new Set([
      "START",
      "LOGOUT",
      "TEST_WAHA_CONNECTION",
      "REQUEST_PAIRING_CODE",
    ]);

    if (!supportedActions.has(action)) {
      return jsonResponse(
        { success: false, message: `Unsupported action: ${action}` },
        400
      );
    }

    // 3. Validate User Authentication via JWT
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return jsonResponse(
        { success: false, message: "Authorization header missing or invalid" },
        401
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";
    const supabaseServiceRoleKey =
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

    if (!supabaseServiceRoleKey) {
      return jsonResponse(
        {
          success: false,
          message: "Server configuration error: Service role key missing",
        },
        500
      );
    }

    const authClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const {
      data: { user },
      error: authError,
    } = await authClient.auth.getUser();

    if (authError || !user) {
      return jsonResponse(
        { success: false, message: "Invalid or expired authorization token" },
        401
      );
    }

    // 4. Server-Side DB Client using Service Role Key
    const dbClient = createClient(supabaseUrl, supabaseServiceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    // 5. Derive Teacher ID & Session Name strictly server-side
    const teacherId = user.id;
    const sessionName = `teacher_${teacherId}`;

    // 6. Read WAHA Environment Secrets
    const wahaBaseUrl = (Deno.env.get("WAHA_BASE_URL") || "").replace(
      /\/+$/,
      ""
    );
    const wahaApiKey = Deno.env.get("WAHA_API_KEY") || "";

    if (!wahaBaseUrl) {
      return jsonResponse(
        { success: false, message: "WAHA_BASE_URL is not configured" },
        500
      );
    }

    const wahaHeaders: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json",
    };
    if (wahaApiKey) {
      wahaHeaders["X-Api-Key"] = wahaApiKey;
    }

    const wahaWebhookSecret = Deno.env.get("WAHA_WEBHOOK_SECRET") || "";
    const webhookUrl = `${supabaseUrl.replace(/\/+$/, "")}/functions/v1/waha-webhook`;

    function buildSessionConfig(url: string, secret: string) {
      return {
        webhooks: [
          {
            url,
            events: ["message"],
            hmac: {
              key: secret,
            },
          },
        ],
      };
    }

    function isWebhookConfigured(
      config: any,
      targetUrl: string,
      secret: string
    ): boolean {
      if (!config || !Array.isArray(config.webhooks) || config.webhooks.length === 0) {
        return false;
      }
      if (!secret || !secret.trim()) {
        return false;
      }
      return config.webhooks.some((w: any) => {
        const urlMatch =
          typeof w.url === "string" && w.url.trim() === targetUrl.trim();
        const eventsMatch =
          Array.isArray(w.events) && w.events.includes("message");
        const hmacKey =
          w.hmac && typeof w.hmac.key === "string" ? w.hmac.key.trim() : "";
        const hmacMatch = hmacKey === secret.trim();
        return urlMatch && eventsMatch && hmacMatch;
      });
    }

    interface ReconciliationResult {
      ok: boolean;
      status: number;
      error?: string;
    }

    async function reconcileSessionConfig(
      baseUrl: string,
      targetSession: string,
      headers: Record<string, string>,
      url: string,
      secret: string
    ): Promise<ReconciliationResult> {
      try {
        const putRes = await fetchWithTimeout(
          `${baseUrl}/api/sessions/${encodeURIComponent(targetSession)}`,
          {
            method: "PUT",
            headers,
            body: JSON.stringify({
              name: targetSession,
              config: buildSessionConfig(url, secret),
            }),
          },
          10000
        );
        let errorMsg: string | undefined = undefined;
        if (!putRes.ok) {
          const errBody = await readResponseBody(putRes);
          errorMsg = typeof errBody === "object" ? JSON.stringify(errBody) : String(errBody);
          console.error(`WAHA session config reconciliation failed (${putRes.status}):`, errorMsg);
          return {
            ok: false,
            status: putRes.status,
            error: errorMsg,
          };
        }

        // Post-PUT verification: ensure WAHA actually saved and returned the matching webhook config
        const verifyRes = await fetchWithTimeout(
          `${baseUrl}/api/sessions/${encodeURIComponent(targetSession)}`,
          { method: "GET", headers },
          8000
        );

        if (!verifyRes.ok) {
          const verifyErrBody = await readResponseBody(verifyRes);
          const verifyErrStr = typeof verifyErrBody === "object" ? JSON.stringify(verifyErrBody) : String(verifyErrBody);
          console.error(`WAHA session verification GET failed (${verifyRes.status}):`, verifyErrStr);
          return {
            ok: false,
            status: verifyRes.status,
            error: `Failed to verify WAHA session config after PUT update (status ${verifyRes.status})`,
          };
        }

        const verifyData: any = await readResponseBody(verifyRes);
        const verified = isWebhookConfigured(verifyData?.config, url, secret);
        if (!verified) {
          console.error("WAHA session config verification failed: webhook mismatch after PUT update");
          return {
            ok: false,
            status: 502,
            error: "WAHA accepted session config update but webhook verification failed (config mismatch)",
          };
        }

        return {
          ok: true,
          status: putRes.status,
        };
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        console.warn("Failed to reconcile WAHA session config:", msg);
        return {
          ok: false,
          status: 0,
          error: msg,
        };
      }
    }

    // Helper: update teacher_whatsapp_sessions in database
    async function updateDbSession(
      status: string,
      connectedPhone: string | null = null
    ) {
      const now = new Date().toISOString();
      const payload: Record<string, any> = {
        teacher_id: teacherId,
        session_name: sessionName,
        status: normalizeStatus(status),
        last_connected_at: isWorkingStatus(status) ? now : null,
        updated_at: now,
      };
      if (connectedPhone !== null || !isWorkingStatus(status)) {
        payload.connected_phone = connectedPhone;
      }
      await dbClient.from("teacher_whatsapp_sessions").upsert(
        payload,
        { onConflict: "teacher_id" }
      );
    }

    // Helper: get existing connected_phone from DB as fallback
    async function getDbConnectedPhone(): Promise<string | null> {
      try {
        const { data } = await dbClient
          .from("teacher_whatsapp_sessions")
          .select("connected_phone")
          .eq("teacher_id", teacherId)
          .maybeSingle();
        return data?.connected_phone || null;
      } catch {
        return null;
      }
    }

    // =========================================================================
    // ACTION: TEST_WAHA_CONNECTION
    // =========================================================================
    if (action === "TEST_WAHA_CONNECTION") {
      const startTime = Date.now();
      let serverReachable = false;
      let sessionExists = false;
      let sessionStatus = "NOT_FOUND";
      let connectedPhone: string | null = null;
      let latencyMs = 0;
      let wahaHttpStatus: number | null = null;
      let webhookConfigured = false;

      // 1. Probe session status (Read-Only)
      try {
        const sessionRes = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}`,
          { method: "GET", headers: wahaHeaders },
          8000
        );

        latencyMs = Date.now() - startTime;
        serverReachable = true;
        wahaHttpStatus = sessionRes.status;

        if (sessionRes.ok) {
          sessionExists = true;
          const data: any = await readResponseBody(sessionRes);
          const rawStatus = data?.status || "STOPPED";
          sessionStatus = normalizeStatus(rawStatus);

          // Probe webhook configuration (read-only, no PUT)
          if (wahaWebhookSecret) {
            webhookConfigured = isWebhookConfigured(
              data?.config,
              webhookUrl,
              wahaWebhookSecret
            );
          }

          connectedPhone = extractPhoneFromMePayload(data?.me);
          if (!connectedPhone && isWorkingStatus(sessionStatus)) {
            connectedPhone = await fetchWahaMePhone(wahaBaseUrl, sessionName, wahaHeaders);
          }
          if (!connectedPhone && isWorkingStatus(sessionStatus)) {
            connectedPhone = await getDbConnectedPhone();
          }
        } else if (sessionRes.status === 404) {
          sessionExists = false;
          sessionStatus = "NOT_FOUND";
        } else if (sessionRes.status === 401 || sessionRes.status === 403) {
          sessionExists = false;
          sessionStatus = "UNAUTHORIZED";
        }
      } catch (_err) {
        // If session call timed out or failed, test server root ping
        try {
          const pingRes = await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions`,
            { method: "GET", headers: wahaHeaders },
            5000
          );
          serverReachable = pingRes.ok || pingRes.status === 401;
          wahaHttpStatus = pingRes.status;
        } catch {
          serverReachable = false;
          sessionStatus = "NETWORK_ERROR";
        }
      }

      const isConnected = isWorkingStatus(sessionStatus);
      let message = "";
      if (!serverReachable) {
        message = "Could not reach WAHA server.";
      } else if (!sessionExists) {
        message =
          "WAHA server is reachable, but this teacher session does not exist yet.";
      } else if (isConnected) {
        message = webhookConfigured
          ? "WAHA session is connected and working with webhooks configured."
          : "WAHA session is connected and working.";
      } else {
        message = `WAHA session exists with status: ${sessionStatus}.`;
      }

      return jsonResponse({
        success: isConnected,
        reachable: serverReachable,
        session_exists: sessionExists,
        status: wahaHttpStatus ?? 0,
        session_name: sessionName,
        session_status: sessionStatus,
        whatsapp_connected: isConnected,
        webhook_configured: webhookConfigured,
        connected_phone: connectedPhone,
        latency_ms: latencyMs,
        message,
      });
    }

    // =========================================================================
    // ACTION: START
    // =========================================================================
    if (action === "START") {
      if (!wahaWebhookSecret || !wahaWebhookSecret.trim()) {
        return jsonResponse(
          {
            success: false,
            message: "Server configuration error: WAHA_WEBHOOK_SECRET is not configured",
          },
          500
        );
      }

      let sessionStatus = "STOPPED";
      let connectedPhone: string | null = null;
      let sessionExists = false;

      // Step A: Check existing session in WAHA
      try {
        const getSessionRes = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}`,
          { method: "GET", headers: wahaHeaders }
        );

        if (getSessionRes.status === 404) {
          // Session does not exist -> create it with webhook config
          const createRes = await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions`,
            {
              method: "POST",
              headers: wahaHeaders,
              body: JSON.stringify({
                name: sessionName,
                start: false,
                config: buildSessionConfig(webhookUrl, wahaWebhookSecret),
              }),
            }
          );

          if (!createRes.ok && createRes.status !== 409) {
            const errBody = await readResponseBody(createRes);
            const errStr = typeof errBody === "object" ? JSON.stringify(errBody) : String(errBody);
            console.error("WAHA session create failed status:", createRes.status, errStr);
            return jsonResponse(
              {
                success: false,
                reachable: true,
                session_exists: false,
                status: "FAILED",
                message: `Could not create WAHA session (status ${createRes.status})`,
              },
              502
            );
          }

          if (createRes.status === 409) {
            // Session exists from a concurrent call -> GET session and verify/reconcile webhook config
            const conflictGetRes = await fetchWithTimeout(
              `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}`,
              { method: "GET", headers: wahaHeaders },
              8000
            );

            if (!conflictGetRes.ok) {
              return jsonResponse(
                {
                  success: false,
                  reachable: true,
                  session_exists: false,
                  status: "FAILED",
                  message: `Could not read WAHA session after 409 conflict (status ${conflictGetRes.status})`,
                },
                502
              );
            }

            sessionExists = true;
            const conflictData: any = await readResponseBody(conflictGetRes);
            sessionStatus = normalizeStatus(conflictData?.status);

            if (!isWebhookConfigured(conflictData?.config, webhookUrl, wahaWebhookSecret)) {
              const recResult = await reconcileSessionConfig(
                wahaBaseUrl,
                sessionName,
                wahaHeaders,
                webhookUrl,
                wahaWebhookSecret
              );
              if (!recResult.ok) {
                return jsonResponse(
                  {
                    success: false,
                    reachable: true,
                    session_exists: true,
                    status: "FAILED",
                    message: `Failed to configure required webhooks on WAHA session (status ${recResult.status})${recResult.error ? `: ${recResult.error}` : ""}`,
                  },
                  502
                );
              }
            }

            connectedPhone = extractPhoneFromMePayload(conflictData?.me);
            if (!connectedPhone && isWorkingStatus(sessionStatus)) {
              connectedPhone = await fetchWahaMePhone(wahaBaseUrl, sessionName, wahaHeaders);
            }
            if (!connectedPhone && isWorkingStatus(sessionStatus)) {
              connectedPhone = await getDbConnectedPhone();
            }
          } else {
            sessionExists = true;
            sessionStatus = "STOPPED";
          }
        } else if (getSessionRes.ok) {
          sessionExists = true;
          const data: any = await readResponseBody(getSessionRes);
          sessionStatus = normalizeStatus(data?.status);

          // Webhook reconciliation
          if (!isWebhookConfigured(data?.config, webhookUrl, wahaWebhookSecret)) {
            const recResult = await reconcileSessionConfig(
              wahaBaseUrl,
              sessionName,
              wahaHeaders,
              webhookUrl,
              wahaWebhookSecret
            );
            if (!recResult.ok) {
              return jsonResponse(
                {
                  success: false,
                  reachable: true,
                  session_exists: true,
                  status: "FAILED",
                  message: `Failed to configure required webhooks on WAHA session (status ${recResult.status})${recResult.error ? `: ${recResult.error}` : ""}`,
                },
                502
              );
            }
          }

          connectedPhone = extractPhoneFromMePayload(data?.me);
          if (!connectedPhone && isWorkingStatus(sessionStatus)) {
            connectedPhone = await fetchWahaMePhone(wahaBaseUrl, sessionName, wahaHeaders);
          }
          if (!connectedPhone && isWorkingStatus(sessionStatus)) {
            connectedPhone = await getDbConnectedPhone();
          }
        }
      } catch (err) {
        console.error("WAHA lookup error:", err);
        return jsonResponse(
          {
            success: false,
            reachable: false,
            message: "Could not connect to WAHA server",
          },
          502
        );
      }

      // Step B: If already working/connected
      if (isWorkingStatus(sessionStatus)) {
        await updateDbSession(sessionStatus, connectedPhone);
        return jsonResponse({
          success: true,
          reachable: true,
          session_exists: true,
          status: sessionStatus,
          session_status: sessionStatus,
          session_name: sessionName,
          connected_phone: connectedPhone,
          qr: null,
          message: "WhatsApp session is already connected",
        });
      }

      // Step C: Start session if stopped or not running
      if (sessionStatus !== "SCAN_QR_CODE" && sessionStatus !== "STARTING") {
        try {
          await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}/start`,
            { method: "POST", headers: wahaHeaders },
            12000
          );
        } catch (err) {
          console.warn("WAHA start request notice:", err);
        }
      }

      // Step D: Attempt to fetch QR code
      let qrData: any = null;
      try {
        const qrRes = await fetchWithTimeout(
          `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/auth/qr`,
          {
            method: "GET",
            headers: { ...wahaHeaders, Accept: "application/json, image/png" },
          },
          8000
        );

        if (qrRes.ok) {
          const contentType =
            qrRes.headers.get("content-type")?.toLowerCase() || "";
          if (contentType.includes("application/json")) {
            const parsed: any = await qrRes.json();
            const qrString = parsed?.data || parsed?.qr || parsed?.value || "";
            if (qrString) {
              qrData = {
                mimetype: parsed?.mimetype || "image/png",
                data: qrString,
              };
            }
          } else if (contentType.includes("image/")) {
            const buffer = await qrRes.arrayBuffer();
            const bytes = new Uint8Array(buffer);
            let binary = "";
            const chunkSize = 8192;
            for (let i = 0; i < bytes.length; i += chunkSize) {
              binary += String.fromCharCode(
                ...bytes.subarray(i, i + chunkSize)
              );
            }
            qrData = {
              mimetype: contentType,
              data: btoa(binary),
            };
          }
        }
      } catch (err) {
        console.warn("WAHA QR fetch notice (may still be generating):", err);
      }

      // Step E: Save SCAN_QR_CODE state to database
      await updateDbSession("SCAN_QR_CODE", null);

      // Return success with status SCAN_QR_CODE (even if QR is still generating in background)
      return jsonResponse({
        success: true,
        reachable: true,
        session_exists: true,
        status: "SCAN_QR_CODE",
        session_status: "SCAN_QR_CODE",
        session_name: sessionName,
        qr: qrData,
        message: qrData
          ? "Scan the QR code with WhatsApp"
          : "WAHA session started. QR code is being generated, please wait...",
      });
    }

    // =========================================================================
    // ACTION: REQUEST_PAIRING_CODE
    // =========================================================================
    if (action === "REQUEST_PAIRING_CODE") {
      if (!wahaWebhookSecret || !wahaWebhookSecret.trim()) {
        return jsonResponse(
          {
            success: false,
            message: "Server configuration error: WAHA_WEBHOOK_SECRET is not configured",
          },
          500
        );
      }

      const rawPhone = body.phoneNumber || "";
      const normalizedPhone = normalizeEgyptianPhone(rawPhone);

      if (!normalizedPhone) {
        return jsonResponse(
          { success: false, message: "Invalid phone number provided" },
          400
        );
      }

      // Ensure session exists with webhook config
      try {
        const getSessionRes = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}`,
          { method: "GET", headers: wahaHeaders },
          5000
        );

        if (getSessionRes.status === 404) {
          const createRes = await fetchWithTimeout(`${wahaBaseUrl}/api/sessions`, {
            method: "POST",
            headers: wahaHeaders,
            body: JSON.stringify({
              name: sessionName,
              start: false,
              config: buildSessionConfig(webhookUrl, wahaWebhookSecret),
            }),
          });
          if (!createRes.ok && createRes.status !== 409) {
            const errBody = await readResponseBody(createRes);
            const errStr = typeof errBody === "object" ? JSON.stringify(errBody) : String(errBody);
            console.error("WAHA session create failed status in pairing code:", createRes.status, errStr);
            return jsonResponse(
              {
                success: false,
                message: `Could not create WAHA session with webhook configuration (status ${createRes.status})`,
              },
              502
            );
          }

          if (createRes.status === 409) {
            const conflictGetRes = await fetchWithTimeout(
              `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}`,
              { method: "GET", headers: wahaHeaders },
              5000
            );

            if (!conflictGetRes.ok) {
              return jsonResponse(
                {
                  success: false,
                  message: `Could not verify WAHA session after conflict in pairing code (status ${conflictGetRes.status})`,
                },
                502
              );
            }

            const conflictData: any = await readResponseBody(conflictGetRes);
            if (!isWebhookConfigured(conflictData?.config, webhookUrl, wahaWebhookSecret)) {
              const recResult = await reconcileSessionConfig(
                wahaBaseUrl,
                sessionName,
                wahaHeaders,
                webhookUrl,
                wahaWebhookSecret
              );
              if (!recResult.ok) {
                return jsonResponse(
                  {
                    success: false,
                    message: `Failed to configure required webhooks on WAHA session (status ${recResult.status})${recResult.error ? `: ${recResult.error}` : ""}`,
                  },
                  502
                );
              }
            }
          }
        } else if (getSessionRes.ok) {
          const sessionData: any = await readResponseBody(getSessionRes);
          if (!isWebhookConfigured(sessionData?.config, webhookUrl, wahaWebhookSecret)) {
            const recResult = await reconcileSessionConfig(
              wahaBaseUrl,
              sessionName,
              wahaHeaders,
              webhookUrl,
              wahaWebhookSecret
            );
            if (!recResult.ok) {
              return jsonResponse(
                {
                  success: false,
                  message: `Failed to configure required webhooks on WAHA session (status ${recResult.status})${recResult.error ? `: ${recResult.error}` : ""}`,
                },
                502
              );
            }
          }
        }
      } catch (err) {
        console.warn("Notice checking WAHA session in REQUEST_PAIRING_CODE:", err instanceof Error ? err.message : String(err));
        return jsonResponse(
          {
            success: false,
            message: "Could not verify or configure WAHA session webhook",
          },
          502
        );
      }

      // Ensure session is started
      try {
        await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}/start`,
          { method: "POST", headers: wahaHeaders },
          8000
        );
      } catch (_) {}

      // Request pairing code from WAHA with endpoint fallback
      let pairingRes: Response | null = null;
      try {
        pairingRes = await fetchWithTimeout(
          `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/auth/request-code`,
          {
            method: "POST",
            headers: wahaHeaders,
            body: JSON.stringify({ phoneNumber: normalizedPhone }),
          },
          15000
        );

        if (pairingRes.status === 404) {
          // Fallback to legacy endpoint
          pairingRes = await fetchWithTimeout(
            `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/request-code`,
            {
              method: "POST",
              headers: wahaHeaders,
              body: JSON.stringify({ phoneNumber: normalizedPhone }),
            },
            15000
          );
        }
      } catch (err) {
        return jsonResponse(
          {
            success: false,
            message:
              "Connection timed out while requesting pairing code from WAHA",
          },
          504
        );
      }

      if (!pairingRes || !pairingRes.ok) {
        const errData = pairingRes ? await readResponseBody(pairingRes) : null;
        return jsonResponse(
          {
            success: false,
            message: "WAHA rejected pairing code request",
            waha_response: errData,
          },
          pairingRes ? pairingRes.status : 502
        );
      }

      const pairingData: any = await readResponseBody(pairingRes);

      // Normalize pairing code extraction across WAHA versions
      let code: string | null = null;
      if (typeof pairingData?.code === "string") {
        code = pairingData.code.trim();
      } else if (typeof pairingData?.data?.code === "string") {
        code = pairingData.data.code.trim();
      } else if (typeof pairingData?.result?.code === "string") {
        code = pairingData.result.code.trim();
      } else if (typeof pairingData?.pairingCode === "string") {
        code = pairingData.pairingCode.trim();
      }

      if (!code) {
        return jsonResponse(
          {
            success: false,
            message: "Pairing code was not found in WAHA response",
            waha_response: pairingData,
          },
          500
        );
      }

      await updateDbSession("SCAN_QR_CODE", null);

      return jsonResponse({
        success: true,
        code,
        status: "SCAN_QR_CODE",
        session_status: "SCAN_QR_CODE",
        session_name: sessionName,
        message: "Pairing code generated successfully",
      });
    }

    // =========================================================================
    // ACTION: LOGOUT
    // =========================================================================
    if (action === "LOGOUT") {
      try {
        await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${encodeURIComponent(sessionName)}/logout`,
          { method: "POST", headers: wahaHeaders },
          12000
        );
      } catch (err) {
        console.warn("WAHA logout request error:", err);
      }

      await updateDbSession("DISCONNECTED", null);

      return jsonResponse({
        success: true,
        status: "DISCONNECTED",
        session_status: "DISCONNECTED",
        session_name: sessionName,
        message: "WhatsApp session disconnected",
      });
    }

    return jsonResponse(
      { success: false, message: `Action not handled: ${action}` },
      400
    );
  } catch (error) {
    console.error("waha-session unhandled error:", error);
    return jsonResponse(
      {
        success: false,
        message: error instanceof Error ? error.message : "Internal error",
      },
      500
    );
  }
});

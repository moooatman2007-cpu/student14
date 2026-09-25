import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

interface RequestBody {
  action?: string;
  phoneNumber?: string;
}

type WahaSessionStatus =
  | "STOPPED"
  | "STARTING"
  | "SCAN_QR_CODE"
  | "WORKING"
  | "CONNECTED"
  | "FAILED"
  | string;

const REQUEST_TIMEOUT_MS = 10000;

function jsonResponse(
  body: Record<string, unknown>,
  status = 200,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: corsHeaders,
  });
}

function safeErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
      .replace(/X-Api-Key\s*[:=]\s*\S+/gi, "X-Api-Key: [REDACTED]")
      .replace(/Authorization\s*[:=]\s*\S+/gi, "Authorization: [REDACTED]")
      .replace(/Bearer\s+\S+/gi, "Bearer [REDACTED]");
  }

  return "Internal Error";
}

async function fetchWithTimeout(
  url: string,
  options: RequestInit,
  timeoutMs = REQUEST_TIMEOUT_MS,
): Promise<Response> {
  const controller = new AbortController();

  const timeout = setTimeout(() => {
    controller.abort();
  }, timeoutMs);

  try {
    return await fetch(url, {
      ...options,
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timeout);
  }
}

function normalizePhoneNumber(phoneNumber: string): string {
  return phoneNumber.replace(/[^\d]/g, "");
}

Deno.serve(async (req: Request) => {
  // --------------------------------------------------------------------------
  // CORS
  // --------------------------------------------------------------------------

  if (req.method === "OPTIONS") {
    return new Response("ok", {
      status: 200,
      headers: corsHeaders,
    });
  }

  if (req.method !== "POST") {
    return jsonResponse(
      {
        success: false,
        message: "Method not allowed",
      },
      405,
    );
  }

  try {
    // ------------------------------------------------------------------------
    // 1. Validate Authentication
    // ------------------------------------------------------------------------

    const authHeader = req.headers.get("Authorization");

    if (!authHeader) {
      return jsonResponse(
        {
          success: false,
          message: "Authorization header missing",
        },
        401,
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";
    const supabaseServiceRoleKey =
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

    if (!supabaseUrl || !supabaseAnonKey) {
      console.error("Supabase authentication environment is incomplete.");

      return jsonResponse(
        {
          success: false,
          message: "تعذر الاتصال بخدمة الحساب",
        },
        500,
      );
    }

    if (!supabaseServiceRoleKey) {
      console.error("SUPABASE_SERVICE_ROLE_KEY is missing.");

      return jsonResponse(
        {
          success: false,
          message: "تعذر تهيئة خدمة واتساب",
        },
        500,
      );
    }

    const authClient = createClient(
      supabaseUrl,
      supabaseAnonKey,
      {
        global: {
          headers: {
            Authorization: authHeader,
          },
        },
      },
    );

    const {
      data: { user },
      error: authError,
    } = await authClient.auth.getUser();

    if (authError || !user) {
      return jsonResponse(
        {
          success: false,
          message: "Invalid or expired authorization token",
        },
        401,
      );
    }

    // ------------------------------------------------------------------------
    // 2. Server-side DB client
    // ------------------------------------------------------------------------

    const dbClient = createClient(
      supabaseUrl,
      supabaseServiceRoleKey,
    );

    // ------------------------------------------------------------------------
    // 3. Parse Request Body
    // ------------------------------------------------------------------------

    let body: RequestBody = {};

    try {
      body = await req.json();
    } catch {
      body = {};
    }

    const action = body.action;

    const supportedActions = new Set([
      "START",
      "LOGOUT",
      "TEST_WAHA_CONNECTION",
      "REQUEST_PAIRING_CODE",
    ]);

    if (!action || !supportedActions.has(action)) {
      return jsonResponse(
        {
          success: false,
          message: "Invalid or unsupported action",
        },
        400,
      );
    }

    // ------------------------------------------------------------------------
    // 4. Derive tenant identity strictly from authenticated user
    // ------------------------------------------------------------------------

    const teacherId = user.id;
    const sessionName = `teacher_${teacherId}`;

    // ------------------------------------------------------------------------
    // 5. WAHA configuration
    // ------------------------------------------------------------------------

    const wahaBaseUrl = (Deno.env.get("WAHA_BASE_URL") || "")
      .replace(/\/+$/, "");

    const wahaApiKey = Deno.env.get("WAHA_API_KEY") || "";

    if (!wahaBaseUrl) {
      console.error("WAHA_BASE_URL is missing.");

      return jsonResponse(
        {
          success: false,
          message: "تعذر الاتصال بخدمة واتساب",
        },
        500,
      );
    }

    const wahaHeaders: Record<string, string> = {
      "Content-Type": "application/json",
    };

    if (wahaApiKey) {
      wahaHeaders["X-Api-Key"] = wahaApiKey;
    }

    // =========================================================================
    // ACTION: TEST_WAHA_CONNECTION
    // =========================================================================

    if (action === "TEST_WAHA_CONNECTION") {
      const startedAt = Date.now();

      try {
        const response = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions`,
          {
            method: "GET",
            headers: wahaHeaders,
          },
        );

        const latencyMs = Date.now() - startedAt;

        let responseBody: unknown = null;

        try {
          responseBody = await response.json();
        } catch {
          responseBody = await response.text();
        }

        return jsonResponse(
          {
            success: response.ok,
            reachable: true,
            status: response.status,
            latency_ms: latencyMs,
            sessions: response.ok ? responseBody : undefined,
          },
          200,
        );
      } catch (error) {
        const latencyMs = Date.now() - startedAt;

        console.error(
          "WAHA connection test failed:",
          safeErrorMessage(error),
        );

        return jsonResponse(
          {
            success: false,
            reachable: false,
            status: 0,
            latency_ms: latencyMs,
            error: "WAHA connection failed",
          },
          200,
        );
      }
    }

    // =========================================================================
    // ACTION: START
    // =========================================================================

    if (action === "START") {
      let sessionStatus: WahaSessionStatus = "STOPPED";
      let connectedPhone: string | null = null;

      // ----------------------------------------------------------------------
      // A. Get existing session
      // ----------------------------------------------------------------------

      try {
        const getSessionResponse = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${sessionName}`,
          {
            method: "GET",
            headers: wahaHeaders,
          },
        );

        if (getSessionResponse.status === 404) {
          // ---------------------------------------------------------------
          // Session doesn't exist → create it
          // ---------------------------------------------------------------

          const createResponse = await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions`,
            {
              method: "POST",
              headers: wahaHeaders,
              body: JSON.stringify({
                name: sessionName,
              }),
            },
          );

          if (
            !createResponse.ok &&
            createResponse.status !== 409
          ) {
            console.error(
              "WAHA session creation failed:",
              createResponse.status,
            );

            return jsonResponse(
              {
                success: false,
                message: "تعذر إنشاء جلسة واتساب",
              },
              502,
            );
          }

          sessionStatus = "STOPPED";
        } else if (getSessionResponse.ok) {
          const sessionData = await getSessionResponse.json();

          sessionStatus =
            sessionData?.status || "STOPPED";

          const me = sessionData?.me;

          if (typeof me === "string") {
            connectedPhone = me
              .split("@")[0]
              .replace(/\D/g, "");
          } else if (
            me &&
            typeof me.id === "string"
          ) {
            connectedPhone = me.id
              .split("@")[0]
              .replace(/\D/g, "");
          }
        } else {
          console.error(
            "WAHA session lookup failed:",
            getSessionResponse.status,
          );

          return jsonResponse(
            {
              success: false,
              message: "تعذر قراءة حالة واتساب",
            },
            502,
          );
        }
      } catch (error) {
        console.error(
          "WAHA session operation failed:",
          safeErrorMessage(error),
        );

        return jsonResponse(
          {
            success: false,
            message: "تعذر بدء ربط واتساب",
          },
          502,
        );
      }

      // ----------------------------------------------------------------------
      // B. Already connected
      // ----------------------------------------------------------------------

      if (
        sessionStatus === "WORKING" ||
        sessionStatus === "CONNECTED"
      ) {
        const now = new Date().toISOString();

        const { error: dbError } = await dbClient
          .from("teacher_whatsapp_sessions")
          .upsert(
            {
              teacher_id: teacherId,
              session_name: sessionName,
              status: "CONNECTED",
              connected_phone: connectedPhone || null,
              last_connected_at: now,
              updated_at: now,
            },
            {
              onConflict: "teacher_id",
            },
          );

        if (dbError) {
          console.error(
            "Failed to update WhatsApp session:",
            dbError.message,
          );

          return jsonResponse(
            {
              success: false,
              message: "تعذر حفظ حالة واتساب",
            },
            500,
          );
        }

        return jsonResponse({
          success: true,
          status: "CONNECTED",
          session_name: sessionName,
          connected_phone: connectedPhone || "",
          qr: null,
        });
      }

      // ----------------------------------------------------------------------
      // C. Start only when necessary
      // ----------------------------------------------------------------------

      if (
        sessionStatus === "STOPPED" ||
        sessionStatus === "FAILED"
      ) {
        try {
          const startResponse = await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions/${sessionName}/start`,
            {
              method: "POST",
              headers: wahaHeaders,
            },
          );

          if (
            !startResponse.ok &&
            startResponse.status !== 409
          ) {
            console.error(
              "WAHA start failed:",
              startResponse.status,
            );

            return jsonResponse(
              {
                success: false,
                message: "تعذر تشغيل جلسة واتساب",
              },
              502,
            );
          }
        } catch (error) {
          console.error(
            "WAHA start exception:",
            safeErrorMessage(error),
          );

          return jsonResponse(
            {
              success: false,
              message: "تعذر تشغيل جلسة واتساب",
            },
            502,
          );
        }
      }

      // ----------------------------------------------------------------------
      // D. Read current session status after start
      // ----------------------------------------------------------------------

      try {
        const statusResponse = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${sessionName}`,
          {
            method: "GET",
            headers: wahaHeaders,
          },
        );

        if (statusResponse.ok) {
          const sessionData = await statusResponse.json();

          sessionStatus =
            sessionData?.status || "STARTING";

          const me = sessionData?.me;

          if (typeof me === "string") {
            connectedPhone = me
              .split("@")[0]
              .replace(/\D/g, "");
          } else if (
            me &&
            typeof me.id === "string"
          ) {
            connectedPhone = me.id
              .split("@")[0]
              .replace(/\D/g, "");
          }
        }
      } catch (error) {
        console.error(
          "WAHA status refresh failed:",
          safeErrorMessage(error),
        );
      }

      // ----------------------------------------------------------------------
      // E. Already became WORKING
      // ----------------------------------------------------------------------

      if (
        sessionStatus === "WORKING" ||
        sessionStatus === "CONNECTED"
      ) {
        const now = new Date().toISOString();

        const { error: dbError } = await dbClient
          .from("teacher_whatsapp_sessions")
          .upsert(
            {
              teacher_id: teacherId,
              session_name: sessionName,
              status: "CONNECTED",
              connected_phone: connectedPhone || null,
              last_connected_at: now,
              updated_at: now,
            },
            {
              onConflict: "teacher_id",
            },
          );

        if (dbError) {
          console.error(
            "Failed to save connected state:",
            dbError.message,
          );

          return jsonResponse(
            {
              success: false,
              message: "تعذر حفظ حالة واتساب",
            },
            500,
          );
        }

        return jsonResponse({
          success: true,
          status: "CONNECTED",
          session_name: sessionName,
          connected_phone: connectedPhone || "",
          qr: null,
        });
      }

      // ----------------------------------------------------------------------
      // F. Fetch QR when session requires pairing
      // ----------------------------------------------------------------------

      let qrData: {
        mimetype: string;
        data: string;
      } | null = null;

      if (
        sessionStatus === "SCAN_QR_CODE" ||
        sessionStatus === "STARTING"
      ) {
        try {
          const qrResponse = await fetchWithTimeout(
            `${wahaBaseUrl}/api/${sessionName}/auth/qr`,
            {
              method: "GET",
              headers: {
                ...wahaHeaders,
                Accept: "application/json",
              },
            },
          );

          if (qrResponse.ok) {
            const qrJson = await qrResponse.json();

            if (
              qrJson &&
              typeof qrJson.data === "string" &&
              qrJson.data.length > 0
            ) {
              qrData = {
                mimetype:
                  typeof qrJson.mimetype === "string"
                    ? qrJson.mimetype
                    : "image/png",
                data: qrJson.data,
              };
            }
          }
        } catch (error) {
          console.error(
            "WAHA QR fetch failed:",
            safeErrorMessage(error),
          );
        }
      }

      // ----------------------------------------------------------------------
      // G. Save session state
      // ----------------------------------------------------------------------

      const dbStatus =
        sessionStatus === "FAILED"
          ? "FAILED"
          : "SCAN_QR_CODE";

      const { error: dbError } = await dbClient
        .from("teacher_whatsapp_sessions")
        .upsert(
          {
            teacher_id: teacherId,
            session_name: sessionName,
            status: dbStatus,
            connected_phone: null,
            updated_at: new Date().toISOString(),
          },
          {
            onConflict: "teacher_id",
          },
        );

      if (dbError) {
        console.error(
          "Failed to save WhatsApp pairing state:",
          dbError.message,
        );

        return jsonResponse(
          {
            success: false,
            message: "تعذر حفظ حالة ربط واتساب",
          },
          500,
        );
      }

      return jsonResponse({
        success: true,
        status: dbStatus,
        session_name: sessionName,
        connected_phone: "",
        qr: qrData,
      });
    }

    // =========================================================================
    // ACTION: REQUEST_PAIRING_CODE
    // =========================================================================

    if (action === "REQUEST_PAIRING_CODE") {
      const rawPhoneNumber = body.phoneNumber;

      if (
        !rawPhoneNumber ||
        typeof rawPhoneNumber !== "string"
      ) {
        return jsonResponse(
          {
            success: false,
            message: "رقم الهاتف غير صالح",
          },
          400,
        );
      }

      const phoneNumber =
        normalizePhoneNumber(rawPhoneNumber);

      if (
        phoneNumber.length < 10 ||
        phoneNumber.length > 15
      ) {
        return jsonResponse(
          {
            success: false,
            message: "رقم الهاتف غير صالح",
          },
          400,
        );
      }

      // ----------------------------------------------------------------------
      // A. Ensure session exists
      // ----------------------------------------------------------------------

      let sessionStatus: WahaSessionStatus = "STOPPED";

      try {
        const getSessionResponse = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${sessionName}`,
          {
            method: "GET",
            headers: wahaHeaders,
          },
        );

        if (getSessionResponse.status === 404) {
          const createResponse = await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions`,
            {
              method: "POST",
              headers: wahaHeaders,
              body: JSON.stringify({
                name: sessionName,
              }),
            },
          );

          if (
            !createResponse.ok &&
            createResponse.status !== 409
          ) {
            console.error(
              "WAHA pairing session creation failed:",
              createResponse.status,
            );

            return jsonResponse(
              {
                success: false,
                message: "تعذر إنشاء جلسة واتساب",
              },
              502,
            );
          }

          sessionStatus = "STOPPED";
        } else if (getSessionResponse.ok) {
          const sessionData =
            await getSessionResponse.json();

          sessionStatus =
            sessionData?.status || "STOPPED";
        } else {
          return jsonResponse(
            {
              success: false,
              message: "تعذر قراءة حالة جلسة واتساب",
            },
            502,
          );
        }
      } catch (error) {
        console.error(
          "Pairing session lookup failed:",
          safeErrorMessage(error),
        );

        return jsonResponse(
          {
            success: false,
            message: "تعذر الاتصال بخدمة واتساب",
          },
          502,
        );
      }

      // ----------------------------------------------------------------------
      // B. Start only if session is stopped/failed
      // ----------------------------------------------------------------------

      if (
        sessionStatus === "STOPPED" ||
        sessionStatus === "FAILED"
      ) {
        try {
          const startResponse = await fetchWithTimeout(
            `${wahaBaseUrl}/api/sessions/${sessionName}/start`,
            {
              method: "POST",
              headers: wahaHeaders,
            },
          );

          if (
            !startResponse.ok &&
            startResponse.status !== 409
          ) {
            console.error(
              "WAHA pairing start failed:",
              startResponse.status,
            );

            return jsonResponse(
              {
                success: false,
                message: "تعذر تشغيل جلسة واتساب",
              },
              502,
            );
          }
        } catch (error) {
          console.error(
            "WAHA pairing start exception:",
            safeErrorMessage(error),
          );

          return jsonResponse(
            {
              success: false,
              message: "تعذر تشغيل جلسة واتساب",
            },
            502,
          );
        }
      }

      // ----------------------------------------------------------------------
      // C. Request pairing code
      // ----------------------------------------------------------------------

      try {
        const pairingResponse = await fetchWithTimeout(
          `${wahaBaseUrl}/api/${sessionName}/auth/request-code`,
          {
            method: "POST",
            headers: wahaHeaders,
            body: JSON.stringify({
              phoneNumber,
            }),
          },
        );

        let pairingData: any = null;

        try {
          pairingData = await pairingResponse.json();
        } catch {
          pairingData = null;
        }

        if (!pairingResponse.ok) {
          console.error(
            "WAHA pairing code request failed:",
            pairingResponse.status,
            pairingData,
          );

          return jsonResponse(
            {
              success: false,
              message:
                "فشل طلب كود الربط من خادم واتساب",
              waha_status: pairingResponse.status,
            },
            502,
          );
        }

        const code =
          typeof pairingData?.code === "string"
            ? pairingData.code.trim()
            : "";

        if (!code) {
          return jsonResponse(
            {
              success: false,
              message:
                "تعذر الحصول على كود الربط من الخادم",
            },
            502,
          );
        }

        return jsonResponse({
          success: true,
          code,
        });
      } catch (error) {
        console.error(
          "WAHA pairing code exception:",
          safeErrorMessage(error),
        );

        return jsonResponse(
          {
            success: false,
            message:
              "حدث خطأ أثناء طلب كود الربط",
          },
          502,
        );
      }
    }

    // =========================================================================
    // ACTION: LOGOUT
    // =========================================================================

    if (action === "LOGOUT") {
      try {
        const logoutResponse = await fetchWithTimeout(
          `${wahaBaseUrl}/api/sessions/${sessionName}/logout`,
          {
            method: "POST",
            headers: wahaHeaders,
          },
        );

        // 404 means there is no active WAHA session.
        // We still clean our own database state.
        if (
          !logoutResponse.ok &&
          logoutResponse.status !== 404
        ) {
          console.error(
            "WAHA logout failed:",
            logoutResponse.status,
          );
        }
      } catch (error) {
        console.error(
          "WAHA logout exception:",
          safeErrorMessage(error),
        );
      }

      const { error: dbError } = await dbClient
        .from("teacher_whatsapp_sessions")
        .upsert(
          {
            teacher_id: teacherId,
            session_name: sessionName,
            status: "DISCONNECTED",
            connected_phone: null,
            last_connected_at: null,
            updated_at: new Date().toISOString(),
          },
          {
            onConflict: "teacher_id",
          },
        );

      if (dbError) {
        console.error(
          "Failed to save logout state:",
          dbError.message,
        );

        return jsonResponse(
          {
            success: false,
            message: "تعذر تحديث حالة واتساب",
          },
          500,
        );
      }

      return jsonResponse({
        success: true,
        status: "DISCONNECTED",
      });
    }

    // ------------------------------------------------------------------------
    // Fallback
    // ------------------------------------------------------------------------

    return jsonResponse(
      {
        success: false,
        message: "Invalid or unsupported action",
      },
      400,
    );
  } catch (error) {
    console.error(
      "Unhandled Edge Function Error:",
      safeErrorMessage(error),
    );

    return jsonResponse(
      {
        success: false,
        message: "تعذر الاتصال بخدمة واتساب",
      },
      500,
    );
  }
});
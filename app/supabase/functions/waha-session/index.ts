import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface RequestBody {
  action?: string;
  phoneNumber?: string;
  teacher_id?: string; // Explicitly ignored for security
  session_name?: string; // Explicitly ignored for security
}

Deno.serve(async (req: Request) => {
  // 1. Handle CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    // 2. Parse body and action
    let body: RequestBody = {};
    try {
      body = await req.clone().json();
    } catch (_) {
      // Body empty or malformed
    }
    const action = body.action;
    if (action !== "START" && action !== "LOGOUT" && action !== "TEST_WAHA_CONNECTION" && action !== "REQUEST_PAIRING_CODE") {
      return new Response(
        JSON.stringify({ success: false, message: "Invalid or unsupported action" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Validate User Authentication via JWT
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return new Response(
        JSON.stringify({ success: false, message: "Authorization header missing or invalid" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

    if (!supabaseServiceRoleKey) {
      return new Response(
        JSON.stringify({ success: false, message: "Server configuration error: Service role key missing" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const authClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: { user }, error: authError } = await authClient.auth.getUser();

    if (authError || !user) {
      return new Response(
        JSON.stringify({ success: false, message: "Invalid or expired authorization token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 4. Create Server-Side DB Client using Service Role Key
    const dbClient = createClient(supabaseUrl, supabaseServiceRoleKey);

    // 5. Derive Teacher ID & Session Name strictly server-side from JWT
    const teacherId = user.id;
    const sessionName = `teacher_${teacherId}`;

    // 6. Read Environment Secrets securely
    const wahaBaseUrl = (Deno.env.get("WAHA_BASE_URL") || "").replace(/\/+$/, "");
    const wahaApiKey = Deno.env.get("WAHA_API_KEY") || "";

    if (!wahaBaseUrl) {
      return new Response(
        JSON.stringify({ success: false, message: "تعذر الاتصال بخدمة واتساب" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const wahaHeaders: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (wahaApiKey) {
      wahaHeaders["X-Api-Key"] = wahaApiKey;
    }

    // Helper for unified timeout fetch (10s)
    async function fetchWithTimeout(url: string, options: RequestInit = {}, timeoutMs = 10000): Promise<Response> {
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

    // ------------------------------------------------------------------------
    // ACTION: START
    // ------------------------------------------------------------------------
    if (action === "START") {
      let wahaSessionStatus = "STOPPED";
      let meId: string | null = null;

      try {
        const getSessionRes = await fetchWithTimeout(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
          method: "GET",
          headers: wahaHeaders,
        });

        if (getSessionRes.status === 404) {
          const createSessionRes = await fetchWithTimeout(`${wahaBaseUrl}/api/sessions`, {
            method: "POST",
            headers: wahaHeaders,
            body: JSON.stringify({ name: sessionName }),
          });

          if (!createSessionRes.ok && createSessionRes.status !== 409) {
            throw new Error(`WAHA Session creation failed status: ${createSessionRes.status}`);
          }
        } else if (getSessionRes.ok) {
          const sessionData = await getSessionRes.json();
          wahaSessionStatus = sessionData?.status || "STOPPED";
          meId = sessionData?.me?.id || sessionData?.me || null;
        }
      } catch (err) {
        console.error("WAHA Session Operation Error");
        return new Response(
          JSON.stringify({ success: false, message: "تعذر بدء ربط واتساب (انتهت المهلة أو خطأ بالاتصال)" }),
          { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Step B: If session is already WORKING / CONNECTED
      if (wahaSessionStatus === "WORKING" || wahaSessionStatus === "CONNECTED") {
        let cleanPhone = "";
        if (meId && typeof meId === "string") {
          cleanPhone = meId.split("@")[0].replace(/[^0-9]/g, "");
        }

        const { error: upsertError } = await dbClient.from("teacher_whatsapp_sessions").upsert(
          {
            teacher_id: teacherId,
            session_name: sessionName,
            status: "CONNECTED",
            connected_phone: cleanPhone || null,
            last_connected_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
          },
          { onConflict: "teacher_id" }
        );

        if (upsertError) {
          console.error("Database upsert failed");
          return new Response(
            JSON.stringify({ success: false, message: "فشل حفظ حالة الجلسة في قاعدة البيانات" }),
            { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }

        return new Response(
          JSON.stringify({
            success: true,
            status: "CONNECTED",
            session_name: sessionName,
            connected_phone: cleanPhone,
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Step C: Start session if not already running
      if (wahaSessionStatus !== "SCAN_QR_CODE" && wahaSessionStatus !== "STARTING") {
        try {
          await fetchWithTimeout(`${wahaBaseUrl}/api/sessions/${sessionName}/start`, {
            method: "POST",
            headers: wahaHeaders,
          });
        } catch (err) {
          console.error("WAHA Session Start Error");
        }
      }

      // Step D: Fetch QR Code transiently with Content-Type check (JSON or image/png)
      let qrData: any = null;
      try {
        const qrRes = await fetchWithTimeout(`${wahaBaseUrl}/api/${sessionName}/auth/qr`, {
          method: "GET",
          headers: {
            ...wahaHeaders,
            Accept: "application/json, image/png",
          },
        });

        if (qrRes.ok) {
          const contentType = qrRes.headers.get("content-type") || "";
          if (contentType.includes("application/json")) {
            const parsed = await qrRes.json();
            qrData = {
              mimetype: parsed.mimetype || "image/png",
              data: parsed.data || parsed.qr || "",
            };
          } else if (contentType.includes("image/")) {
            const arrayBuffer = await qrRes.arrayBuffer();
            const uint8Array = new Uint8Array(arrayBuffer);
            let binary = "";
            const chunkSize = 8192;
            for (let i = 0; i < uint8Array.length; i += chunkSize) {
              const chunk = uint8Array.subarray(i, i + chunkSize);
              binary += String.fromCharCode.apply(null, Array.from(chunk));
            }
            const base64 = btoa(binary);
            qrData = {
              mimetype: contentType,
              data: base64,
            };
          }
        }
      } catch (err) {
        console.error("WAHA QR Fetch Error");
      }

      const { error: upsertError } = await dbClient.from("teacher_whatsapp_sessions").upsert(
        {
          teacher_id: teacherId,
          session_name: sessionName,
          status: "SCAN_QR_CODE",
          updated_at: new Date().toISOString(),
        },
        { onConflict: "teacher_id" }
      );

      if (upsertError) {
        console.error("Database upsert failed");
        return new Response(
          JSON.stringify({ success: false, message: "فشل حفظ حالة الجلسة في قاعدة البيانات" }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({
          success: true,
          status: "SCAN_QR_CODE",
          session_name: sessionName,
          qr: qrData
            ? {
                mimetype: qrData.mimetype || "image/png",
                data: qrData.data || "",
              }
            : null,
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ------------------------------------------------------------------------
    // ACTION: TEST_WAHA_CONNECTION
    // ------------------------------------------------------------------------
    if (action === "TEST_WAHA_CONNECTION") {
      const startTime = Date.now();
      let reachable = false;
      let status: number | null = null;
      let success = false;
      let sessionStatus = "STOPPED";
      let connectedPhone: string | null = null;
      let errorMessage: string | null = null;

      try {
        const testRes = await fetchWithTimeout(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
          method: "GET",
          headers: wahaHeaders,
        });

        status = testRes.status;
        reachable = true;

        if (testRes.ok) {
          const sessionData = await testRes.json();
          sessionStatus = sessionData?.status || "STOPPED";
          const meId = sessionData?.me?.id || sessionData?.me || null;
          if (meId && typeof meId === "string") {
            connectedPhone = meId.split("@")[0].replace(/[^0-9]/g, "");
          }
          if (sessionStatus === "WORKING" || sessionStatus === "CONNECTED") {
            success = true;
          } else {
            success = false;
            errorMessage = `جلسة WhatsApp بحالة: ${sessionStatus}`;
          }
        } else if (testRes.status === 404) {
          sessionStatus = "NOT_FOUND";
          success = false;
          errorMessage = "جلسة WhatsApp غير موجودة أو لم تقم بربطها بعد";
        } else if (testRes.status === 401 || testRes.status === 403) {
          sessionStatus = "UNAUTHORIZED";
          success = false;
          errorMessage = "خطأ في مصادقة خادم واتساب";
        } else {
          sessionStatus = "FAILED";
          success = false;
          errorMessage = `استجابة خادم واتساب غير صالحة (${testRes.status})`;
        }
      } catch (err) {
        reachable = false;
        success = false;
        if (err instanceof Error && err.name === "AbortError") {
          sessionStatus = "TIMEOUT";
          errorMessage = "انتهت مهلة الاتصال بالخادم";
        } else {
          sessionStatus = "NETWORK_ERROR";
          errorMessage = "تعذر الاتصال بالخادم";
        }
      }

      const latencyMs = Date.now() - startTime;

      return new Response(
        JSON.stringify({
          success,
          reachable,
          status,
          session_name: sessionName,
          session_status: sessionStatus,
          connected_phone: connectedPhone,
          latency_ms: latencyMs,
          message: errorMessage,
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ------------------------------------------------------------------------
    // ACTION: REQUEST_PAIRING_CODE
    // ------------------------------------------------------------------------
    if (action === "REQUEST_PAIRING_CODE") {
      const phoneNumber = body.phoneNumber;
      if (!phoneNumber || typeof phoneNumber !== "string") {
        return new Response(
          JSON.stringify({ success: false, message: "رقم الهاتف غير صالح" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      let wahaSessionStatus = "STOPPED";
      try {
        const getSessionRes = await fetchWithTimeout(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
          method: "GET",
          headers: wahaHeaders,
        });

        if (getSessionRes.status === 404) {
          await fetchWithTimeout(`${wahaBaseUrl}/api/sessions`, {
            method: "POST",
            headers: wahaHeaders,
            body: JSON.stringify({ name: sessionName }),
          });
        } else if (getSessionRes.ok) {
          const sessionData = await getSessionRes.json();
          wahaSessionStatus = sessionData?.status || "STOPPED";
        }
      } catch (err) {
        console.error("WAHA Session Check Error for pairing code");
      }

      if (wahaSessionStatus !== "WORKING" && wahaSessionStatus !== "CONNECTED" && wahaSessionStatus !== "STARTING") {
        try {
          await fetchWithTimeout(`${wahaBaseUrl}/api/sessions/${sessionName}/start`, {
            method: "POST",
            headers: wahaHeaders,
          });
        } catch (err) {
          console.error("WAHA Session Start Error for pairing code");
        }
      }

      try {
        const pairingRes = await fetchWithTimeout(`${wahaBaseUrl}/api/${sessionName}/auth/request-code`, {
          method: "POST",
          headers: wahaHeaders,
          body: JSON.stringify({ phoneNumber }),
        });

        if (!pairingRes.ok) {
          return new Response(
            JSON.stringify({ success: false, message: "فشل طلب كود الربط من خادم واتساب" }),
            { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }

        const pairingData = await pairingRes.json();
        const code = pairingData?.code;

        if (!code) {
          return new Response(
            JSON.stringify({ success: false, message: "تعذر الحصول على كود الربط من الخادم" }),
            { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }

        return new Response(
          JSON.stringify({
            success: true,
            code: code,
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      } catch (err) {
        return new Response(
          JSON.stringify({ success: false, message: "حدث خطأ غير متوقع أثناء طلب كود الربط (انتهت المهلة أو خطأ اتصال)" }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
    }

    // ------------------------------------------------------------------------
    // ACTION: LOGOUT
    // ------------------------------------------------------------------------
    if (action === "LOGOUT") {
      try {
        await fetchWithTimeout(`${wahaBaseUrl}/api/sessions/${sessionName}/logout`, {
          method: "POST",
          headers: wahaHeaders,
        });
      } catch (err) {
        console.error("WAHA Logout Error");
      }

      const { error: upsertError } = await dbClient.from("teacher_whatsapp_sessions").upsert(
        {
          teacher_id: teacherId,
          session_name: sessionName,
          status: "DISCONNECTED",
          connected_phone: null,
          last_connected_at: null,
          updated_at: new Date().toISOString(),
        },
        { onConflict: "teacher_id" }
      );

      if (upsertError) {
        console.error("Database upsert failed");
        return new Response(
          JSON.stringify({ success: false, message: "فشل تحديث حالة الجلسة في قاعدة البيانات" }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({
          success: true,
          status: "DISCONNECTED",
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ success: false, message: "Invalid or unsupported action" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (error) {
    console.error("Unhandled Edge Function Error");
    return new Response(
      JSON.stringify({ success: false, message: "تعذر الاتصال بخدمة واتساب" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});

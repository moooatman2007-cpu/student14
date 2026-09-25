import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface RequestBody {
  action?: string;
  teacher_id?: string; // Explicitly ignored for security
  session_name?: string; // Explicitly ignored for security
}

Deno.serve(async (req: Request) => {
  // 1. Handle CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    // 2. Validate Authentication via Auth Client (Anon Key + JWT User token)
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ success: false, message: "Authorization header missing" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

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

    // 3. Create Server-Side DB Client using Service Role Key to bypass RLS for write operations
    const dbClient = supabaseServiceRoleKey
      ? createClient(supabaseUrl, supabaseServiceRoleKey)
      : authClient;

    // 4. Derive Teacher ID & Session Name strictly server-side
    const teacherId = user.id;
    const sessionName = `teacher_${teacherId}`;

    // 5. Parse & Validate Request Body
    let body: RequestBody = {};
    try {
      body = await req.json();
    } catch (_) {
      // Body empty or malformed JSON
    }

    const action = body.action;
    if (action !== "START" && action !== "LOGOUT" && action !== "TEST_WAHA_CONNECTION" && action !== "REQUEST_PAIRING_CODE") {
      return new Response(
        JSON.stringify({ success: false, message: "Invalid or unsupported action" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

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

    // ------------------------------------------------------------------------
    // ACTION: START
    // ------------------------------------------------------------------------
    if (action === "START") {
      let wahaSessionStatus = "STOPPED";
      let meId: string | null = null;

      try {
        const getSessionRes = await fetch(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
          method: "GET",
          headers: wahaHeaders,
        });

        if (getSessionRes.status === 404) {
          // Session does not exist -> Create it
          const createSessionRes = await fetch(`${wahaBaseUrl}/api/sessions`, {
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
        console.error("WAHA Session Operation Error:", err instanceof Error ? err.message : "Internal Error");
        return new Response(
          JSON.stringify({ success: false, message: "تعذر بدء ربط واتساب" }),
          { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Step B: If session is already WORKING / CONNECTED
      if (wahaSessionStatus === "WORKING" || wahaSessionStatus === "CONNECTED") {
        let cleanPhone = "";
        if (meId && typeof meId === "string") {
          cleanPhone = meId.split("@")[0].replace(/[^0-9]/g, "");
        }

        // Update database state using server-side Service Role DB client
        await dbClient.from("teacher_whatsapp_sessions").upsert(
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
          await fetch(`${wahaBaseUrl}/api/sessions/${sessionName}/start`, {
            method: "POST",
            headers: wahaHeaders,
          });
        } catch (err) {
          console.error("WAHA Session Start Error:", err instanceof Error ? err.message : "Internal Error");
        }
      }

      // Step D: Fetch QR Code transiently
      let qrData: any = null;
      try {
        const qrRes = await fetch(`${wahaBaseUrl}/api/${sessionName}/auth/qr`, {
          method: "GET",
          headers: {
            ...wahaHeaders,
            Accept: "application/json",
          },
        });

        if (qrRes.ok) {
          qrData = await qrRes.json();
        }
      } catch (err) {
        console.error("WAHA QR Fetch Error:", err instanceof Error ? err.message : "Internal Error");
      }

      // Update database status to SCAN_QR_CODE using Service Role DB client (Without QR in DB payload)
      await dbClient.from("teacher_whatsapp_sessions").upsert(
        {
          teacher_id: teacherId,
          session_name: sessionName,
          status: "SCAN_QR_CODE",
          updated_at: new Date().toISOString(),
        },
        { onConflict: "teacher_id" }
      );

      return new Response(
        JSON.stringify({
          success: true,
          status: "SCAN_QR_CODE",
          session_name: sessionName,
          qr: qrData
            ? {
                mimetype: qrData.mimetype || "image/png",
                data: qrData.data || qrData.qr || "",
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
      let bodyData: any = null;

      try {
        const testRes = await fetch(`${wahaBaseUrl}/api/sessions`, {
          method: "GET",
          headers: wahaHeaders,
        });
        reachable = true;
        status = testRes.status;
        success = testRes.ok;
        if (testRes.ok) {
          bodyData = await testRes.json();
        } else {
          bodyData = await testRes.text();
        }
      } catch (err) {
        console.error("WAHA connection test failed:", err instanceof Error ? err.message : "Internal Error");
        bodyData = { error: err instanceof Error ? err.message : "Internal Error" };
      }

      const latencyMs = Date.now() - startTime;

      return new Response(
        JSON.stringify({
          success,
          reachable,
          status,
          latency_ms: latencyMs,
          sessions: bodyData,
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

      // Ensure the session exists and is started
      let wahaSessionStatus = "STOPPED";
      try {
        const getSessionRes = await fetch(`${wahaBaseUrl}/api/sessions/${sessionName}`, {
          method: "GET",
          headers: wahaHeaders,
        });

        if (getSessionRes.status === 404) {
          await fetch(`${wahaBaseUrl}/api/sessions`, {
            method: "POST",
            headers: wahaHeaders,
            body: JSON.stringify({ name: sessionName }),
          });
        } else if (getSessionRes.ok) {
          const sessionData = await getSessionRes.json();
          wahaSessionStatus = sessionData?.status || "STOPPED";
        }
      } catch (err) {
        console.error("WAHA Session Check Error for pairing code:", err);
      }

      if (wahaSessionStatus !== "WORKING" && wahaSessionStatus !== "CONNECTED" && wahaSessionStatus !== "STARTING") {
        try {
          await fetch(`${wahaBaseUrl}/api/sessions/${sessionName}/start`, {
            method: "POST",
            headers: wahaHeaders,
          });
        } catch (err) {
          console.error("WAHA Session Start Error for pairing code:", err);
        }
      }

      try {
        const pairingRes = await fetch(`${wahaBaseUrl}/api/${sessionName}/auth/request-code`, {
          method: "POST",
          headers: wahaHeaders,
          body: JSON.stringify({ phoneNumber }),
        });

        if (!pairingRes.ok) {
          const errText = await pairingRes.text();
          console.error("WAHA Request Code Failed:", errText);
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
        console.error("WAHA Request Code Exception:", err);
        return new Response(
          JSON.stringify({ success: false, message: "حدث خطأ غير متوقع أثناء طلب كود الربط" }),
          { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
    }

    // ------------------------------------------------------------------------
    // ACTION: LOGOUT
    // ------------------------------------------------------------------------
    if (action === "LOGOUT") {
      try {
        await fetch(`${wahaBaseUrl}/api/sessions/${sessionName}/logout`, {
          method: "POST",
          headers: wahaHeaders,
        });
      } catch (err) {
        console.error("WAHA Logout Error:", err instanceof Error ? err.message : "Internal Error");
      }

      // Update database status to DISCONNECTED using Service Role DB client
      await dbClient.from("teacher_whatsapp_sessions").upsert(
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
    console.error("Unhandled Edge Function Error:", error instanceof Error ? error.message : "Internal Error");
    return new Response(
      JSON.stringify({ success: false, message: "تعذر الاتصال بخدمة واتساب" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});

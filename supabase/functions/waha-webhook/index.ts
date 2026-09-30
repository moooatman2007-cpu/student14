import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const wahaBaseUrl = (Deno.env.get("WAHA_BASE_URL") || "").replace(/\/+$/, "");
const wahaApiKey = Deno.env.get("WAHA_API_KEY");

if (!supabaseUrl || !serviceRoleKey || !wahaBaseUrl || !wahaApiKey) {
  console.error("Missing required environment variables for waha-webhook");
}

const supabase = createClient(supabaseUrl || "", serviceRoleKey || "", {
  auth: {
    persistSession: false,
    autoRefreshToken: false,
  },
});

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "content-type, authorization, apikey, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

/**
 * Normalize Egyptian phone numbers to international format (201012345678).
 */
function normalizeEgyptianPhone(value: string): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const digits = value.replace(/\D/g, "");

  // Local Egyptian mobile number (01012345678 -> 201012345678)
  if (/^01[0125][0-9]{8}$/.test(digits)) {
    return `20${digits.slice(1)}`;
  }

  // International Egyptian mobile number (201012345678)
  if (/^201[0125][0-9]{8}$/.test(digits)) {
    return digits;
  }

  // Already with leading double zeros (00201012345678 -> 201012345678)
  if (/^00201[0125][0-9]{8}$/.test(digits)) {
    return digits.slice(2);
  }

  if (digits.length >= 8 && digits.length <= 15) {
    return digits;
  }

  return null;
}

/**
 * Remove WhatsApp suffixes from an ID.
 */
function cleanWhatsAppId(value: string): string {
  return value
    .replace(/@c\.us$/i, "")
    .replace(/@lid$/i, "")
    .trim();
}

/**
 * Mask identifiers before writing them to logs.
 */
function maskIdentifier(value: string | null | undefined): string {
  if (!value) return "null";

  const clean = value.toString();

  if (clean.length <= 6) {
    return "***";
  }

  return `${clean.slice(0, 3)}***${clean.slice(-3)}`;
}

function isWorkingStatus(status: unknown): boolean {
  return status === "WORKING" || status === "CONNECTED";
}

/**
 * WAHA GET helper with timeout and fallback.
 */
async function wahaGet(path: string, timeoutMs = 10000) {
  const controller = new AbortController();

  const timeoutId = setTimeout(() => {
    controller.abort();
  }, timeoutMs);

  const startedAt = Date.now();

  try {
    const response = await fetch(`${wahaBaseUrl}${path}`, {
      method: "GET",
      headers: {
        Accept: "application/json",
        "X-Api-Key": wahaApiKey || "",
      },
      signal: controller.signal,
    });

    const latencyMs = Date.now() - startedAt;
    const raw = await response.text();

    let data: unknown = null;

    try {
      data = raw ? JSON.parse(raw) : null;
    } catch {
      data = raw;
    }

    return {
      ok: response.ok,
      status: response.status,
      latencyMs,
      data,
    };
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Resolve a WAHA LID to a phone number.
 */
async function resolveLid(sessionName: string, lid: string) {
  const encodedSession = encodeURIComponent(sessionName);
  const encodedLid = encodeURIComponent(lid);

  let res = await wahaGet(`/api/${encodedSession}/lids/${encodedLid}`);
  if (!res.ok) {
    // Fallback to non-session LID endpoint
    res = await wahaGet(`/api/lids/${encodedLid}`);
  }
  return res;
}

/**
 * Extract message sender from different WAHA payload shapes.
 */
function extractSender(payload: any): string | null {
  const candidates = [
    payload?.from,
    payload?.author,
    payload?.participant,
    payload?.chatId,
  ];

  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim()) {
      return candidate.trim();
    }
  }

  return null;
}

// Helper for timing-safe string comparison
function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let mismatch = 0;
  for (let i = 0; i < a.length; i++) {
    mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return mismatch === 0;
}

Deno.serve(async (req: Request) => {
  // 1. CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  // 2. HTTP Method Validation
  if (req.method !== "POST") {
    return jsonResponse({ ok: false, error: "Method not allowed" }, 405);
  }

  try {
    // 3. HMAC-SHA512 Webhook Verification
    const webhookSecret = Deno.env.get("WAHA_WEBHOOK_SECRET");
    if (!webhookSecret || !webhookSecret.trim()) {
      console.error("WAHA_WEBHOOK_SECRET is not configured");
      return jsonResponse({ ok: false, error: "Unauthorized webhook configuration" }, 401);
    }

    const hmacHeader = req.headers.get("X-Webhook-Hmac");
    const algoHeader = req.headers.get("X-Webhook-Hmac-Algorithm");

    if (!hmacHeader || !algoHeader || algoHeader.toLowerCase() !== "sha512") {
      return jsonResponse({ ok: false, error: "Missing or invalid webhook security headers" }, 401);
    }

    const rawBody = await req.text();

    const enc = new TextEncoder();
    const keyData = enc.encode(webhookSecret.trim());
    const msgData = enc.encode(rawBody);

    const cryptoKey = await crypto.subtle.importKey(
      "raw",
      keyData,
      { name: "HMAC", hash: "SHA-512" },
      false,
      ["sign"]
    );

    const signatureBuffer = await crypto.subtle.sign("HMAC", cryptoKey, msgData);
    const signatureArray = Array.from(new Uint8Array(signatureBuffer));
    const computedHex = signatureArray.map((b) => b.toString(16).padStart(2, "0")).join("");

    if (!timingSafeEqual(computedHex.toLowerCase(), hmacHeader.trim().toLowerCase())) {
      return jsonResponse({ ok: false, error: "Invalid webhook signature" }, 401);
    }

    // 4. Parse JSON Body from Verified Raw Body
    let body: any;
    try {
      body = JSON.parse(rawBody);
    } catch {
      return jsonResponse({ ok: false, error: "Invalid JSON payload" }, 400);
    }

    // 5. Basic Event Validation (Only process message events)
    const event = body?.event;
    if (event !== "message") {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Not a message event",
        event: event ?? null,
      });
    }

    const sessionName = body?.session;
    const payload = body?.payload;

    if (
      typeof sessionName !== "string" ||
      !sessionName.trim() ||
      !payload ||
      typeof payload !== "object"
    ) {
      return jsonResponse(
        { ok: false, error: "Invalid WAHA message payload" },
        400
      );
    }

    // 5. Ignore messages sent by our own system
    if (payload?.fromMe === true) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Outgoing message",
      });
    }

    // 6. Resolve sender
    const sender = extractSender(payload);
    if (!sender) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "No sender identifier",
      });
    }

    // 7. Ignore group chats
    if (sender.endsWith("@g.us")) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Group message",
      });
    }

    // 8. Find teacher session safely
    const { data: session, error: sessionError } = await supabase
      .from("teacher_whatsapp_sessions")
      .select("teacher_id, session_name, status, connected_phone")
      .eq("session_name", sessionName)
      .maybeSingle();

    if (sessionError) {
      console.error(
        "WAHA webhook session lookup failed:",
        sessionError.message
      );
      // Return 200 to prevent WAHA retry flood when database is momentarily busy
      return jsonResponse({ ok: false, error: "Session lookup failed" }, 200);
    }

    if (!session) {
      console.warn(
        "WAHA webhook received event for unknown session:",
        sessionName
      );
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Unknown WhatsApp session",
        session: sessionName,
      });
    }

    // 9. Check session status
    if (!isWorkingStatus(session.status)) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "WhatsApp session is not working",
        session: sessionName,
        session_status: session.status,
      });
    }

    // 10. Resolve sender phone
    let phoneE164: string | null = null;
    let chatId = sender;

    if (sender.endsWith("@c.us")) {
      const rawPhone = cleanWhatsAppId(sender);
      phoneE164 = normalizeEgyptianPhone(rawPhone);

      if (!phoneE164) {
        console.warn(
          "Non-Egyptian or invalid phone from WAHA @c.us sender:",
          maskIdentifier(rawPhone)
        );
        return jsonResponse({
          ok: true,
          saved: false,
          ignored: true,
          reason: "Non-standard or invalid phone number",
        });
      }
    } else if (sender.endsWith("@lid")) {
      const lidNumber = cleanWhatsAppId(sender);
      const lidResponse = await resolveLid(sessionName, lidNumber);

      if (!lidResponse.ok) {
        console.warn("WAHA LID lookup failed:", {
          status: lidResponse.status,
          lid: maskIdentifier(lidNumber),
        });
        return jsonResponse({
          ok: true,
          saved: false,
          reason: "Could not resolve WhatsApp LID",
          session: sessionName,
        });
      }

      const lidData = lidResponse.data as {
        lid?: string | null;
        pn?: string | null;
      };

      if (!lidData?.pn) {
        return jsonResponse({
          ok: true,
          saved: false,
          reason: "WAHA has no phone mapping for this LID yet",
          session: sessionName,
        });
      }

      const rawPhone = cleanWhatsAppId(lidData.pn);
      phoneE164 = normalizeEgyptianPhone(rawPhone);

      if (!phoneE164) {
        return jsonResponse({
          ok: true,
          saved: false,
          reason: "Invalid phone returned by WAHA LID mapping",
        });
      }

      chatId = sender;
    } else {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Unsupported WhatsApp sender type",
      });
    }

    if (!phoneE164) {
      return jsonResponse({
        ok: true,
        saved: false,
        reason: "Could not resolve sender phone",
      });
    }

    // 11. Upsert contact into teacher_whatsapp_contacts safely
    const now = new Date().toISOString();
    let contactId: string | null = null;

    const { data: existingContact } = await supabase
      .from("teacher_whatsapp_contacts")
      .select("id")
      .eq("teacher_id", session.teacher_id)
      .eq("phone_e164", phoneE164)
      .maybeSingle();

    if (existingContact?.id) {
      contactId = existingContact.id;
      const { error: updateError } = await supabase
        .from("teacher_whatsapp_contacts")
        .update({
          session_name: session.session_name,
          chat_id: chatId,
          updated_at: now,
          last_seen_at: now,
        })
        .eq("id", existingContact.id);

      if (updateError) {
        console.error("WAHA contact update failed:", updateError.message);
      }
    } else {
      const { data: inserted, error: insertError } = await supabase
        .from("teacher_whatsapp_contacts")
        .insert({
          teacher_id: session.teacher_id,
          session_name: session.session_name,
          phone_e164: phoneE164,
          chat_id: chatId,
          updated_at: now,
          last_seen_at: now,
        })
        .select("id")
        .maybeSingle();

      if (insertError) {
        console.warn("WAHA contact insert notice, attempting update fallback:", insertError.message);
        await supabase
          .from("teacher_whatsapp_contacts")
          .update({
            session_name: session.session_name,
            chat_id: chatId,
            updated_at: now,
            last_seen_at: now,
          })
          .eq("teacher_id", session.teacher_id)
          .eq("phone_e164", phoneE164);
      } else {
        contactId = inserted?.id ?? null;
      }
    }

    console.log(
      "WHATSAPP CONTACT SAVED",
      JSON.stringify({
        teacher_id: maskIdentifier(session.teacher_id),
        session_name: session.session_name,
        phone_e164: maskIdentifier(phoneE164),
        chat_id: maskIdentifier(chatId),
      })
    );

    return jsonResponse({
      ok: true,
      saved: true,
      session: sessionName,
      phone_e164: phoneE164,
      chat_id: chatId,
      contact_id: contactId,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      return jsonResponse({ ok: false, error: "WAHA request timed out" }, 200);
    }

    console.error(
      "WAHA webhook unexpected error:",
      error instanceof Error ? error.message : "Unknown error"
    );

    return jsonResponse({ ok: false, error: "Internal webhook error" }, 200);
  }
});

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const wahaBaseUrl = (Deno.env.get("WAHA_BASE_URL") || "").replace(/\/+$/, "");
const wahaApiKey = Deno.env.get("WAHA_API_KEY");
const webhookSecret = Deno.env.get("WAHA_WEBHOOK_SECRET");

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "content-type, authorization, apikey, x-client-info, x-webhook-hmac, x-hub-signature",
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

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let mismatch = 0;
  for (let i = 0; i < a.length; i++) {
    mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return mismatch === 0;
}

/**
 * Normalize Egyptian phone numbers to international format (201012345678).
 */
function normalizeEgyptianPhone(value: string): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const digits = value.replace(/\D/g, "");

  if (/^01[0125][0-9]{8}$/.test(digits)) {
    return `20${digits.slice(1)}`;
  }

  if (/^201[0125][0-9]{8}$/.test(digits)) {
    return digits;
  }

  if (/^00201[0125][0-9]{8}$/.test(digits)) {
    return digits.slice(2);
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

/**
 * WAHA GET helper with timeout.
 */
async function wahaGet(path: string, timeoutMs = 10000) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
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

Deno.serve(async (req: Request) => {
  // 1. CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  // 2. HTTP Method Validation
  if (req.method !== "POST") {
    return jsonResponse({ ok: false, error: "Method not allowed" }, 405);
  }

  // 3. Environment Validation
  if (!supabaseUrl || !serviceRoleKey) {
    console.error("Missing required Supabase environment variables in waha-webhook");
    return jsonResponse({ ok: false, error: "Server misconfiguration" }, 500);
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });

  try {
    // 4. Read raw request body for HMAC verification & JSON parsing
    const rawBody = await req.text();
    if (!rawBody) {
      return jsonResponse({ ok: false, error: "Empty request body" }, 400);
    }

    // 5. HMAC Signature Verification (if configured)
    if (webhookSecret && webhookSecret.trim()) {
      const signatureHeader =
        req.headers.get("X-Webhook-Hmac") ||
        req.headers.get("X-Hub-Signature") ||
        req.headers.get("X-WAHA-Signature");

      if (!signatureHeader) {
        console.warn("WAHA webhook rejected: missing signature header");
        return jsonResponse({ ok: false, error: "Missing webhook signature" }, 401);
      }

      const cleanSig = signatureHeader
        .replace(/^sha256=/i, "")
        .replace(/^sha512=/i, "")
        .trim()
        .toLowerCase();

      const enc = new TextEncoder();
      const key = await crypto.subtle.importKey(
        "raw",
        enc.encode(webhookSecret.trim()),
        { name: "HMAC", hash: "SHA-256" },
        false,
        ["sign"]
      );

      const sigBuffer = await crypto.subtle.sign("HMAC", key, enc.encode(rawBody));
      const hashArray = Array.from(new Uint8Array(sigBuffer));
      const computedHex = hashArray
        .map((b) => b.toString(16).padStart(2, "0"))
        .join("")
        .toLowerCase();

      if (!timingSafeEqual(computedHex, cleanSig)) {
        console.warn("WAHA webhook rejected: invalid signature");
        return jsonResponse({ ok: false, error: "Invalid webhook signature" }, 401);
      }
    }

    // 6. Parse JSON Body
    let body: any;
    try {
      body = JSON.parse(rawBody);
    } catch {
      return jsonResponse({ ok: false, error: "Invalid JSON payload" }, 400);
    }

    // 7. Event Validation
    const event = body?.event;
    if (event !== "message" && event !== "session.status") {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Unsupported event type",
        event: event ?? null,
      });
    }

    // -------------------------------------------------------------------------
    // EVENT: session.status
    // -------------------------------------------------------------------------
    if (event === "session.status") {
      const sessionName =
        (typeof body?.session === "string" && body.session.trim()) ||
        (typeof body?.payload?.name === "string" && body.payload.name.trim()) ||
        (typeof body?.payload?.session === "string" && body.payload.session.trim()) ||
        "";

      if (!sessionName) {
        return jsonResponse(
          { ok: false, error: "Missing session identifier in session.status event" },
          400
        );
      }

      const rawStatus =
        (typeof body?.payload?.status === "string" && body.payload.status.trim()) ||
        (typeof body?.payload === "string" && body.payload.trim()) ||
        (typeof body?.status === "string" && body.status.trim()) ||
        "";

      if (!rawStatus) {
        return jsonResponse(
          { ok: false, error: "Missing status in session.status event" },
          400
        );
      }

      const upperStatus = rawStatus.toUpperCase();

      // Look up session in DB
      const { data: session, error: sessionError } = await supabase
        .from("teacher_whatsapp_sessions")
        .select("teacher_id, session_name, status, connected_phone")
        .eq("session_name", sessionName)
        .maybeSingle();

      if (sessionError) {
        console.error(
          "WAHA webhook session.status database lookup error:",
          sessionError.message
        );
        return jsonResponse({ ok: false, error: "Database error during session lookup" }, 503);
      }

      // If session record does not exist yet, infer teacher_id from session_name (teacher_<uuid>)
      let teacherId = session?.teacher_id || null;
      if (!teacherId && sessionName.startsWith("teacher_")) {
        const candidateId = sessionName.substring("teacher_".length).trim();
        const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
        if (uuidRegex.test(candidateId)) {
          teacherId = candidateId;
        }
      }

      if (!session && !teacherId) {
        console.warn("WAHA webhook received session.status for unknown session:", sessionName);
        return jsonResponse({
          ok: true,
          ignored: true,
          reason: "Unknown WhatsApp session",
          session: sessionName,
        });
      }

      const now = new Date().toISOString();
      const currentStatus = session?.status || "DISCONNECTED";

      let newStatus: string;
      let newPhone: string | null | undefined = undefined;
      let updateLastConnected = false;

      if (upperStatus === "WORKING" || upperStatus === "CONNECTED") {
        newStatus = "CONNECTED";
        updateLastConnected = true;

        // Try to extract phone number from payload or query WAHA me
        let phone = extractPhoneFromMePayload(body?.payload?.me || body?.me || body?.payload);
        if (!phone && wahaBaseUrl) {
          try {
            const meRes = await wahaGet(`/api/${encodeURIComponent(sessionName)}/me`, 4000);
            if (meRes.ok) {
              phone = extractPhoneFromMePayload(meRes.data);
            }
          } catch (_err) {
            // Non-fatal fallback
          }
        }
        if (phone) {
          newPhone = phone;
        }
      } else if (upperStatus === "SCAN_QR_CODE" || upperStatus === "STARTING") {
        // If already connected, do not overwrite to SCAN_QR_CODE
        if (currentStatus === "CONNECTED") {
          return jsonResponse({
            ok: true,
            ignored: true,
            reason: "Session is already connected, ignoring transient starting/QR status",
            session: sessionName,
            status: currentStatus,
          });
        }
        newStatus = "SCAN_QR_CODE";
      } else if (upperStatus === "FAILED") {
        newStatus = "FAILED";
        newPhone = null;
      } else {
        // STOPPED, DISCONNECTED, or unknown
        newStatus = "DISCONNECTED";
        newPhone = null;
      }

      const updateData: Record<string, any> = {
        session_name: sessionName,
        status: newStatus,
        updated_at: now,
      };

      if (updateLastConnected) {
        updateData.last_connected_at = now;
      }
      if (newPhone !== undefined) {
        updateData.connected_phone = newPhone;
      }

      let updateError = null;
      if (session) {
        const { error } = await supabase
          .from("teacher_whatsapp_sessions")
          .update(updateData)
          .eq("session_name", sessionName);
        updateError = error;
      } else if (teacherId) {
        updateData.teacher_id = teacherId;
        const { error } = await supabase
          .from("teacher_whatsapp_sessions")
          .upsert(updateData, { onConflict: "teacher_id" });
        updateError = error;
      }

      if (updateError) {
        console.error("Failed to update teacher_whatsapp_sessions:", updateError.message);
        return jsonResponse({ ok: false, error: "Database error updating session status" }, 500);
      }

      console.log(
        "WHATSAPP SESSION STATUS UPDATED",
        JSON.stringify({
          session_name: sessionName,
          teacher_id: maskIdentifier(teacherId),
          old_status: currentStatus,
          new_status: newStatus,
        })
      );

      return jsonResponse({
        ok: true,
        event: "session.status",
        session: sessionName,
        status: newStatus,
        previous_status: currentStatus,
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

    // 8. Ignore messages sent by our own system
    if (payload?.fromMe === true) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Outgoing message",
      });
    }

    // 9. Idempotency Check using message ID / event hash
    const messageId =
      typeof payload?.id === "string"
        ? payload.id
        : typeof payload?._serialized === "string"
        ? payload._serialized
        : null;

    if (messageId) {
      const { data: existingEvent, error: dedupError } = await supabase
        .from("waha_processed_events")
        .select("message_id")
        .eq("message_id", messageId)
        .maybeSingle();

      if (dedupError) {
        console.warn("Idempotency lookup warning:", dedupError.message);
      } else if (existingEvent) {
        return jsonResponse({
          ok: true,
          processed: true,
          idempotent: true,
          reason: "Event already processed",
        });
      }
    }

    // 10. Resolve sender
    const sender = extractSender(payload);
    if (!sender) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "No sender identifier",
      });
    }

    // 11. Ignore group chats
    if (sender.endsWith("@g.us")) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "Group message",
      });
    }

    // 12. Find teacher session safely with error check
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
      // Return 503 so WAHA retries on transient DB errors
      return jsonResponse({ ok: false, error: "Session lookup database error" }, 503);
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

    // 13. Check session status
    if (!isWorkingStatus(session.status)) {
      return jsonResponse({
        ok: true,
        ignored: true,
        reason: "WhatsApp session is not working",
        session: sessionName,
        session_status: session.status,
      });
    }

    // 14. Resolve sender phone
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

    // 15. Upsert contact into teacher_whatsapp_contacts with proper error handling and conflict handling
    const now = new Date().toISOString();
    
    const { data: contactData, error: contactError } = await supabase
      .from("teacher_whatsapp_contacts")
      .upsert(
        {
          teacher_id: session.teacher_id,
          session_name: session.session_name,
          phone_e164: phoneE164,
          chat_id: chatId,
          updated_at: now,
          last_seen_at: now,
        },
        {
          onConflict: "teacher_id,phone_e164",
        }
      )
      .select("id")
      .maybeSingle();

    if (contactError) {
      console.error("WAHA contact upsert failed:", contactError.message);
      // Return 503 for transient database errors so WAHA retries
      return jsonResponse({ ok: false, error: "Database error saving contact", details: contactError.message }, 503);
    }

    const contactId = contactData?.id ?? null;
    if (!contactId) {
      console.error("WAHA contact upsert succeeded but no contact ID returned");
      return jsonResponse({ ok: false, error: "Failed to persist contact record" }, 500);
    }

    // 16. Record processed event for idempotency
    if (messageId) {
      await supabase
        .from("waha_processed_events")
        .insert({
          message_id: messageId,
          session_name: sessionName,
          processed_at: now,
        })
        .select("message_id")
        .maybeSingle();
    }

    console.log(
      "WHATSAPP CONTACT SAVED",
      JSON.stringify({
        teacher_id: maskIdentifier(session.teacher_id),
        session_name: session.session_name,
        phone_e164: maskIdentifier(phoneE164),
        chat_id: maskIdentifier(chatId),
        contact_id: contactId,
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
      return jsonResponse({ ok: false, error: "WAHA request timed out" }, 503);
    }

    console.error(
      "WAHA webhook unexpected error:",
      error instanceof Error ? error.message : "Unknown error"
    );

    return jsonResponse({ ok: false, error: "Internal webhook error" }, 500);
  }
});

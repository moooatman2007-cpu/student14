import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-worker-secret",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

export interface WorkerConfig {
  supabaseUrl: string;
  serviceRoleKey: string;
  workerSecret: string;
  wahaBaseUrl: string;
  wahaApiKey: string;
}

export interface ProcessEventContext {
  supabase: any;
  wahaBaseUrl: string;
  wahaApiKey: string;
  eventType: "ABSENCE" | "HOMEWORK" | "RECITATION" | "EXAM" | "MONTHLY_REPORT";
  event: any;
  attemptId: string;
  claimToken: string;
  logicalNotificationKey: string;
  providerIdempotencyKey: string;
}

export function formatParentPhone(phone: string): string {
  let clean = phone.replace(/[^\d]/g, "");
  if (clean.startsWith("01") && clean.length === 11) {
    clean = "2" + clean;
  } else if (clean.startsWith("0020")) {
    clean = clean.substring(2);
  }
  return clean;
}

export function extractWahaMessageId(resData: any): string | null {
  if (!resData) return null;
  // Format 1: serialized string e.g. "true_2010...@c.us_3EB0..."
  if (typeof resData.id === "string" && resData.id.trim().length > 0) {
    return resData.id.trim();
  }
  // Format 2: object with id or _serialized (WEBJS engine)
  if (resData.id && typeof resData.id === "object") {
    if (typeof resData.id._serialized === "string" && resData.id._serialized.trim().length > 0) {
      return resData.id._serialized.trim();
    }
    if (typeof resData.id.id === "string" && resData.id.id.trim().length > 0) {
      return resData.id.id.trim();
    }
  }
  // Format 3: explicit messageId property
  if (typeof resData.messageId === "string" && resData.messageId.trim().length > 0) {
    return resData.messageId.trim();
  }
  // Format 4: Baileys raw key { key: { id: "3EB0..." } }
  if (resData.key && typeof resData.key === "object" && typeof resData.key.id === "string") {
    return resData.key.id.trim();
  }
  // Format 5: top-level _serialized
  if (typeof resData._serialized === "string" && resData._serialized.trim().length > 0) {
    return resData._serialized.trim();
  }
  return null;
}

export function constructNotificationMessage(
  eventType: "ABSENCE" | "HOMEWORK" | "RECITATION" | "EXAM" | "MONTHLY_REPORT",
  studentName: string
): string {
  switch (eventType) {
    case "ABSENCE":
      return `السلام عليكم،\n\nنحب نبلغ حضرتك إن الطالب/ة ${studentName} تم تسجيل غيابه/غيابها اليوم.\n\nلو الغياب بعذر، يرجى التواصل مع المعلم.\n\nشكرًا لحضرتك.`;
    case "HOMEWORK":
      return `السلام عليكم،\n\nنود إعلامكم بخصوص الواجب المدرسي للطالب/ة ${studentName}:\nتم تسجيل حالة الواجب اليوم.\n\nشكرًا لحضرتكم.`;
    case "RECITATION":
      return `السلام عليكم،\n\nنود إعلامكم بنتيجة تسميع القرآن الكريم للطالب/ة ${studentName}:\nتم تسجيل جلسة التسميع بنجاح.\n\nبارك الله فيه.`;
    case "EXAM":
      return `السلام عليكم،\n\nنود إعلامكم بنتيجة الاختبار للطالب/ة ${studentName}:\nتم رصد نتيجة الاختبار في سجل الطالب.\n\nمع تمنياتنا بالتوفيق الدائم.`;
    case "MONTHLY_REPORT":
      return `السلام عليكم،\n\nنود إعلامكم بصدور التقرير الشهري للطالب/ة ${studentName}.\nيمكنكم مراجعة المعلم للاطلاع على تقرير الأداء الشامل.\n\nمع تحيات إدارة المعلم.`;
  }
}

export async function recordDeliveryAttempt(
  supabase: any,
  params: {
    attemptId: string;
    claimToken: string;
    providerStatus: "SENDING" | "SENT" | "FAILED" | "UNKNOWN";
    providerMessageId?: string | null;
    providerResponse?: any;
    providerError?: string | null;
  }
): Promise<{ data: any; error?: string }> {
  const { data, error } = await supabase.rpc(
    "record_notification_delivery_attempt_v2",
    {
      p_attempt_id: params.attemptId,
      p_claim_token: params.claimToken,
      p_provider_status: params.providerStatus,
      p_provider_message_id: params.providerMessageId || null,
      p_provider_response: params.providerResponse || null,
      p_provider_error: params.providerError || null,
    },
  );

  return { data, error: error?.message };
}

export async function reconcileDeliveryAttempt(
  supabase: any,
  params: {
    attemptId: string;
    claimToken: string;
    resolution: "CONFIRMED_SENT" | "CONFIRMED_NOT_SENT";
    providerMessageId?: string | null;
    providerResponse?: any;
    providerError?: string | null;
  },
): Promise<{ data: any; error?: string }> {
  const { data, error } = await supabase.rpc(
    "reconcile_notification_delivery_attempt_v2",
    {
      p_attempt_id: params.attemptId,
      p_claim_token: params.claimToken,
      p_resolution: params.resolution,
      p_provider_message_id: params.providerMessageId || null,
      p_provider_response: params.providerResponse || null,
      p_provider_error: params.providerError || null,
    },
  );

  return { data, error: error?.message };
}

export async function checkWahaRecentMessageSent(
  wahaBaseUrl: string,
  wahaApiKey: string,
  sessionName: string,
  chatId: string,
  exactMessageText: string,
  minTimestampSec: number,
  maxTimestampSec: number
): Promise<{ found: boolean; wahaMessageId: string | null }> {
  try {
    const url = `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/chats/${encodeURIComponent(chatId)}/messages?limit=20`;
    const res = await fetch(url, {
      method: "GET",
      headers: {
        "X-Api-Key": wahaApiKey,
        "Accept": "application/json",
      },
    });

    if (!res.ok) return { found: false, wahaMessageId: null };

    const messages = await res.json();
    if (!Array.isArray(messages)) return { found: false, wahaMessageId: null };

    const normalizedTarget = exactMessageText.trim();

    for (const msg of messages) {
      // 1. Must be sent by the teacher's session
      if (msg.fromMe !== true) continue;

      // 2. A valid timestamp is required; without it an old identical message
      //      must never be associated with the current attempt.
      if (typeof msg.timestamp !== "number" || !Number.isFinite(msg.timestamp)) {
        continue;
      }
      if (msg.timestamp < minTimestampSec || msg.timestamp > maxTimestampSec) {
        continue;
      }

      // 3. Exact deterministic fingerprint match with generated message
      const body = typeof msg.body === "string" ? msg.body.trim() : "";
      if (body === normalizedTarget) {
        const id = extractWahaMessageId(msg);
        return { found: true, wahaMessageId: id };
      }
    }
  } catch (_) {
    // Non-fatal reconciliation probe
  }
  return { found: false, wahaMessageId: null };
}

export async function finalizeNotificationSentV2(
  supabase: any,
  params: {
    attemptId: string;
    claimToken: string;
    eventId: string;
    eventType: string;
    teacherId: string;
    studentId: string;
    sourceId: string;
    channel?: string;
    wahaMessageId?: string | null;
  }
): Promise<{ success: boolean; error?: string }> {
  const channel = params.channel || "WHATSAPP";
  const { error } = await supabase.rpc("finalize_notification_sent_v2", {
    p_attempt_id: params.attemptId,
    p_claim_token: params.claimToken,
    p_event_id: params.eventId,
    p_event_type: params.eventType,
    p_teacher_id: params.teacherId,
    p_student_id: params.studentId,
    p_source_id: params.sourceId,
    p_channel: channel,
    p_waha_message_id: params.wahaMessageId || null,
  });

  return error
    ? { success: false, error: error.message }
    : { success: true };
}

export async function processNotificationEvent(ctx: ProcessEventContext): Promise<{
  success: boolean;
  reconciled: boolean;
  duplicatePrevented: boolean;
  wahaMessageId?: string | null;
  wahaSent?: boolean;
  error?: string;
}> {
  const {
    supabase,
    wahaBaseUrl,
    wahaApiKey,
    eventType,
    event,
    attemptId,
    claimToken,
  } = ctx;
  const teacherId = event.teacher_id;
  const studentId = event.student_id;

  let sourceId = "";

  switch (eventType) {
    case "ABSENCE":
      sourceId = event.attendance_id;
      break;
    case "HOMEWORK":
      sourceId = event.homework_id;
      break;
    case "RECITATION":
      sourceId = event.recitation_id;
      break;
    case "EXAM":
      sourceId = event.exam_id;
      break;
    case "MONTHLY_REPORT":
      sourceId = event.monthly_report_id;
      break;
  }

  const recordFailed = async (errorMsg: string) => {
    await recordDeliveryAttempt(supabase, {
      attemptId,
      claimToken,
      providerStatus: "FAILED",
      providerError: errorMsg,
    });
  };

  const finalizeConfirmedSend = async (
    wahaMessageId: string,
  ): Promise<{ success: boolean; error?: string }> => {
    return finalizeNotificationSentV2(supabase, {
      attemptId,
      claimToken,
      eventId: event.id,
      eventType,
      teacherId,
      studentId,
      sourceId,
      channel: "WHATSAPP",
      wahaMessageId,
    });
  };

  // 1. Strict Tenant Isolation Check: verify student belongs to teacher
  const { data: student, error: studentErr } = await supabase
    .from("students")
    .select("id, full_name, parent_phone, has_whatsapp, teacher_id, deleted_at")
    .eq("id", studentId)
    .eq("teacher_id", teacherId)
    .maybeSingle();

  if (studentErr || !student || student.deleted_at != null) {
    await recordFailed("Student not found, deleted, or tenant mismatch");
    return { success: false, reconciled: false, duplicatePrevented: false, error: "Tenant mismatch or student not found" };
  }

  if (!student.has_whatsapp || !student.parent_phone) {
    await recordFailed("Student has WhatsApp disabled or no parent phone");
    return { success: false, reconciled: false, duplicatePrevented: false, error: "WhatsApp disabled" };
  }

  // 2. Check Teacher WhatsApp Session
  const { data: sessionData } = await supabase
    .from("teacher_whatsapp_sessions")
    .select("session_name, status, connected_phone")
    .eq("teacher_id", teacherId)
    .maybeSingle();

  const sessionName = sessionData?.session_name;
  const sessionStatus = sessionData?.status;
  if (!sessionName || (sessionStatus !== "WORKING" && sessionStatus !== "CONNECTED")) {
    await recordFailed(
      `WhatsApp session unavailable or not WORKING/CONNECTED (session: ${sessionName || "NONE"}, status: ${sessionStatus || "NONE"})`,
    );
    return { success: false, reconciled: false, duplicatePrevented: false, error: "WhatsApp session not active" };
  }

  const rawPhone = formatParentPhone(student.parent_phone);
  if (rawPhone.length < 8) {
    await recordFailed(`Invalid parent phone number: ${student.parent_phone}`);
    return { success: false, reconciled: false, duplicatePrevented: false, error: "Invalid phone number" };
  }
  const chatId = `${rawPhone}@c.us`;

  // 3. Construct deterministic exact message text BEFORE reconciliation and sendText
  const messageText = constructNotificationMessage(eventType, student.full_name);

  // 4. Record the durable attempt before the external provider request.
  const sending = await recordDeliveryAttempt(supabase, {
    attemptId,
    claimToken,
    providerStatus: "SENDING",
    providerResponse: {
      event_type: eventType,
      logical_notification_key: ctx.logicalNotificationKey,
      provider_idempotency_key: ctx.providerIdempotencyKey,
    },
  });

  if (sending.error) {
    return {
      success: false,
      reconciled: false,
      duplicatePrevented: false,
      error: `Attempt transition failed: ${sending.error}`,
    };
  }

  // 5. External Call to WAHA /api/sendText
  const wahaUrl = `${wahaBaseUrl}/api/sendText`;
  let sendError = "";
  let extractedWahaId: string | null = null;
  let providerResponse: any = null;
  let providerStatus: "SENT" | "FAILED" | "UNKNOWN" = "UNKNOWN";

  try {
    const wahaRes = await fetch(wahaUrl, {
      method: "POST",
      headers: {
        "X-Api-Key": wahaApiKey,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        session: sessionName,
        chatId,
        text: messageText,
      }),
      signal: AbortSignal.timeout(30000),
    });

    if (wahaRes.ok) {
      try {
        providerResponse = await wahaRes.json();
        extractedWahaId = extractWahaMessageId(providerResponse);
      } catch (err: any) {
        providerResponse = { parse_error: err?.message || String(err) };
      }

      providerStatus = extractedWahaId ? "SENT" : "UNKNOWN";
    } else {
      const errText = await wahaRes.text();
      sendError = `WAHA send failed (${wahaRes.status}): ${errText}`;
      // A non-2xx response does not prove that WAHA did not accept or send
      // the request. Keep it UNKNOWN to prevent a blind resend.
      providerStatus = "UNKNOWN";
    }
  } catch (err: any) {
    sendError = `WAHA network failure: ${err.message || String(err)}`;
    providerStatus = "UNKNOWN";
  }

  const recorded = await recordDeliveryAttempt(supabase, {
    attemptId,
    claimToken,
    providerStatus,
    providerMessageId: extractedWahaId,
    providerResponse,
    providerError: sendError || null,
  });

  if (recorded.error) {
    return {
      success: false,
      reconciled: false,
      duplicatePrevented: false,
      wahaSent: providerStatus !== "FAILED",
      wahaMessageId: extractedWahaId,
      error: recorded.error,
    };
  }

  if (providerStatus === "FAILED") {
    return {
      success: false,
      reconciled: false,
      duplicatePrevented: false,
      error: sendError || "WAHA send failed",
    };
  }

  // A successful provider response without a durable provider message ID is
  // uncertain. Probe history only for reconciliation; never resend here.
  if (providerStatus === "UNKNOWN") {
    const nowSec = Math.floor(Date.now() / 1000);
    const recon = await checkWahaRecentMessageSent(
      wahaBaseUrl,
      wahaApiKey,
      sessionName,
      chatId,
      messageText,
      nowSec - 120,
      nowSec + 60,
    );

    if (recon.found && recon.wahaMessageId) {
      const reconciled = await reconcileDeliveryAttempt(supabase, {
        attemptId,
        claimToken,
        resolution: "CONFIRMED_SENT",
        providerMessageId: recon.wahaMessageId,
        providerResponse,
      });

      if (!reconciled.error) {
        const finalized = await finalizeConfirmedSend(recon.wahaMessageId);
        return {
          success: finalized.success,
          reconciled: finalized.success,
          duplicatePrevented: finalized.success,
          wahaSent: true,
          wahaMessageId: recon.wahaMessageId,
          error: finalized.error,
        };
      }
    }

    return {
      success: false,
      reconciled: false,
      duplicatePrevented: false,
      wahaSent: true,
      error: sendError || "WAHA result is uncertain; reconciliation required",
    };
  }

  // 6. Explicitly reconcile SENT before fenced finalization.
  const reconciled = await reconcileDeliveryAttempt(supabase, {
    attemptId,
    claimToken,
    resolution: "CONFIRMED_SENT",
    providerMessageId: extractedWahaId,
    providerResponse,
  });

  if (reconciled.error || !extractedWahaId) {
    return {
      success: false,
      wahaSent: true,
      reconciled: false,
      duplicatePrevented: false,
      wahaMessageId: extractedWahaId,
      error: reconciled.error || "Provider message ID is missing",
    };
  }

  const finalizeRes = await finalizeConfirmedSend(extractedWahaId);

  return {
    success: finalizeRes.success,
    reconciled: finalizeRes.success,
    duplicatePrevented: false,
    wahaSent: true,
    wahaMessageId: extractedWahaId,
    error: finalizeRes.error,
  };
}

// ----------------------------------------------------------------------------
// Worker Handler
// ----------------------------------------------------------------------------

async function finalizeConfirmedSentReconciliations(
  supabase: any,
): Promise<{ finalized: number; errors: string[] }> {
  const { data: attempts, error: attemptsError } = await supabase
    .from("notification_delivery_attempts")
    .select(
      "attempt_id, claim_token, event_type, event_id, teacher_id, student_id, provider_message_id",
    )
    .eq("provider_status", "SENT")
    .eq("reconciliation_status", "CONFIRMED_SENT")
    .limit(50);

  if (attemptsError) {
    return { finalized: 0, errors: [attemptsError.message] };
  }

  const eventTableByType = {
    ABSENCE: "notification_events",
    HOMEWORK: "homework_notification_events",
    RECITATION: "recitation_notification_events",
    EXAM: "exam_notification_events",
    MONTHLY_REPORT: "monthly_report_notification_events",
  } as const;

  const sourceColumnByType = {
    ABSENCE: "attendance_id",
    HOMEWORK: "homework_id",
    RECITATION: "recitation_id",
    EXAM: "exam_id",
    MONTHLY_REPORT: "monthly_report_id",
  } as const;

  let finalized = 0;
  const errors: string[] = [];

  for (const attempt of attempts || []) {
    const eventType = attempt.event_type as keyof typeof eventTableByType;
    const tableName = eventTableByType[eventType];
    const sourceColumn = sourceColumnByType[eventType];

    if (!tableName || !sourceColumn || !attempt.provider_message_id) {
      continue;
    }

    const { data: event, error: eventError } = await supabase
      .from(tableName)
      .select(`id, teacher_id, student_id, claim_token, ${sourceColumn}`)
      .eq("id", attempt.event_id)
      .eq("status", "FAILED")
      .maybeSingle();

    if (eventError) {
      errors.push(`${attempt.attempt_id}: ${eventError.message}`);
      continue;
    }

    if (!event || event.claim_token !== attempt.claim_token) {
      continue;
    }

    const sourceId = event[sourceColumn];
    if (
      event.teacher_id !== attempt.teacher_id ||
      event.student_id !== attempt.student_id ||
      !sourceId
    ) {
      errors.push(`${attempt.attempt_id}: event identity mismatch`);
      continue;
    }

    const result = await finalizeNotificationSentV2(supabase, {
      attemptId: attempt.attempt_id,
      claimToken: attempt.claim_token,
      eventId: event.id,
      eventType,
      teacherId: event.teacher_id,
      studentId: event.student_id,
      sourceId,
      channel: "WHATSAPP",
      wahaMessageId: attempt.provider_message_id,
    });

    if (result.success) {
      finalized += 1;
    } else if (result.error) {
      errors.push(`${attempt.attempt_id}: ${result.error}`);
    }
  }

  return { finalized, errors };
}

export async function runWorkerCycle(config: WorkerConfig) {
  const supabase = createClient(config.supabaseUrl, config.serviceRoleKey, {
    auth: { persistSession: false },
  });

  const results: Record<string, any> = {};
  const eventTypes = ["ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT"] as const;

  // Finalize already-confirmed provider sends without calling WAHA.
  const confirmedReconciliations = await finalizeConfirmedSentReconciliations(supabase);
  results.confirmedReconciliations = confirmedReconciliations;

  // 1. Recover stale processing events first using core RPC with explicit named parameters
  results.recovery = {};
  for (const type of eventTypes) {
    try {
      const { data, error } = await supabase.rpc("recover_notification_event_v2_core", {
        p_event_type: type,
        p_timeout_minutes: 5,
      });
      if (error) {
        results.recovery[type] = { success: false, error: error.message };
      } else {
        results.recovery[type] = { success: true, recoveredCount: data ?? 0 };
      }
    } catch (err: any) {
      results.recovery[type] = { success: false, error: err?.message || String(err) };
    }
  }

  // 2. Claim and process each event type
  for (const type of eventTypes) {
    const claimRpcByType = {
      ABSENCE: "claim_notification_event_v2",
      HOMEWORK: "claim_homework_notification_event_v2",
      RECITATION: "claim_recitation_notification_event_v2",
      EXAM: "claim_exam_notification_event_v2",
      MONTHLY_REPORT: "claim_monthly_report_notification_event_v2",
    } as const;

    try {
      const { data, error } = await supabase.rpc(claimRpcByType[type]);
      if (error) {
        results[type] = { error: `Claim error: ${error.message}` };
        continue;
      }

      if (!data) {
        results[type] = { claimedCount: 0, processed: [] };
        continue;
      }

      const claimedEvent = data.event;
      if (
        !claimedEvent ||
        !data.attempt_id ||
        !data.claim_token ||
        !data.logical_notification_key ||
        !data.provider_idempotency_key
      ) {
        results[type] = { error: "Claim response missing durable lease data" };
        continue;
      }

      const outcome = await processNotificationEvent({
        supabase,
        wahaBaseUrl: config.wahaBaseUrl,
        wahaApiKey: config.wahaApiKey,
        eventType: type,
        event: claimedEvent,
        attemptId: data.attempt_id,
        claimToken: data.claim_token,
        logicalNotificationKey: data.logical_notification_key,
        providerIdempotencyKey: data.provider_idempotency_key,
      });

      results[type] = {
        claimedCount: 1,
        processed: [{ id: claimedEvent.id, outcome }],
      };
    } catch (e: any) {
      results[type] = { error: `Claim error: ${e.message}` };
    }
  }

  return results;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { status: 200, headers: corsHeaders });
  }

  if ((Deno.env.get("NOTIFICATION_WORKER_DISABLED") || "").toLowerCase() === "true") {
    return new Response(
      JSON.stringify({
        ok: false,
        disabled: true,
        reason: "notification worker temporarily disabled",
      }),
      {
        status: 503,
        headers: corsHeaders,
      },
    );
  }

  const workerSecret = Deno.env.get("WORKER_SECRET") || "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const headerSecret = req.headers.get("x-worker-secret");
  const authHeader = req.headers.get("authorization") || "";

  const isAuthorized =
    (workerSecret.length > 0 && headerSecret === workerSecret) ||
    (serviceRoleKey.length > 0 && authHeader === `Bearer ${serviceRoleKey}`);

  if (!isAuthorized) {
    return new Response(JSON.stringify({ ok: false, error: "Unauthorized" }), {
      status: 401,
      headers: corsHeaders,
    });
  }

  const config: WorkerConfig = {
    supabaseUrl: Deno.env.get("SUPABASE_URL") || "",
    serviceRoleKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "",
    workerSecret,
    wahaBaseUrl: Deno.env.get("WAHA_BASE_URL") || "",
    wahaApiKey: Deno.env.get("WAHA_API_KEY") || "",
  };

  try {
    const summary = await runWorkerCycle(config);
    return new Response(JSON.stringify({ ok: true, summary }), {
      status: 200,
      headers: corsHeaders,
    });
  } catch (err: any) {
    return new Response(JSON.stringify({ ok: false, error: err.message || String(err) }), {
      status: 500,
      headers: corsHeaders,
    });
  }
});

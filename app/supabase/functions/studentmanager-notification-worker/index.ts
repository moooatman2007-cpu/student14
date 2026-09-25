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

export async function checkNotificationAlreadySent(
  supabase: any,
  params: {
    teacherId: string;
    studentId: string;
    sourceId: string;
    notificationType: string;
    channel?: string;
  }
): Promise<{ alreadySent: boolean; wahaMessageId: string | null }> {
  const channel = params.channel || "WHATSAPP";

  const { data: rows, error: qErr } = await supabase
    .from("notifications")
    .select("id, status, waha_message_id")
    .eq("teacher_id", params.teacherId)
    .eq("student_id", params.studentId)
    .eq("source_id", params.sourceId)
    .eq("notification_type", params.notificationType)
    .eq("channel", channel)
    .eq("status", "SENT")
    .limit(1);

  if (!qErr && rows && rows.length > 0) {
    return { alreadySent: true, wahaMessageId: rows[0].waha_message_id || null };
  }

  // Fallback to RPC function
  const { data } = await supabase.rpc("check_notification_already_sent", {
    p_teacher_id: params.teacherId,
    p_student_id: params.studentId,
    p_source_id: params.sourceId,
    p_notification_type: params.notificationType,
    p_channel: channel,
  });

  return { alreadySent: data === true, wahaMessageId: null };
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
    const url = `${wahaBaseUrl}/api/${sessionName}/chats/${chatId}/messages?limit=20`;
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

      // 2. Strict Bounded Time Window: must fall within the attempt timeframe
      if (typeof msg.timestamp === "number") {
        if (msg.timestamp < minTimestampSec || msg.timestamp > maxTimestampSec) {
          continue;
        }
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

export async function finalizeNotificationSent(
  supabase: any,
  params: {
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
  const { data, error } = await supabase.rpc("finalize_notification_sent", {
    p_event_id: params.eventId,
    p_event_type: params.eventType,
    p_teacher_id: params.teacherId,
    p_student_id: params.studentId,
    p_source_id: params.sourceId,
    p_channel: channel,
    p_waha_message_id: params.wahaMessageId || null,
  });

  if (error) {
    return { success: false, error: error.message };
  }

  return { success: true };
}

export async function processNotificationEvent(ctx: ProcessEventContext): Promise<{
  success: boolean;
  reconciled: boolean;
  duplicatePrevented: boolean;
  wahaMessageId?: string | null;
  wahaSent?: boolean;
  error?: string;
}> {
  const { supabase, wahaBaseUrl, wahaApiKey, eventType, event } = ctx;
  const teacherId = event.teacher_id;
  const studentId = event.student_id;

  let sourceId = "";
  let notifTypeStr = "";

  switch (eventType) {
    case "ABSENCE":
      sourceId = event.attendance_id;
      notifTypeStr = "ATTENDANCE_ABSENT";
      break;
    case "HOMEWORK":
      sourceId = event.homework_id;
      notifTypeStr = "HOMEWORK";
      break;
    case "RECITATION":
      sourceId = event.recitation_id;
      notifTypeStr = "RECITATION";
      break;
    case "EXAM":
      sourceId = event.exam_id;
      notifTypeStr = "EXAM";
      break;
    case "MONTHLY_REPORT":
      sourceId = event.monthly_report_id;
      notifTypeStr = "MONTHLY_REPORT";
      break;
  }

  // 1. Strict Tenant Isolation Check: verify student belongs to teacher
  const { data: student, error: studentErr } = await supabase
    .from("students")
    .select("id, full_name, parent_phone, has_whatsapp, teacher_id, deleted_at")
    .eq("id", studentId)
    .eq("teacher_id", teacherId)
    .maybeSingle();

  if (studentErr || !student || student.deleted_at != null) {
    await markEventFailed(supabase, eventType, event.id, "Student not found, deleted, or tenant mismatch");
    return { success: false, reconciled: false, duplicatePrevented: false, error: "Tenant mismatch or student not found" };
  }

  if (!student.has_whatsapp || !student.parent_phone) {
    await markEventFailed(supabase, eventType, event.id, "Student has WhatsApp disabled or no parent phone");
    return { success: false, reconciled: false, duplicatePrevented: false, error: "WhatsApp disabled" };
  }

  // 2. Check Teacher WhatsApp Session
  const { data: sessionData } = await supabase
    .from("teacher_whatsapp_sessions")
    .select("status, connected_phone")
    .eq("teacher_id", teacherId)
    .maybeSingle();

  const sessionStatus = sessionData?.status;
  if (sessionStatus !== "WORKING" && sessionStatus !== "CONNECTED") {
    await markEventFailed(supabase, eventType, event.id, `WhatsApp session not active (status: ${sessionStatus || "NONE"})`);
    return { success: false, reconciled: false, duplicatePrevented: false, error: "WhatsApp session not active" };
  }

  const sessionName = `teacher_${teacherId}`;
  const rawPhone = formatParentPhone(student.parent_phone);
  if (rawPhone.length < 8) {
    await markEventFailed(supabase, eventType, event.id, `Invalid parent phone number: ${student.parent_phone}`);
    return { success: false, reconciled: false, duplicatePrevented: false, error: "Invalid phone number" };
  }
  const chatId = `${rawPhone}@c.us`;

  // 3. Construct deterministic exact message text BEFORE reconciliation and sendText
  const messageText = constructNotificationMessage(eventType, student.full_name);

  // 4. Pre-Send Idempotency Check (Database-backed)
  const dbCheck = await checkNotificationAlreadySent(supabase, {
    teacherId,
    studentId,
    sourceId,
    notificationType: notifTypeStr,
    channel: "WHATSAPP",
  });

  if (dbCheck.alreadySent) {
    console.log(`[Idempotency] Message already recorded SENT for source ${sourceId}`);
    await finalizeNotificationSent(supabase, {
      eventId: event.id,
      eventType,
      teacherId,
      studentId,
      sourceId,
      wahaMessageId: dbCheck.wahaMessageId,
    });
    return { success: true, reconciled: true, duplicatePrevented: true, wahaMessageId: dbCheck.wahaMessageId };
  }

  // 5. Retry Reconciliation against WAHA (when attempts > 1)
  // Bounded time window: from start of previous processing attempt (or creation) to now + 60s
  if (event.attempts > 1) {
    const refTime = event.processing_started_at || event.created_at || new Date().toISOString();
    const minTimestampSec = Math.floor(new Date(refTime).getTime() / 1000) - 60;
    const maxTimestampSec = Math.floor(Date.now() / 1000) + 60;

    const recon = await checkWahaRecentMessageSent(
      wahaBaseUrl,
      wahaApiKey,
      sessionName,
      chatId,
      messageText,
      minTimestampSec,
      maxTimestampSec
    );

    if (recon.found) {
      console.log(`[Reconciliation] WAHA history confirms previous send delivered (wahaId: ${recon.wahaMessageId}). Finalizing without resending.`);
      await finalizeNotificationSent(supabase, {
        eventId: event.id,
        eventType,
        teacherId,
        studentId,
        sourceId,
        wahaMessageId: recon.wahaMessageId,
      });
      return { success: true, reconciled: true, duplicatePrevented: true, wahaMessageId: recon.wahaMessageId };
    }
  }

  // 6. External Call to WAHA /api/sendText
  const wahaUrl = `${wahaBaseUrl}/api/sendText`;
  let sendSuccess = false;
  let sendError = "";
  let extractedWahaId: string | null = null;

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
    });

    if (wahaRes.ok) {
      sendSuccess = true;
      try {
        const resJson = await wahaRes.json();
        extractedWahaId = extractWahaMessageId(resJson);
      } catch (_) {}
    } else {
      const errText = await wahaRes.text();
      sendError = `WAHA send failed (${wahaRes.status}): ${errText}`;
    }
  } catch (err: any) {
    sendError = `WAHA network failure: ${err.message || String(err)}`;
  }

  if (!sendSuccess) {
    // WAHA failed before sending: safely mark failed for normal retry
    await markEventFailed(supabase, eventType, event.id, sendError);
    return { success: false, reconciled: false, duplicatePrevented: false, error: sendError };
  }

  // 7. Atomic Finalize in PostgreSQL (Marks event SENT + records history + waha_message_id)
  const finalizeRes = await finalizeNotificationSent(supabase, {
    eventId: event.id,
    eventType,
    teacherId,
    studentId,
    sourceId,
    wahaMessageId: extractedWahaId,
  });

  if (!finalizeRes.success) {
    // CRITICAL: WAHA succeeded, but DB finalize failed (e.g. DB connection dropped/timeout).
    // DO NOT call markEventFailed which would cause a blind resend!
    // Leave the event in its current state so the next cycle reconciles against WAHA history.
    console.error(`[Finalize Warning] Finalize failed after successful WAHA send (wahaId: ${extractedWahaId}): ${finalizeRes.error}`);
    return {
      success: false,
      wahaSent: true,
      reconciled: false,
      duplicatePrevented: false,
      wahaMessageId: extractedWahaId,
      error: finalizeRes.error,
    };
  }

  return { success: true, reconciled: false, duplicatePrevented: false, wahaMessageId: extractedWahaId };
}

async function markEventFailed(
  supabase: any,
  eventType: string,
  eventId: string,
  errorMsg: string
) {
  let rpcName = "mark_notification_failed";
  switch (eventType) {
    case "HOMEWORK": rpcName = "mark_homework_notification_failed"; break;
    case "RECITATION": rpcName = "mark_recitation_notification_failed"; break;
    case "EXAM": rpcName = "mark_exam_notification_failed"; break;
    case "MONTHLY_REPORT": rpcName = "mark_monthly_report_notification_failed"; break;
  }

  await supabase.rpc(rpcName, {
    p_event_id: eventId,
    p_error: errorMsg,
  });
}

// ----------------------------------------------------------------------------
// Worker Handler
// ----------------------------------------------------------------------------

export async function runWorkerCycle(config: WorkerConfig) {
  const supabase = createClient(config.supabaseUrl, config.serviceRoleKey, {
    auth: { persistSession: false },
  });

  const results: Record<string, any> = {};
  const eventTypes = ["ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT"] as const;

  // 1. Recover stale processing events first
  for (const type of eventTypes) {
    let recoverRpc = "recover_stale_notification_events";
    if (type === "HOMEWORK") recoverRpc = "recover_stale_homework_notification_events";
    if (type === "RECITATION") recoverRpc = "recover_stale_recitation_notification_events";
    if (type === "EXAM") recoverRpc = "recover_stale_exam_notification_events";
    if (type === "MONTHLY_REPORT") recoverRpc = "recover_stale_monthly_report_notification_events";

    try {
      await supabase.rpc(recoverRpc, { p_timeout_minutes: 5 });
    } catch (_) {}
  }

  // 2. Claim and process each event type
  for (const type of eventTypes) {
    let claimRpc = "claim_notification_event";
    if (type === "HOMEWORK") claimRpc = "claim_homework_notification_event";
    if (type === "RECITATION") claimRpc = "claim_recitation_notification_event";
    if (type === "EXAM") claimRpc = "claim_exam_notification_event";
    if (type === "MONTHLY_REPORT") claimRpc = "claim_monthly_report_notification_event";

    let claimedList: any[] = [];
    try {
      const { data } = await supabase.rpc(claimRpc);
      if (Array.isArray(data)) {
        claimedList = data;
      }
    } catch (e: any) {
      results[type] = { error: `Claim error: ${e.message}` };
      continue;
    }

    const processedList = [];
    for (const event of claimedList) {
      const outcome = await processNotificationEvent({
        supabase,
        wahaBaseUrl: config.wahaBaseUrl,
        wahaApiKey: config.wahaApiKey,
        eventType: type,
        event,
      });
      processedList.push({ id: event.id, outcome });
    }

    results[type] = { claimedCount: claimedList.length, processed: processedList };
  }

  // 3. Monitoring call
  try {
    await supabase.rpc("monitor_failed_notification_events");
  } catch (_) {}

  return results;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { status: 200, headers: corsHeaders });
  }

  const workerSecret = Deno.env.get("WORKER_SECRET") || "";
  const headerSecret = req.headers.get("x-worker-secret");
  const authHeader = req.headers.get("authorization") || "";

  const isAuthorized =
    (workerSecret && headerSecret === workerSecret) ||
    authHeader.includes(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "never-match-blank");

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

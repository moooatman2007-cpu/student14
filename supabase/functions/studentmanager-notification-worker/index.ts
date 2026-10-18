import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-worker-secret, x-cron-secret",
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
  scopedTeacherId?: string;
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
  studentName: string,
  extraDetails?: {
    title?: string | null;
    score?: number | string | null;
    maxScore?: number | string | null;
    subject?: string | null;
    examName?: string | null;
  }
): string {
  switch (eventType) {
    case "ABSENCE":
      return `مدار | إشعار غياب ⚠️\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إحاطة سيادتكم بأن الطالب/ة: *${studentName}*\nتم تسجيل غيابه/ا عن حصة اليوم.\n\nنرجو متابعة انتظام الطالب حرصاً على مستواه التعليمي.\n\nشكرًا لحسن تعاونكم ❤️\nمدار — منصة إدارة ومتابعة الطلاب`;
    case "HOMEWORK":
      return `مدار | متابعة الواجب ✍️\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بخصوص الواجب المدرسي للطالب/ة: *${studentName}*\nتم تسجيل ومتابعة حالة الواجب في حصة اليوم.\n\nمع تمنياتنا بالتفوق المستمر 🌟\nمدار — منصة إدارة ومتابعة الطلاب`;
    case "RECITATION": {
      let recitationInfo = "";
      if (extraDetails?.title && extraDetails?.score != null) {
        recitationInfo = `\n📖 التسميع: *${extraDetails.title}*\n🎯 الدرجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 10}*\n`;
      } else if (extraDetails?.title) {
        recitationInfo = `\n📖 التسميع: *${extraDetails.title}*\n`;
      } else if (extraDetails?.score != null) {
        recitationInfo = `\n🎯 الدرجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 10}*\n`;
      }
      return `مدار | تقييم التسميع 📖\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بنتيجة تسميع القرآن الكريم للطالب/ة: *${studentName}*${recitationInfo ? `\n${recitationInfo}` : "\nتم رصد وتوثيق التسميع في سجل المتابعة بنجاح.\n"}\nبارك الله فيه ووفقه دائماً 🌟\nمدار — منصة إدارة ومتابعة الطلاب`;
    }
    case "EXAM": {
      let examInfo = "";
      const examNameStr = extraDetails?.examName || extraDetails?.title;
      if (examNameStr && extraDetails?.score != null) {
        examInfo = `\n📝 الاختبار: *${examNameStr}*${extraDetails.subject ? ` (${extraDetails.subject})` : ""}\n🎯 النتيجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 100}*\n`;
      } else if (examNameStr) {
        examInfo = `\n📝 الاختبار: *${examNameStr}*${extraDetails.subject ? ` (${extraDetails.subject})` : ""}\n`;
      } else if (extraDetails?.score != null) {
        examInfo = `\n🎯 النتيجة: *${extraDetails.score}* من *${extraDetails.maxScore ?? 100}*\n`;
      }
      return `مدار | نتيجة الاختبار 📝\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بنتيجة الاختبار للطالب/ة: *${studentName}*${examInfo ? `\n${examInfo}` : "\nتم رصد نتيجة الاختبار في سجل أداء الطالب.\n"}\nمع تمنياتنا بمزيد من النجاح والتميز 🎯\nمدار — منصة إدارة ومتابعة الطلاب`;
    }
    case "MONTHLY_REPORT":
      return `مدار | التقرير الشهري 📊\n\nالسلام عليكم ورحمة الله وبركاته 🌷\n\nنود إعلامكم بصدور التقرير الشهري الشامل للطالب/ة: *${studentName}*.\nيمكنكم مراجعة المعلم أو الاطلاع على التقرير التفصيلي.\n\nشاكرين اهتمامكم المستمر ❤️\nمدار — منصة إدارة ومتابعة الطلاب`;
  }
}

export interface ResolveRecipientResult {
  chatId: string | null;
  error?: string;
  lid?: string | null;
  pn?: string | null;
}

export async function resolveWahaRecipient(
  wahaBaseUrl: string,
  wahaApiKey: string,
  sessionName: string,
  rawPhone: string
): Promise<ResolveRecipientResult> {
  const formattedPhone = formatParentPhone(rawPhone);
  if (!formattedPhone || formattedPhone.length < 8) {
    return { chatId: null, error: `Invalid parent phone number: "${rawPhone}"` };
  }

  const sessionEncoded = encodeURIComponent(sessionName);
  const phoneEncoded = encodeURIComponent(formattedPhone);
  const headers: Record<string, string> = {
    Accept: "application/json",
  };
  if (wahaApiKey) {
    headers["X-Api-Key"] = wahaApiKey;
  }

  // 1. Primary Check: Official WAHA LID lookup endpoint GET /api/{session}/lids/pn/{phone}
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 6000);
    const lidRes = await fetch(
      `${wahaBaseUrl}/api/${sessionEncoded}/lids/pn/${phoneEncoded}`,
      {
        method: "GET",
        headers,
        signal: controller.signal,
      }
    );
    clearTimeout(timer);

    if (lidRes.ok) {
      const lidData = await lidRes.json();
      if (lidData && typeof lidData.lid === "string" && lidData.lid.trim().length > 0) {
        return {
          chatId: lidData.lid.trim(),
          lid: lidData.lid.trim(),
          pn: lidData.pn || `${formattedPhone}@c.us`,
        };
      }
    }
  } catch (_err) {
    // Continue to check-exists fallback
  }

  // 2. Secondary Check: WAHA check-exists endpoint GET /api/contacts/check-exists?phone={phone}&session={session}
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 6000);
    const checkRes = await fetch(
      `${wahaBaseUrl}/api/contacts/check-exists?phone=${phoneEncoded}&session=${sessionEncoded}`,
      {
        method: "GET",
        headers,
        signal: controller.signal,
      }
    );
    clearTimeout(timer);

    if (checkRes.ok) {
      const checkData = await checkRes.json();
      if (checkData && checkData.numberExists) {
        let extractedId: string | null = null;
        if (typeof checkData.id === "string" && checkData.id.trim().length > 0) {
          extractedId = checkData.id.trim();
        } else if (checkData.id && typeof checkData.id === "object") {
          if (typeof checkData.id._serialized === "string" && checkData.id._serialized.trim().length > 0) {
            extractedId = checkData.id._serialized.trim();
          }
        }
        if (extractedId && (extractedId.endsWith("@lid") || extractedId.endsWith("@c.us"))) {
          return { chatId: extractedId };
        }
      }
    }
  } catch (_err) {
    // Ignore error
  }

  // 3. Explicit Failure Handling when LID cannot be resolved for WEBJS:
  // Do NOT blindly send @c.us if LID resolution returned null/failed for WEBJS,
  // as sending directly to @c.us without a LID causes WAHA 500 "No LID for user".
  return {
    chatId: null,
    error: `WAHA recipient resolution failed: No LID found for phone number ${formattedPhone} in session ${sessionName} (WEBJS).`,
  };
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
): Promise<{
  status: "FOUND" | "NOT_FOUND" | "CHECK_FAILED";
  wahaMessageId: string | null;
  error?: string;
}> {
  try {
    const url = `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/chats/${encodeURIComponent(chatId)}/messages?limit=20`;
    const res = await fetch(url, {
      method: "GET",
      headers: {
        "X-Api-Key": wahaApiKey,
        "Accept": "application/json",
      },
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => "");
      return { status: "CHECK_FAILED", wahaMessageId: null, error: `HTTP ${res.status}: ${errText}` };
    }

    let messages: any;
    try {
      messages = await res.json();
    } catch (parseErr: any) {
      return { status: "CHECK_FAILED", wahaMessageId: null, error: `JSON parse error: ${parseErr?.message || String(parseErr)}` };
    }

    if (!Array.isArray(messages)) {
      return { status: "CHECK_FAILED", wahaMessageId: null, error: "Response is not an array of messages" };
    }

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
        if (id) {
          return { status: "FOUND", wahaMessageId: id };
        }
      }
    }

    return { status: "NOT_FOUND", wahaMessageId: null };
  } catch (err: any) {
    return { status: "CHECK_FAILED", wahaMessageId: null, error: err?.message || String(err) };
  }
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

  // 0. Boundary Verification: ensure event belongs to authorized caller if scoped
  if (ctx.scopedTeacherId && teacherId !== ctx.scopedTeacherId) {
    await recordFailed("Tenant boundary violation: event does not belong to authorized teacher");
    return {
      success: false,
      reconciled: false,
      duplicatePrevented: false,
      error: "Tenant boundary violation: unauthorized teacher access",
    };
  }

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

  // Resolve recipient chatId:
  // 1. Primary lookup: Check public.teacher_whatsapp_contacts
  let chatId: string | null = null;
  try {
    const { data: contactData, error: contactError } = await supabase
      .from("teacher_whatsapp_contacts")
      .select("chat_id")
      .eq("teacher_id", teacherId)
      .eq("phone_e164", rawPhone)
      .maybeSingle();

    if (contactError) {
      console.warn(
        `Notice: teacher_whatsapp_contacts lookup failed for teacher ${teacherId}: ${contactError.message}`
      );
    } else if (
      contactData &&
      typeof contactData.chat_id === "string" &&
      contactData.chat_id.trim().length > 0
    ) {
      chatId = contactData.chat_id.trim();
    }
  } catch (err) {
    console.warn(
      `Notice: Exception during teacher_whatsapp_contacts lookup for teacher ${teacherId}:`,
      err instanceof Error ? err.message : String(err)
    );
  }

  // 2. Fallback: Resolve recipient chatId (LID or @c.us) via WAHA APIs if not found in contacts
  if (!chatId) {
    const recipientResolution = await resolveWahaRecipient(
      wahaBaseUrl,
      wahaApiKey,
      sessionName,
      student.parent_phone
    );

    if (!recipientResolution.chatId) {
      const resolutionError =
        recipientResolution.error ||
        `WAHA recipient resolution failed for ${student.parent_phone}`;
      await recordFailed(resolutionError);
      return {
        success: false,
        reconciled: false,
        duplicatePrevented: false,
        error: resolutionError,
      };
    }

    chatId = recipientResolution.chatId;
  }

  // 3. Construct deterministic exact message text BEFORE reconciliation and sendText
  let extraDetails: {
    title?: string | null;
    score?: number | string | null;
    maxScore?: number | string | null;
    subject?: string | null;
    examName?: string | null;
  } | undefined = undefined;

  if (eventType === "RECITATION" && sourceId) {
    try {
      const { data: recData } = await supabase
        .from("recitations")
        .select("title, score, max_score")
        .eq("id", sourceId)
        .eq("teacher_id", teacherId)
        .maybeSingle();
      if (recData) {
        extraDetails = {
          title: recData.title,
          score: recData.score,
          maxScore: recData.max_score,
        };
      }
    } catch (_err) {
      // Graceful fallback to default recitation template
    }
  } else if (eventType === "EXAM" && sourceId) {
    try {
      const { data: exData } = await supabase
        .from("exams")
        .select("exam_name, subject, score, max_score")
        .eq("id", sourceId)
        .eq("teacher_id", teacherId)
        .maybeSingle();
      if (exData) {
        extraDetails = {
          title: exData.exam_name,
          examName: exData.exam_name,
          subject: exData.subject,
          score: exData.score,
          maxScore: exData.max_score,
        };
      }
    } catch (_err) {
      // Graceful fallback to default exam template
    }
  }

  const messageText = constructNotificationMessage(eventType, student.full_name, extraDetails);

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

    if (recon.status === "FOUND" && recon.wahaMessageId) {
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
    } else if (recon.status === "CHECK_FAILED") {
      return {
        success: false,
        reconciled: false,
        duplicatePrevented: false,
        wahaSent: true,
        error: `WAHA check failed (${recon.error || "network/HTTP error"}); attempt remains uncertain for later reconciliation.`,
      };
    } else {
      return {
        success: false,
        reconciled: false,
        duplicatePrevented: false,
        wahaSent: true,
        error: sendError || "WAHA result is uncertain; message not found in recent probe, reconciliation required",
      };
    }
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

export const EVENT_TABLE_BY_TYPE = {
  ABSENCE: "notification_events",
  HOMEWORK: "homework_notification_events",
  RECITATION: "recitation_notification_events",
  EXAM: "exam_notification_events",
  MONTHLY_REPORT: "monthly_report_notification_events",
} as const;

export const SOURCE_COLUMN_BY_TYPE = {
  ABSENCE: "attendance_id",
  HOMEWORK: "homework_id",
  RECITATION: "recitation_id",
  EXAM: "exam_id",
  MONTHLY_REPORT: "monthly_report_id",
} as const;

// ----------------------------------------------------------------------------
// Worker Handler
// ----------------------------------------------------------------------------

async function finalizeConfirmedSentReconciliations(
  supabase: any,
  scopedTeacherId?: string,
): Promise<{ finalized: number; errors: string[] }> {
  let query = supabase
    .from("notification_delivery_attempts")
    .select(
      "attempt_id, claim_token, event_type, event_id, teacher_id, student_id, provider_message_id",
    )
    .eq("provider_status", "SENT")
    .eq("reconciliation_status", "CONFIRMED_SENT");

  if (scopedTeacherId) {
    query = query.eq("teacher_id", scopedTeacherId);
  }

  const { data: attempts, error: attemptsError } = await query.limit(50);

  if (attemptsError) {
    return { finalized: 0, errors: [attemptsError.message] };
  }

  const eventTableByType = EVENT_TABLE_BY_TYPE;
  const sourceColumnByType = SOURCE_COLUMN_BY_TYPE;

  let finalized = 0;
  const errors: string[] = [];

  for (const attempt of attempts || []) {
    if (scopedTeacherId && attempt.teacher_id !== scopedTeacherId) {
      continue;
    }

    const eventType = attempt.event_type as keyof typeof eventTableByType;
    const tableName = eventTableByType[eventType];
    const sourceColumn = sourceColumnByType[eventType];

    if (!tableName || !sourceColumn || !attempt.provider_message_id) {
      continue;
    }

    let eventQuery = supabase
      .from(tableName)
      .select(`id, teacher_id, student_id, claim_token, ${sourceColumn}`)
      .eq("id", attempt.event_id)
      .eq("status", "FAILED");

    if (scopedTeacherId) {
      eventQuery = eventQuery.eq("teacher_id", scopedTeacherId);
    }

    const { data: event, error: eventError } = await eventQuery.maybeSingle();

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
      (scopedTeacherId && event.teacher_id !== scopedTeacherId) ||
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

export function constantTimeCompare(a: string, b: string): boolean {
  if (typeof a !== "string" || typeof b !== "string" || a.length === 0 || b.length === 0) {
    return false;
  }
  const aBytes = new TextEncoder().encode(a);
  const bBytes = new TextEncoder().encode(b);
  if (aBytes.length !== bBytes.length) {
    return false;
  }
  let mismatch = 0;
  for (let i = 0; i < aBytes.length; i++) {
    mismatch |= aBytes[i] ^ bBytes[i];
  }
  return mismatch === 0;
}

export interface AuthResult {
  isAuthorized: boolean;
  mode?: "GLOBAL_WORKER" | "SCOPED_TEACHER";
  teacherId?: string;
  statusCode?: number;
  error?: string;
}

export async function evaluateRequestAuth(
  req: Request,
  env: {
    cronSecret: string;
    workerSecret: string;
    supabaseUrl: string;
    supabaseAnonKey: string;
    serviceRoleKey: string;
  },
  adminSupabase: any,
): Promise<AuthResult> {
  const authHeader = req.headers.get("authorization") || "";
  const apiKeyHeader = req.headers.get("apikey") || "";
  const headerWorkerSecret = req.headers.get("x-worker-secret") || "";
  const headerCronSecret = req.headers.get("x-cron-secret") || "";

  const bearerToken = authHeader.toLowerCase().startsWith("bearer ")
    ? authHeader.slice(7).trim()
    : "";

  // 1. Backend / Cron / Scheduled Worker Path (Global Queue Access)
  // MUST have a dedicated, strong secret configured (CRON_SECRET or WORKER_SECRET)
  // Constant-time comparison protects against timing attacks.
  // NEVER accept SUPABASE_SERVICE_ROLE_KEY as a client-supplied shared secret!
  const configuredSecrets = [env.cronSecret, env.workerSecret].filter(
    (s) => s.trim().length > 0,
  );

  if (configuredSecrets.length > 0) {
    const incomingTokens = [
      bearerToken,
      headerWorkerSecret,
      headerCronSecret,
      apiKeyHeader,
    ].filter((t) => t.trim().length > 0);

    const isGlobalAuthorized = configuredSecrets.some((secret) =>
      incomingTokens.some((token) => constantTimeCompare(secret, token)),
    );

    if (isGlobalAuthorized) {
      return {
        isAuthorized: true,
        mode: "GLOBAL_WORKER",
      };
    }
  }

  // 2. Authenticated User (Teacher) Path from Client / Android App
  // Must provide a valid Supabase Auth User JWT
  if (bearerToken.length > 0) {
    try {
      const userClient = createClient(env.supabaseUrl, env.supabaseAnonKey || env.serviceRoleKey, {
        global: { headers: { Authorization: `Bearer ${bearerToken}` } },
      });
      const {
        data: { user },
        error: userError,
      } = await userClient.auth.getUser();

      if (userError || !user || !user.id) {
        return {
          isAuthorized: false,
          statusCode: 401,
          error: "Invalid or expired user session token",
        };
      }

      // CRITICAL SECURITY ENFORCEMENT:
      // Verify that user.id is a genuine, authorized teacher in public.teachers!
      // This MUST be checked directly against the database, NEVER from client-supplied payload.
      const { data: teacher, error: teacherError } = await adminSupabase
        .from("teachers")
        .select("id")
        .eq("id", user.id)
        .maybeSingle();

      if (teacherError) {
        console.error(`Teacher lookup error for user ${user.id}: ${teacherError.message}`);
        return {
          isAuthorized: false,
          statusCode: 500,
          error: "Failed to verify teacher authorization",
        };
      }

      if (!teacher || !teacher.id) {
        // Authenticated user exists in Auth, but is NOT a registered teacher!
        return {
          isAuthorized: false,
          statusCode: 403,
          error: "Forbidden: authenticated user is not registered as a teacher",
        };
      }

      // Teacher is verified. Processing MUST BE SCOPED ONLY to this teacher!
      return {
        isAuthorized: true,
        mode: "SCOPED_TEACHER",
        teacherId: teacher.id,
      };
    } catch (err: any) {
      return {
        isAuthorized: false,
        statusCode: 401,
        error: `Authentication failed: ${err.message || String(err)}`,
      };
    }
  }

  // 3. Unauthenticated request
  return {
    isAuthorized: false,
    statusCode: 401,
    error: "Unauthorized: Missing authentication credentials",
  };
}

export interface WorkerCycleOptions {
  eventId?: string | null;
  eventType?: "ABSENCE" | "HOMEWORK" | "RECITATION" | "EXAM" | "MONTHLY_REPORT" | null;
  dryRun?: boolean;
}

export async function runWorkerCycle(
  config: WorkerConfig,
  scopedTeacherId?: string,
  options?: WorkerCycleOptions
) {
  const supabase = createClient(config.supabaseUrl, config.serviceRoleKey, {
    auth: { persistSession: false },
  });

  const isDryRun = options?.dryRun === true;
  const targetEventId = options?.eventId?.trim() || null;
  const targetEventType = options?.eventType || null;

  const allEventTypes = ["ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT"] as const;
  const eventTypesToProcess = targetEventType ? [targetEventType] : allEventTypes;

  const results: Record<string, any> = {};

  // --------------------------------------------------------------------------
  // Mode 1: Dry-Run (Pure Simulation - ZERO database mutations & ZERO WAHA calls)
  // --------------------------------------------------------------------------
  if (isDryRun) {
    if (targetEventId) {
      const inspectType = targetEventType || "ABSENCE";
      const tableName = EVENT_TABLE_BY_TYPE[inspectType];

      const { data: event, error: eventErr } = await supabase
        .from(tableName)
        .select("*")
        .eq("id", targetEventId)
        .maybeSingle();

      if (eventErr || !event) {
        return {
          dry_run: true,
          success: false,
          error: `Target event ${targetEventId} not found in ${tableName}: ${eventErr?.message || "Record not found"}`,
        };
      }

      if (scopedTeacherId && event.teacher_id !== scopedTeacherId) {
        return {
          dry_run: true,
          success: false,
          error: "Tenant boundary violation: event does not belong to authorized teacher",
        };
      }

      if (event.status !== "PENDING") {
        return {
          dry_run: true,
          success: false,
          error: `Target event is in ${event.status} status, expected PENDING`,
          event_status: event.status,
        };
      }

      if (event.attempts >= 3) {
        return {
          dry_run: true,
          success: false,
          error: `Target event attempts exhausted (${event.attempts})`,
          attempts: event.attempts,
        };
      }

      const { data: student, error: studentErr } = await supabase
        .from("students")
        .select("id, full_name, parent_phone, has_whatsapp, teacher_id, deleted_at")
        .eq("id", event.student_id)
        .eq("teacher_id", event.teacher_id)
        .maybeSingle();

      if (studentErr || !student || student.deleted_at != null) {
        return {
          dry_run: true,
          success: false,
          error: "Student not found, deleted, or tenant mismatch",
        };
      }

      if (!student.has_whatsapp || !student.parent_phone) {
        return {
          dry_run: true,
          success: false,
          error: "Student has WhatsApp disabled or missing parent phone",
        };
      }

      const rawPhone = formatParentPhone(student.parent_phone);
      if (rawPhone.length < 8) {
        return {
          dry_run: true,
          success: false,
          error: `Invalid parent phone format: ${student.parent_phone}`,
        };
      }

      const { data: sessionData } = await supabase
        .from("teacher_whatsapp_sessions")
        .select("session_name, status, connected_phone")
        .eq("teacher_id", event.teacher_id)
        .maybeSingle();

      const sessionName = sessionData?.session_name;
      const sessionStatus = sessionData?.status;
      if (!sessionName || (sessionStatus !== "WORKING" && sessionStatus !== "CONNECTED")) {
        return {
          dry_run: true,
          success: false,
          error: `WhatsApp session unavailable or not active (session: ${sessionName || "NONE"}, status: ${sessionStatus || "NONE"})`,
        };
      }

      let extraDetails: any = undefined;
      let sourceId = "";
      if (inspectType === "RECITATION") sourceId = event.recitation_id;
      if (inspectType === "EXAM") sourceId = event.exam_id;
      if (inspectType === "RECITATION" && sourceId) {
        try {
          const { data: recData } = await supabase
            .from("recitations")
            .select("title, score, max_score")
            .eq("id", sourceId)
            .eq("teacher_id", event.teacher_id)
            .maybeSingle();
          if (recData) {
            extraDetails = { title: recData.title, score: recData.score, maxScore: recData.max_score };
          }
        } catch (_err) {}
      } else if (inspectType === "EXAM" && sourceId) {
        try {
          const { data: exData } = await supabase
            .from("exams")
            .select("exam_name, subject, score, max_score")
            .eq("id", sourceId)
            .eq("teacher_id", event.teacher_id)
            .maybeSingle();
          if (exData) {
            extraDetails = { title: exData.exam_name, examName: exData.exam_name, subject: exData.subject, score: exData.score, maxScore: exData.max_score };
          }
        } catch (_err) {}
      }

      const messagePreview = constructNotificationMessage(inspectType, student.full_name, extraDetails);

      return {
        dry_run: true,
        simulation_passed: true,
        event_id: targetEventId,
        event_type: inspectType,
        status: "READY",
        teacher_id: event.teacher_id,
        student: {
          id: student.id,
          full_name: student.full_name,
          parent_phone_masked: student.parent_phone ? `***-${student.parent_phone.slice(-4)}` : null,
          formatted_phone_masked: rawPhone ? `***-${rawPhone.slice(-4)}` : null,
          has_whatsapp: student.has_whatsapp,
        },
        session: {
          session_name: sessionName,
          status: sessionStatus,
          connected_phone_masked: sessionData?.connected_phone ? `***-${sessionData.connected_phone.slice(-4)}` : null,
        },
        message_preview: messagePreview,
        checks: {
          event_exists: true,
          tenant_match: true,
          status_pending: true,
          attempts_valid: true,
          student_valid: true,
          whatsapp_enabled: true,
          session_active: true,
          phone_valid: true,
        },
        rpc_mutations_executed: false,
        waha_called: false,
      };
    } else {
      // General dry-run without specific event_id
      const dryRunSummary: Record<string, any> = {};
      for (const type of eventTypesToProcess) {
        const tableName = EVENT_TABLE_BY_TYPE[type];
        let query = supabase
          .from(tableName)
          .select("id, status, attempts, created_at, teacher_id")
          .eq("status", "PENDING")
          .lt("attempts", 3);
        if (scopedTeacherId) {
          query = query.eq("teacher_id", scopedTeacherId);
        }
        const { data: pendingItems, error: pErr } = await query.limit(5);
        dryRunSummary[type] = {
          pending_count_preview: pendingItems?.length || 0,
          pending_ids: pendingItems?.map((it: any) => it.id) || [],
          error: pErr?.message || null,
        };
      }
      return {
        dry_run: true,
        simulation_passed: true,
        types_evaluated: eventTypesToProcess,
        summary: dryRunSummary,
        rpc_mutations_executed: false,
        waha_called: false,
      };
    }
  }

  // --------------------------------------------------------------------------
  // Mode 2: Live Processing (Normal or Targeted)
  // --------------------------------------------------------------------------

  // Finalize already-confirmed provider sends without calling WAHA.
  if (!targetEventId) {
    const confirmedReconciliations = await finalizeConfirmedSentReconciliations(supabase, scopedTeacherId);
    results.confirmedReconciliations = confirmedReconciliations;
  }

  // 1. Recover stale processing events
  results.recovery = {};
  if (!targetEventId) {
    for (const type of eventTypesToProcess) {
      if (scopedTeacherId) {
        // Production database currently defines recover_notification_event_v2_core(p_event_type text, p_timeout_minutes integer).
        // Scoped recovery with p_teacher_id is not yet defined on database.
        // We explicitly skip executing un-scoped recovery during scoped teacher invocations to preserve tenant isolation,
        // avoiding sending an unexpected p_teacher_id parameter to PostgREST.
        results.recovery[type] = {
          skipped: true,
          reason: "Scoped recovery requires database migration with p_teacher_id; un-scoped recovery skipped for tenant isolation",
        };
        continue;
      }

      try {
        const rpcParams: Record<string, any> = {
          p_event_type: type,
          p_timeout_minutes: 5,
        };
        let { data, error } = await supabase.rpc("recover_notification_event_v2_core", rpcParams);
        if (error) {
          results.recovery[type] = { success: false, error: error.message };
        } else {
          results.recovery[type] = { success: true, recoveredCount: data ?? 0 };
        }
      } catch (err: any) {
        results.recovery[type] = { success: false, error: err?.message || String(err) };
      }
    }
  }

  // 2. Claim and process event types
  const MAX_ITEMS_PER_TYPE_PER_CYCLE = targetEventId ? 1 : 25;

  // Configurable cycle budget (default 120,000 ms = 2 minutes).
  // Capped at 140,000 ms to guarantee completion comfortably before Supabase's 150s platform ceiling.
  const rawMaxDuration = parseInt(
    Deno.env.get("WORKER_MAX_CYCLE_DURATION_MS") || Deno.env.get("MAX_CYCLE_DURATION_MS") || "120000",
    10
  );
  const MAX_CYCLE_DURATION_MS = !isNaN(rawMaxDuration) && rawMaxDuration > 0
    ? Math.min(rawMaxDuration, 140000)
    : 120000;

  const cycleStartTime = Date.now();

  for (const type of eventTypesToProcess) {
    const claimRpcByType = {
      ABSENCE: "claim_notification_event_v2",
      HOMEWORK: "claim_homework_notification_event_v2",
      RECITATION: "claim_recitation_notification_event_v2",
      EXAM: "claim_exam_notification_event_v2",
      MONTHLY_REPORT: "claim_monthly_report_notification_event_v2",
    } as const;

    results[type] = {
      claimedCount: 0,
      successCount: 0,
      failedCount: 0,
      processed: [] as Array<{ id: string; outcome: any }>,
      capacityLimitReached: false,
      remainingPendingCount: 0,
      hasMorePending: false,
      completed: false,
    };

    // When targeting a specific event, verify it BEFORE claiming
    if (targetEventId) {
      const tableName = EVENT_TABLE_BY_TYPE[type];
      const { data: targetEvent, error: targetErr } = await supabase
        .from(tableName)
        .select("id, status, attempts, created_at, teacher_id")
        .eq("id", targetEventId)
        .maybeSingle();

      if (targetErr || !targetEvent) {
        results[type].error = `Target event ${targetEventId} not found in ${tableName}`;
        break;
      }

      if (scopedTeacherId && targetEvent.teacher_id !== scopedTeacherId) {
        results[type].error = "Tenant boundary violation: event does not belong to authorized teacher";
        break;
      }

      if (targetEvent.status !== "PENDING") {
        results[type].error = `Target event is in ${targetEvent.status} status, cannot claim`;
        break;
      }

      if (targetEvent.attempts >= 3) {
        results[type].error = `Target event attempts exhausted (${targetEvent.attempts})`;
        break;
      }

      // CRITICAL PRE-CLAIM SAFETY GUARD:
      // Verify that targetEventId is the oldest pending event for this teacher that claim RPC will select.
      // If an older pending event exists, general claim RPC would claim the older event first!
      let oldestQuery = supabase
        .from(tableName)
        .select("id")
        .eq("status", "PENDING")
        .lt("attempts", 3);
      if (scopedTeacherId) {
        oldestQuery = oldestQuery.eq("teacher_id", scopedTeacherId);
      }
      const { data: oldestPending } = await oldestQuery
        .order("created_at", { ascending: true })
        .order("id", { ascending: true })
        .limit(1)
        .maybeSingle();

      if (!oldestPending || oldestPending.id !== targetEventId) {
        results[type].error = `Targeted claim aborted safely: next eligible queue item is ${oldestPending?.id || "NONE"}, not ${targetEventId}. Cannot claim target without claiming older items first.`;
        break;
      }
    }

    let itemsProcessedForType = 0;

    while (itemsProcessedForType < MAX_ITEMS_PER_TYPE_PER_CYCLE) {
      const elapsed = Date.now() - cycleStartTime;
      if (elapsed >= MAX_CYCLE_DURATION_MS) {
        console.warn(
          `Worker cycle time budget reached (${elapsed}ms elapsed >= ${MAX_CYCLE_DURATION_MS}ms budget); yielding cycle safely.`
        );
        results[type].capacityLimitReached = true;
        break;
      }

      try {
        let rpcName: string = claimRpcByType[type];
        let rpcParams: Record<string, any> = {};

        if (scopedTeacherId) {
          rpcName = "claim_notification_event_v2_core";
          rpcParams = {
            p_event_type: type,
            p_teacher_id: scopedTeacherId,
          };
        }

        let { data, error } = await supabase.rpc(rpcName, rpcParams);

        if (error && scopedTeacherId && error.message.includes("p_teacher_id")) {
          // Fallback: try wrapper with p_teacher_id
          const wrapperRes = await supabase.rpc(claimRpcByType[type], { p_teacher_id: scopedTeacherId });
          data = wrapperRes.data;
          error = wrapperRes.error;
        }

        console.log(
          `Diagnostic: Type=${type}, HasData=${data != null}, HasError=${error != null}`
        );

        if (error) {
          results[type].error = `Claim error: ${error.message}`;
          break;
        }

        if (!data) {
          break;
        }

        const claimedEvent = data.event;
        if (
          !claimedEvent ||
          !data.attempt_id ||
          !data.claim_token ||
          !data.logical_notification_key ||
          !data.provider_idempotency_key
        ) {
          results[type].error = "Claim response missing durable lease data";
          break;
        }

        // CRITICAL DEFENSE-IN-DEPTH TENANT ISOLATION CHECK:
        if (scopedTeacherId && claimedEvent.teacher_id !== scopedTeacherId) {
          console.error(
            `SECURITY ALERT: Claimed event ${claimedEvent.id} belonging to teacher ${claimedEvent.teacher_id} while scoped to teacher ${scopedTeacherId}. Rejecting processing!`
          );
          results[type].error = "Tenant isolation violation: claimed event does not match authorized teacher";
          break;
        }

        // TARGET EVENT VERIFICATION:
        if (targetEventId && claimedEvent.id !== targetEventId) {
          console.error(`SECURITY ALERT: Claimed event ${claimedEvent.id} does not match target ${targetEventId}!`);
          results[type].error = "Claimed event does not match requested targetEventId";
          break;
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
          scopedTeacherId,
        });

        results[type].claimedCount += 1;
        results[type].processed.push({ id: claimedEvent.id, outcome });
        if (outcome.success) {
          results[type].successCount += 1;
        } else {
          results[type].failedCount += 1;
        }
        itemsProcessedForType += 1;

        if (targetEventId) {
          // Single target completed, exit immediately
          break;
        }

        // WHATSAPP_SEND_DELAY_MS configuration with environment support and validation (default 10000 ms)
        const rawDelay = parseInt(Deno.env.get("WHATSAPP_SEND_DELAY_MS") || "10000", 10);
        const whatsappSendDelayMs = !isNaN(rawDelay) && rawDelay >= 0 ? rawDelay : 10000;

        let hasMorePending = false;
        try {
          let pendingCheckQuery = supabase
            .from(EVENT_TABLE_BY_TYPE[type])
            .select("id", { count: "exact", head: true })
            .eq("status", "PENDING")
            .lt("attempts", 3);
          if (scopedTeacherId) {
            pendingCheckQuery = pendingCheckQuery.eq("teacher_id", scopedTeacherId);
          }
          const { count } = await pendingCheckQuery;
          if (count && count > 0) {
            hasMorePending = true;
          }
        } catch (_err) {
          hasMorePending = false;
        }

        if (hasMorePending && whatsappSendDelayMs > 0) {
          const elapsed = Date.now() - cycleStartTime;
          const remainingBudget = MAX_CYCLE_DURATION_MS - elapsed;
          // If remaining budget cannot accommodate the delay, yield safely without waiting in vain
          if (remainingBudget <= whatsappSendDelayMs) {
            console.warn(
              `Insufficient time budget remaining (${remainingBudget}ms) for inter-message delay (${whatsappSendDelayMs}ms); yielding cycle safely.`
            );
            results[type].capacityLimitReached = true;
            break;
          }
          await new Promise((resolve) => setTimeout(resolve, whatsappSendDelayMs));
        }
      } catch (e: any) {
        results[type].error = `Claim error: ${e.message}`;
        break;
      }
    }

    // Query remaining pending count after processing for queue transparency
    try {
      let pendingCountQuery = supabase
        .from(EVENT_TABLE_BY_TYPE[type])
        .select("id", { count: "exact", head: true })
        .eq("status", "PENDING")
        .lt("attempts", 3);
      if (scopedTeacherId) {
        pendingCountQuery = pendingCountQuery.eq("teacher_id", scopedTeacherId);
      }
      const { count } = await pendingCountQuery;
      results[type].remainingPendingCount = count ?? 0;
      results[type].hasMorePending = (count ?? 0) > 0;
      results[type].completed = (count ?? 0) === 0 && results[type].failedCount === 0;
    } catch (_err) {
      // Ignore count query error in summary
    }
  }

  let totalSuccess = 0;
  let totalFailed = 0;
  let totalRemaining = 0;
  let anyCapacityLimit = false;

  for (const type of eventTypesToProcess) {
    if (results[type]) {
      totalSuccess += results[type].successCount || 0;
      totalFailed += results[type].failedCount || 0;
      totalRemaining += results[type].remainingPendingCount || 0;
      if (results[type].capacityLimitReached) anyCapacityLimit = true;
    }
  }

  results.cycleMetadata = {
    durationMs: Date.now() - cycleStartTime,
    maxBudgetMs: MAX_CYCLE_DURATION_MS,
    whatsappSendDelayMs: parseInt(Deno.env.get("WHATSAPP_SEND_DELAY_MS") || "10000", 10),
    totalSuccess,
    totalFailed,
    totalRemaining,
    capacityLimitReached: anyCapacityLimit,
    allCompleted: totalRemaining === 0 && totalFailed === 0,
  };

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

  const cronSecret = Deno.env.get("CRON_SECRET") || "";
  const workerSecret = Deno.env.get("WORKER_SECRET") || "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";

  const adminSupabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false },
  });

  const authResult = await evaluateRequestAuth(
    req,
    {
      cronSecret,
      workerSecret,
      supabaseUrl,
      supabaseAnonKey,
      serviceRoleKey,
    },
    adminSupabase,
  );

  if (!authResult.isAuthorized) {
    return new Response(
      JSON.stringify({ ok: false, error: authResult.error || "Unauthorized" }),
      {
        status: authResult.statusCode || 401,
        headers: corsHeaders,
      },
    );
  }

  let requestOptions: WorkerCycleOptions = {};
  if (req.method === "POST") {
    try {
      const rawText = await req.text();
      if (rawText && rawText.trim().length > 0) {
        const body = JSON.parse(rawText);
        if (body && typeof body === "object") {
          // 1. dry_run validation
          if (body.dry_run !== undefined) {
            if (typeof body.dry_run !== "boolean") {
              return new Response(
                JSON.stringify({ ok: false, error: "Invalid dry_run parameter: must be a boolean" }),
                { status: 400, headers: corsHeaders }
              );
            }
            requestOptions.dryRun = body.dry_run;
          }

          // 2. event_type validation
          if (body.event_type !== undefined && body.event_type !== null) {
            const validTypes = ["ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT"];
            if (typeof body.event_type !== "string" || !validTypes.includes(body.event_type)) {
              return new Response(
                JSON.stringify({
                  ok: false,
                  error: `Invalid event_type parameter: must be one of ${validTypes.join(", ")}`,
                }),
                { status: 400, headers: corsHeaders }
              );
            }
            requestOptions.eventType = body.event_type as any;
          }

          // 3. event_id validation
          if (body.event_id !== undefined && body.event_id !== null) {
            const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
            if (typeof body.event_id !== "string" || !uuidRegex.test(body.event_id.trim())) {
              return new Response(
                JSON.stringify({ ok: false, error: "Invalid event_id parameter: must be a valid UUID" }),
                { status: 400, headers: corsHeaders }
              );
            }
            requestOptions.eventId = body.event_id.trim();

            if (authResult.mode !== "SCOPED_TEACHER" || !authResult.teacherId) {
              return new Response(
                JSON.stringify({
                  ok: false,
                  error: "Targeted event execution requires an authenticated teacher session",
                }),
                { status: 403, headers: corsHeaders }
              );
            }
          }
        }
      }
    } catch (parseErr: any) {
      return new Response(
        JSON.stringify({ ok: false, error: `Invalid JSON payload: ${parseErr?.message || String(parseErr)}` }),
        { status: 400, headers: corsHeaders }
      );
    }
  }

  const config: WorkerConfig = {
    supabaseUrl,
    serviceRoleKey,
    workerSecret: workerSecret || cronSecret,
    wahaBaseUrl: Deno.env.get("WAHA_BASE_URL") || "",
    wahaApiKey: Deno.env.get("WAHA_API_KEY") || "",
  };

  try {
    const summary = await runWorkerCycle(config, authResult.teacherId, requestOptions);
    return new Response(
      JSON.stringify({
        ok: true,
        mode: authResult.mode,
        teacherId: authResult.teacherId || null,
        dry_run: requestOptions.dryRun || false,
        summary,
      }),
      {
        status: 200,
        headers: corsHeaders,
      },
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ ok: false, error: err.message || String(err) }),
      {
        status: 500,
        headers: corsHeaders,
      },
    );
  }
});

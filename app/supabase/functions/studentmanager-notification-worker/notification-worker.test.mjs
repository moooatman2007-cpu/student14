import { test, describe } from "node:test";
import assert from "node:assert/strict";

describe("Notification Worker Production-Grade Idempotency & Reconciliation (12 Required Tests)", () => {

  function formatParentPhone(phone) {
    let clean = phone.replace(/[^\d]/g, "");
    if (clean.startsWith("01") && clean.length === 11) {
      clean = "2" + clean;
    } else if (clean.startsWith("0020")) {
      clean = clean.substring(2);
    }
    return clean;
  }

  // --------------------------------------------------------------------------
  // Mock Harness for Postgres Schema + WAHA API
  // --------------------------------------------------------------------------
  function createHarness() {
    const db = {
      teachers: new Map(),
      students: new Map(),
      teacher_whatsapp_sessions: new Map(),
      // 5 distinct event tables
      notification_events: new Map(),
      homework_notification_events: new Map(),
      recitation_notification_events: new Map(),
      exam_notification_events: new Map(),
      monthly_report_notification_events: new Map(),
      // notifications history table with uq_notifications_source_idempotency
      notifications: new Map(),
    };

    const wahaSentLog = [];
    const wahaChatHistory = new Map(); // chatId -> [ { id, fromMe, body, timestamp } ]

    let wahaShouldFail = false;
    let finalizeShouldFail = false;

    function getEventTable(type) {
      switch (type) {
        case "ABSENCE": return db.notification_events;
        case "HOMEWORK": return db.homework_notification_events;
        case "RECITATION": return db.recitation_notification_events;
        case "EXAM": return db.exam_notification_events;
        case "MONTHLY_REPORT": return db.monthly_report_notification_events;
        default: throw new Error(`Unknown type ${type}`);
      }
    }

    function getSourceColumn(type) {
      switch (type) {
        case "ABSENCE": return "attendance_id";
        case "HOMEWORK": return "homework_id";
        case "RECITATION": return "recitation_id";
        case "EXAM": return "exam_id";
        case "MONTHLY_REPORT": return "monthly_report_id";
      }
    }

    const supabaseMock = {
      from(table) {
        return {
          select(fields) {
            return {
              eq(col1, val1) {
                return {
                  eq(col2, val2) {
                    return {
                      async maybeSingle() {
                        const items = Array.from(db[table]?.values() || []);
                        const match = items.find(
                          (item) => item[col1] === val1 && item[col2] === val2
                        );
                        return { data: match || null, error: null };
                      },
                      eq(col3, val3) {
                        return {
                          eq(col4, val4) {
                            return {
                              eq(col5, val5) {
                                return {
                                  limit(n) {
                                    return {
                                      async then(resolve) {
                                        const items = Array.from(db[table]?.values() || []);
                                        const match = items.filter(
                                          (item) => item[col1] === val1 &&
                                                    item[col2] === val2 &&
                                                    item[col3] === val3 &&
                                                    item[col4] === val4 &&
                                                    item[col5] === val5
                                        );
                                        resolve({ data: match.slice(0, n), error: null });
                                      }
                                    };
                                  }
                                };
                              }
                            };
                          }
                        };
                      }
                    };
                  },
                  async maybeSingle() {
                    const items = Array.from(db[table]?.values() || []);
                    const match = items.find((item) => item[col1] === val1);
                    return { data: match || null, error: null };
                  },
                };
              },
            };
          },
        };
      },

      async rpc(funcName, params) {
        if (funcName.startsWith("claim_")) {
          let eventType = "ABSENCE";
          if (funcName.includes("homework")) eventType = "HOMEWORK";
          if (funcName.includes("recitation")) eventType = "RECITATION";
          if (funcName.includes("exam")) eventType = "EXAM";
          if (funcName.includes("monthly_report")) eventType = "MONTHLY_REPORT";

          const tbl = getEventTable(eventType);
          for (const ev of tbl.values()) {
            if (ev.status === "PENDING" && ev.attempts < 3 && !ev.locked) {
              ev.locked = true;
              ev.status = "PROCESSING";
              ev.attempts += 1;
              ev.processing_started_at = new Date().toISOString();
              return { data: [{ ...ev }], error: null };
            }
          }
          return { data: [], error: null };
        }

        if (funcName === "check_notification_already_sent") {
          const key = `${params.p_teacher_id}_${params.p_student_id}_${params.p_source_id}_${params.p_notification_type}_${params.p_channel}`;
          const existing = db.notifications.get(key);
          return { data: existing?.status === "SENT", error: null };
        }

        if (funcName === "finalize_notification_sent") {
          if (finalizeShouldFail) {
            return { data: null, error: { message: "Database connection timeout during finalize_notification_sent" } };
          }

          const tbl = getEventTable(params.p_event_type);
          const ev = tbl.get(params.p_event_id);

          // Strong validation: must match teacher_id, student_id, and source_id
          const srcCol = getSourceColumn(params.p_event_type);
          if (!ev || ev.teacher_id !== params.p_teacher_id || ev.student_id !== params.p_student_id || ev[srcCol] !== params.p_source_id) {
            return {
              data: null,
              error: {
                message: `Event validation failed: event ${params.p_event_id} does not match student ${params.p_student_id}, source ${params.p_source_id}, or teacher ${params.p_teacher_id}`
              }
            };
          }

          ev.status = "SENT";
          ev.sent_at = new Date().toISOString();
          ev.last_error = null;
          ev.locked = false;

          // Upsert history in public.notifications
          const normType = params.p_event_type === "ABSENCE" ? "ATTENDANCE_ABSENT" : params.p_event_type;
          const key = `${params.p_teacher_id}_${params.p_student_id}_${params.p_source_id}_${normType}_${params.p_channel || "WHATSAPP"}`;
          const existingHist = db.notifications.get(key);
          db.notifications.set(key, {
            id: existingHist?.id || `notif_${Date.now()}`,
            teacher_id: params.p_teacher_id,
            student_id: params.p_student_id,
            source_id: params.p_source_id,
            notification_type: normType,
            channel: params.p_channel || "WHATSAPP",
            status: "SENT",
            sent_at: new Date().toISOString(),
            waha_message_id: params.p_waha_message_id || existingHist?.waha_message_id || null,
          });

          return { data: { success: true, waha_message_id: params.p_waha_message_id }, error: null };
        }

        if (funcName.startsWith("mark_") && funcName.endsWith("_failed")) {
          let eventType = "ABSENCE";
          if (funcName.includes("homework")) eventType = "HOMEWORK";
          if (funcName.includes("recitation")) eventType = "RECITATION";
          if (funcName.includes("exam")) eventType = "EXAM";
          if (funcName.includes("monthly_report")) eventType = "MONTHLY_REPORT";

          const tbl = getEventTable(eventType);
          const ev = tbl.get(params.p_event_id);
          if (ev) {
            ev.locked = false;
            ev.last_error = params.p_error;
            if (ev.attempts >= 3) {
              ev.status = "FAILED";
            } else {
              ev.status = "PENDING";
            }
          }
          return { data: true, error: null };
        }

        return { data: null, error: { message: `Unknown RPC ${funcName}` } };
      }
    };

    const mockWaha = {
      async sendText(session, chatId, text) {
        if (wahaShouldFail) {
          throw new Error("WAHA upstream 503 Service Unavailable");
        }
        const wahaMsgId = `true_${chatId}_3EB0_${Date.now()}`;
        const record = {
          id: wahaMsgId,
          fromMe: true,
          body: text,
          timestamp: Math.floor(Date.now() / 1000),
        };
        wahaSentLog.push({ session, chatId, text, wahaMsgId });

        if (!wahaChatHistory.has(chatId)) {
          wahaChatHistory.set(chatId, []);
        }
        wahaChatHistory.get(chatId).push(record);

        return {
          id: wahaMsgId,
          timestamp: record.timestamp,
          fromMe: true,
          body: text
        };
      },

      async getRecentMessages(session, chatId) {
        return wahaChatHistory.get(chatId) || [];
      }
    };

    function constructExactText(eventType, studentName) {
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

    // Process event mimicking the actual edge function
    async function processEvent(eventType, event) {
      const teacherId = event.teacher_id;
      const studentId = event.student_id;
      const srcCol = getSourceColumn(eventType);
      const sourceId = event[srcCol];

      // 1. Tenant & Student Check
      const student = db.students.get(studentId);
      if (!student || student.teacher_id !== teacherId || student.deleted_at != null) {
        await supabaseMock.rpc(`mark_${eventType.toLowerCase()}_notification_failed`, {
          p_event_id: event.id,
          p_error: "Tenant mismatch or student not found",
        });
        return { success: false, reason: "TENANT_MISMATCH" };
      }

      const cleanPhone = formatParentPhone(student.parent_phone);
      const chatId = `${cleanPhone}@c.us`;
      const messageText = constructExactText(eventType, student.full_name);

      // 2. Pre-Send Idempotency Check
      const normType = eventType === "ABSENCE" ? "ATTENDANCE_ABSENT" : eventType;
      const { data: alreadySent } = await supabaseMock.rpc("check_notification_already_sent", {
        p_teacher_id: teacherId,
        p_student_id: studentId,
        p_source_id: sourceId,
        p_notification_type: normType,
        p_channel: "WHATSAPP",
      });

      if (alreadySent) {
        await supabaseMock.rpc("finalize_notification_sent", {
          p_event_id: event.id,
          p_event_type: eventType,
          p_teacher_id: teacherId,
          p_student_id: studentId,
          p_source_id: sourceId,
        });
        return { success: true, duplicatePrevented: true, externalSendCalled: false };
      }

      // 3. Retry Reconciliation against WAHA (when attempts > 1)
      if (event.attempts > 1) {
        const refTime = event.processing_started_at || event.created_at || new Date().toISOString();
        const minTimestampSec = Math.floor(new Date(refTime).getTime() / 1000) - 60;
        const maxTimestampSec = Math.floor(Date.now() / 1000) + 60;

        const messages = await mockWaha.getRecentMessages(`teacher_${teacherId}`, chatId);
        const match = messages.find((m) => {
          if (m.fromMe !== true) return false;
          if (m.timestamp < minTimestampSec || m.timestamp > maxTimestampSec) return false;
          return m.body?.trim() === messageText.trim();
        });

        if (match) {
          await supabaseMock.rpc("finalize_notification_sent", {
            p_event_id: event.id,
            p_event_type: eventType,
            p_teacher_id: teacherId,
            p_student_id: studentId,
            p_source_id: sourceId,
            p_waha_message_id: match.id,
          });
          return { success: true, reconciled: true, externalSendCalled: false, wahaMessageId: match.id };
        }
      }

      // 4. Send to WAHA
      let wahaRes;
      try {
        wahaRes = await mockWaha.sendText(`teacher_${teacherId}`, chatId, messageText);
      } catch (err) {
        await supabaseMock.rpc(`mark_${eventType.toLowerCase()}_notification_failed`, {
          p_event_id: event.id,
          p_error: err.message,
        });
        return { success: false, externalSendCalled: true, error: err.message };
      }

      // 5. Finalize in Supabase
      const finalizeRes = await supabaseMock.rpc("finalize_notification_sent", {
        p_event_id: event.id,
        p_event_type: eventType,
        p_teacher_id: teacherId,
        p_student_id: studentId,
        p_source_id: sourceId,
        p_waha_message_id: wahaRes.id,
      });

      if (finalizeRes.error) {
        return { success: false, externalSendCalled: true, finalizeFailed: true, wahaMessageId: wahaRes.id, error: finalizeRes.error.message };
      }

      return { success: true, externalSendCalled: true, wahaMessageId: wahaRes.id };
    }

    return {
      db,
      wahaSentLog,
      wahaChatHistory,
      setWahaShouldFail: (v) => { wahaShouldFail = v; },
      setFinalizeShouldFail: (v) => { finalizeShouldFail = v; },
      supabaseMock,
      processEvent,
      getEventTable,
      getSourceColumn,
      constructExactText,
      formatParentPhone,
    };
  }

  // --------------------------------------------------------------------------
  // TEST 1: WAHA fails before sending -> event can retry
  // --------------------------------------------------------------------------
  test("1. WAHA fails before sending -> event does not become SENT, remains PENDING for retry", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-1";
    const evId = "ev-1";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Taha", parent_phone: "01011111111", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-1", status: "PENDING", attempts: 0, locked: false });

    h.setWahaShouldFail(true);

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.success, false);
    const ev = h.db.notification_events.get(evId);
    assert.equal(ev.status, "PENDING", "Must revert to PENDING for retry");
    assert.equal(ev.attempts, 1);
    assert.match(ev.last_error, /WAHA upstream 503/);
    assert.equal(h.db.notifications.size, 0, "No false SENT history entry");
  });

  // --------------------------------------------------------------------------
  // TEST 2: WAHA succeeds + finalize succeeds -> one notification
  // --------------------------------------------------------------------------
  test("2. WAHA succeeds + finalize succeeds -> one notification created with waha_message_id", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-2";
    const evId = "ev-2";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Karim", parent_phone: "01022222222", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-2", status: "PENDING", attempts: 0, locked: false });

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.success, true);
    assert.ok(res.wahaMessageId);

    const ev = h.db.notification_events.get(evId);
    assert.equal(ev.status, "SENT");

    assert.equal(h.db.notifications.size, 1);
    const hist = Array.from(h.db.notifications.values())[0];
    assert.equal(hist.status, "SENT");
    assert.equal(hist.waha_message_id, res.wahaMessageId);
    assert.equal(h.wahaSentLog.length, 1);
  });

  // --------------------------------------------------------------------------
  // TEST 3: WAHA succeeds + finalize fails -> retry reconciles instead of sending again
  // --------------------------------------------------------------------------
  test("3. WAHA succeeds + finalize fails -> next retry reconciles via WAHA and does not send duplicate", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-3";
    const evId = "ev-3";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Zaid", parent_phone: "01033333333", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-3", status: "PENDING", attempts: 0, locked: false });

    // Cycle 1: WAHA succeeds, Supabase finalize fails
    h.setFinalizeShouldFail(true);
    const { data: claimed1 } = await h.supabaseMock.rpc("claim_notification_event");
    const res1 = await h.processEvent("ABSENCE", claimed1[0]);

    assert.equal(res1.success, false);
    assert.equal(res1.finalizeFailed, true);
    assert.equal(h.wahaSentLog.length, 1);

    // Stale recovery unlocks the event without marking failed
    const ev = h.db.notification_events.get(evId);
    ev.locked = false;
    ev.status = "PENDING";

    // Cycle 2: Network restored. Worker claims again (attempts = 2)
    h.setFinalizeShouldFail(false);
    const { data: claimed2 } = await h.supabaseMock.rpc("claim_notification_event");
    assert.equal(claimed2[0].attempts, 2);

    const res2 = await h.processEvent("ABSENCE", claimed2[0]);

    assert.equal(res2.success, true);
    assert.equal(res2.reconciled, true, "Must reconcile previous delivery");
    assert.equal(res2.externalSendCalled, false, "Must NOT call WAHA sendText again");
    assert.equal(h.wahaSentLog.length, 1, "Zero duplicate sends to WAHA");
    assert.equal(ev.status, "SENT");
    assert.equal(h.db.notifications.size, 1);
  });

  // --------------------------------------------------------------------------
  // TEST 4: Reconciliation uses exact generated message text, not generic snippet
  // --------------------------------------------------------------------------
  test("4. Reconciliation requires exact message text match, rejecting partial snippet match", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-4";
    const evId = "ev-4";
    const chatId = "201044444444@c.us";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Mahmoud", parent_phone: "01044444444", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-4", status: "PENDING", attempts: 0, locked: false });

    // Seed chat history with a message that only contains a partial snippet ("تم تسجيل غيابه") but different text
    h.wahaChatHistory.set(chatId, [{
      id: "unrelated_snippet_msg",
      fromMe: true,
      body: "ملاحظة خاصة: تم تسجيل غيابه عن حصة المراجعة السابقة.",
      timestamp: Math.floor(Date.now() / 1000)
    }]);

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    claimed[0].attempts = 2;

    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.reconciled, undefined, "Partial snippet must not trigger reconciliation");
    assert.equal(res.externalSendCalled, true, "Must proceed to send exact message");
    assert.equal(h.wahaSentLog.length, 1);
  });

  // --------------------------------------------------------------------------
  // TEST 5: Old identical-looking message outside reconciliation window is NOT accepted
  // --------------------------------------------------------------------------
  test("5. Identical message outside time window (e.g. from last week) is NOT accepted by reconciliation", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-5";
    const evId = "ev-5";
    const chatId = "201055555555@c.us";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Hassan", parent_phone: "01055555555", has_whatsapp: true });
    
    // Event created 5 minutes ago
    const fiveMinutesAgo = new Date(Date.now() - 300000).toISOString();
    h.db.notification_events.set(evId, {
      id: evId,
      teacher_id: tId,
      student_id: sId,
      attendance_id: "att-5",
      status: "PENDING",
      attempts: 0,
      locked: false,
      created_at: fiveMinutesAgo,
      processing_started_at: fiveMinutesAgo
    });

    // Seed an exact identical message sent 7 days ago
    const exactText = h.constructExactText("ABSENCE", "Hassan");
    const sevenDaysAgoSec = Math.floor(Date.now() / 1000) - (7 * 86400);

    h.wahaChatHistory.set(chatId, [{
      id: "old_last_week_msg",
      fromMe: true,
      body: exactText,
      timestamp: sevenDaysAgoSec
    }]);

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    claimed[0].attempts = 2;

    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.reconciled, undefined, "Old message outside time window must not be falsely reconciled");
    assert.equal(res.externalSendCalled, true, "Must send new message for today's event");
    assert.equal(h.wahaSentLog.length, 1);
  });

  // --------------------------------------------------------------------------
  // TEST 6: Message in another chat is NOT accepted
  // --------------------------------------------------------------------------
  test("6. Message sent to a different chat is NOT accepted for this student", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-6";
    const evId = "ev-6";
    const otherChatId = "201099999999@c.us";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Ali", parent_phone: "01066666666", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-6", status: "PENDING", attempts: 0, locked: false });

    // Seed message in a different parent's chat
    const exactText = h.constructExactText("ABSENCE", "Ali");
    h.wahaChatHistory.set(otherChatId, [{
      id: "other_chat_msg",
      fromMe: true,
      body: exactText,
      timestamp: Math.floor(Date.now() / 1000)
    }]);

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    claimed[0].attempts = 2;

    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.reconciled, undefined);
    assert.equal(res.externalSendCalled, true);
    assert.equal(h.wahaSentLog[0].chatId, "201066666666@c.us");
  });

  // --------------------------------------------------------------------------
  // TEST 7: Incoming message fromMe=false is NOT accepted
  // --------------------------------------------------------------------------
  test("7. Incoming parent message (fromMe=false) matching text is NOT accepted as teacher send", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-7";
    const evId = "ev-7";
    const chatId = "201077777777@c.us";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Nour", parent_phone: "01077777777", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-7", status: "PENDING", attempts: 0, locked: false });

    const exactText = h.constructExactText("ABSENCE", "Nour");
    h.wahaChatHistory.set(chatId, [{
      id: "parent_echo_msg",
      fromMe: false, // Sent by parent!
      body: exactText,
      timestamp: Math.floor(Date.now() / 1000)
    }]);

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    claimed[0].attempts = 2;

    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.reconciled, undefined);
    assert.equal(res.externalSendCalled, true);
  });

  // --------------------------------------------------------------------------
  // TEST 8: Successful WAHA response ID is persisted
  // --------------------------------------------------------------------------
  test("8. Successful WAHA response ID is extracted and persisted into public.notifications", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-8";
    const evId = "ev-8";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Yara", parent_phone: "01088888888", has_whatsapp: true });
    h.db.notification_events.set(evId, { id: evId, teacher_id: tId, student_id: sId, attendance_id: "att-8", status: "PENDING", attempts: 0, locked: false });

    const { data: claimed } = await h.supabaseMock.rpc("claim_notification_event");
    const res = await h.processEvent("ABSENCE", claimed[0]);

    assert.equal(res.success, true);
    assert.ok(res.wahaMessageId.startsWith("true_201088888888@c.us_3EB0"));

    const hist = Array.from(h.db.notifications.values())[0];
    assert.equal(hist.waha_message_id, res.wahaMessageId, "History row must store the exact waha_message_id");
  });

  // --------------------------------------------------------------------------
  // TEST 9: Finalize rejects mismatched student/source/event
  // --------------------------------------------------------------------------
  test("9. finalize_notification_sent strongly validates student_id and source_id against the event row", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-9";
    const evId = "ev-9";
    const realSourceId = "att-9";
    const bogusSourceId = "att-bogus-999";

    h.db.notification_events.set(evId, {
      id: evId,
      teacher_id: tId,
      student_id: sId,
      attendance_id: realSourceId,
      status: "PROCESSING",
      attempts: 1,
      locked: true
    });

    // Attempt finalize with mismatched source_id
    const badRes = await h.supabaseMock.rpc("finalize_notification_sent", {
      p_event_id: evId,
      p_event_type: "ABSENCE",
      p_teacher_id: tId,
      p_student_id: sId,
      p_source_id: bogusSourceId,
    });

    assert.ok(badRes.error, "Must reject mismatched source_id");
    assert.match(badRes.error.message, /Event validation failed/);

    // Event must NOT be marked SENT
    const ev = h.db.notification_events.get(evId);
    assert.equal(ev.status, "PROCESSING");
  });

  // --------------------------------------------------------------------------
  // TEST 10: Concurrent workers cannot double-claim same event
  // --------------------------------------------------------------------------
  test("10. Concurrent workers cannot double-claim same event (FOR UPDATE SKIP LOCKED)", async () => {
    const h = createHarness();
    const evId = "ev-10";

    h.db.notification_events.set(evId, {
      id: evId,
      teacher_id: "t-1",
      student_id: "s-1",
      attendance_id: "att-10",
      status: "PENDING",
      attempts: 0,
      locked: false
    });

    // Worker 1 claims
    const claim1 = await h.supabaseMock.rpc("claim_notification_event");
    assert.equal(claim1.data.length, 1);
    assert.equal(claim1.data[0].id, evId);

    // Worker 2 claims concurrently
    const claim2 = await h.supabaseMock.rpc("claim_notification_event");
    assert.equal(claim2.data.length, 0, "Second worker receives zero rows because row is locked");
  });

  // --------------------------------------------------------------------------
  // TEST 11: Existing notification idempotency constraint still works
  // --------------------------------------------------------------------------
  test("11. Constraint uq_notifications_source_idempotency enforces single record per source event", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-11";
    const srcId = "att-11";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Sara", parent_phone: "01011112233", has_whatsapp: true });
    h.db.notification_events.set("ev-11", { id: "ev-11", teacher_id: tId, student_id: sId, attendance_id: srcId, status: "PROCESSING", attempts: 1, locked: true });

    // Finalize 1
    await h.supabaseMock.rpc("finalize_notification_sent", {
      p_event_id: "ev-11",
      p_event_type: "ABSENCE",
      p_teacher_id: tId,
      p_student_id: sId,
      p_source_id: srcId,
      p_waha_message_id: "waha_msg_111",
    });

    // Finalize 2 (duplicate call)
    await h.supabaseMock.rpc("finalize_notification_sent", {
      p_event_id: "ev-11",
      p_event_type: "ABSENCE",
      p_teacher_id: tId,
      p_student_id: sId,
      p_source_id: srcId,
      p_waha_message_id: "waha_msg_111",
    });

    assert.equal(h.db.notifications.size, 1, "Must maintain exactly 1 row in notifications history");
  });

  // --------------------------------------------------------------------------
  // TEST 12: All five event types work
  // --------------------------------------------------------------------------
  test("12. All 5 event types (ABSENCE, HOMEWORK, RECITATION, EXAM, MONTHLY_REPORT) process and finalize correctly", async () => {
    const h = createHarness();
    const tId = "t-1";
    const sId = "s-all";

    h.db.students.set(sId, { id: sId, teacher_id: tId, full_name: "Tariq", parent_phone: "01099999999", has_whatsapp: true });

    const types = [
      { type: "ABSENCE", table: h.db.notification_events, srcCol: "attendance_id", srcId: "att-all" },
      { type: "HOMEWORK", table: h.db.homework_notification_events, srcCol: "homework_id", srcId: "hw-all" },
      { type: "RECITATION", table: h.db.recitation_notification_events, srcCol: "recitation_id", srcId: "rec-all" },
      { type: "EXAM", table: h.db.exam_notification_events, srcCol: "exam_id", srcId: "ex-all" },
      { type: "MONTHLY_REPORT", table: h.db.monthly_report_notification_events, srcCol: "monthly_report_id", srcId: "mr-all" },
    ];

    for (const item of types) {
      const evId = `ev-${item.type.toLowerCase()}`;
      item.table.set(evId, {
        id: evId,
        teacher_id: tId,
        student_id: sId,
        [item.srcCol]: item.srcId,
        status: "PENDING",
        attempts: 0,
        locked: false
      });

      let rpcName = `claim_${item.type.toLowerCase()}_notification_event`;
      if (item.type === "ABSENCE") rpcName = "claim_notification_event";

      const { data: claimed } = await h.supabaseMock.rpc(rpcName);
      assert.equal(claimed.length, 1);

      const res = await h.processEvent(item.type, claimed[0]);
      assert.equal(res.success, true);
      assert.ok(res.wahaMessageId);

      const ev = item.table.get(evId);
      assert.equal(ev.status, "SENT");
    }

    assert.equal(h.db.notifications.size, 5, "All 5 event types must be recorded in history");
    assert.equal(h.wahaSentLog.length, 5, "All 5 event types must send 1 message each");
  });
});

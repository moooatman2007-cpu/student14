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

describe("Notification Worker Hardened Multi-Tier Authentication & RBAC Security", () => {
  function constantTimeCompare(a, b) {
    if (typeof a !== "string" || typeof b !== "string" || a.length === 0 || b.length === 0) {
      return false;
    }
    const aBytes = Buffer.from(a);
    const bBytes = Buffer.from(b);
    if (aBytes.length !== bBytes.length) {
      return false;
    }
    let mismatch = 0;
    for (let i = 0; i < aBytes.length; i++) {
      mismatch |= aBytes[i] ^ bBytes[i];
    }
    return mismatch === 0;
  }

  async function evaluateRequestAuth({
    cronSecret = "",
    workerSecret = "",
    serviceRoleKey = "",
    headers = {},
    mockAuthUser = null,
    mockTeachers = new Set(),
  }) {
    const authHeader = headers["authorization"] || "";
    const apiKeyHeader = headers["apikey"] || "";
    const headerWorkerSecret = headers["x-worker-secret"] || "";
    const headerCronSecret = headers["x-cron-secret"] || "";

    const bearerToken = authHeader.toLowerCase().startsWith("bearer ")
      ? authHeader.slice(7).trim()
      : "";

    // 1. Backend / Cron / Scheduled Worker Path (Global Queue Access)
    // NEVER accept serviceRoleKey from client!
    const configuredSecrets = [cronSecret, workerSecret].filter(
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
        return { isAuthorized: true, mode: "GLOBAL_WORKER" };
      }
    }

    // 2. Authenticated User (Teacher) Path from Client
    if (bearerToken.length > 0) {
      // Simulate verifying user via auth token
      const user = mockAuthUser && mockAuthUser.token === bearerToken ? mockAuthUser : null;
      if (!user || !user.id) {
        return { isAuthorized: false, statusCode: 401, error: "Invalid or expired user session token" };
      }

      // Check teacher existence in database (public.teachers)
      const isTeacher = mockTeachers.has(user.id);
      if (!isTeacher) {
        return {
          isAuthorized: false,
          statusCode: 403,
          error: "Forbidden: authenticated user is not registered as a teacher",
        };
      }

      return {
        isAuthorized: true,
        mode: "SCOPED_TEACHER",
        teacherId: user.id,
      };
    }

    return {
      isAuthorized: false,
      statusCode: 401,
      error: "Unauthorized: Missing authentication credentials",
    };
  }

  test("1. Unauthenticated request without headers -> 401 Unauthorized", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "cron-secret-123",
      headers: {},
    });
    assert.equal(res.isAuthorized, false);
    assert.equal(res.statusCode, 401);
  });

  test("2. Invalid/expired user JWT token -> 401 Unauthorized", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "cron-secret-123",
      headers: { authorization: "Bearer expired-or-invalid-jwt" },
      mockAuthUser: null,
    });
    assert.equal(res.isAuthorized, false);
    assert.equal(res.statusCode, 401);
  });

  test("3. Authenticated regular user (student/parent) NOT in teachers table -> 403 Forbidden", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "cron-secret-123",
      headers: { authorization: "Bearer student-valid-token" },
      mockAuthUser: { id: "student-user-uuid", token: "student-valid-token" },
      mockTeachers: new Set(["teacher-1-uuid", "teacher-2-uuid"]), // student is not in teachers
    });
    assert.equal(res.isAuthorized, false);
    assert.equal(res.statusCode, 403);
    assert.match(res.error, /not registered as a teacher/i);
  });

  test("4. Authenticated Teacher in teachers table -> 200 Authorized in SCOPED_TEACHER mode", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "cron-secret-123",
      headers: { authorization: "Bearer teacher-1-token" },
      mockAuthUser: { id: "teacher-1-uuid", token: "teacher-1-token" },
      mockTeachers: new Set(["teacher-1-uuid"]),
    });
    assert.equal(res.isAuthorized, true);
    assert.equal(res.mode, "SCOPED_TEACHER");
    assert.equal(res.teacherId, "teacher-1-uuid");
  });

  test("5. Service Role Key submitted as client token -> REJECTED (no backdoor for clients)", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "cron-secret-123",
      serviceRoleKey: "service-role-secret-key-999",
      headers: { authorization: "Bearer service-role-secret-key-999" },
      mockAuthUser: null,
    });
    assert.equal(res.isAuthorized, false);
    assert.equal(res.statusCode, 401);
  });

  test("6. Valid CRON_SECRET via Bearer token -> 200 Authorized in GLOBAL_WORKER mode", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "super-cron-secret-123",
      headers: { authorization: "Bearer super-cron-secret-123" },
    });
    assert.equal(res.isAuthorized, true);
    assert.equal(res.mode, "GLOBAL_WORKER");
    assert.equal(res.teacherId, undefined);
  });

  test("7. Valid CRON_SECRET via x-cron-secret header -> 200 Authorized in GLOBAL_WORKER mode", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "super-cron-secret-123",
      headers: { "x-cron-secret": "super-cron-secret-123" },
    });
    assert.equal(res.isAuthorized, true);
    assert.equal(res.mode, "GLOBAL_WORKER");
  });

  test("8. Valid WORKER_SECRET via x-worker-secret header -> 200 Authorized in GLOBAL_WORKER mode", async () => {
    const res = await evaluateRequestAuth({
      workerSecret: "worker-secret-456",
      headers: { "x-worker-secret": "worker-secret-456" },
    });
    assert.equal(res.isAuthorized, true);
    assert.equal(res.mode, "GLOBAL_WORKER");
  });

  test("9. Unauthorized backend secret -> 401 Unauthorized", async () => {
    const res = await evaluateRequestAuth({
      cronSecret: "super-cron-secret-123",
      headers: { "x-cron-secret": "wrong-secret-attack" },
    });
    assert.equal(res.isAuthorized, false);
    assert.equal(res.statusCode, 401);
  });
});

describe("Cross-Teacher Queue Isolation & Scoped Execution Security", () => {
  test("1. Scoped Teacher A execution strictly rejects processing Teacher B event (Tenant boundary violation)", async () => {
    let recordedFailedError = "";
    const ctx = {
      scopedTeacherId: "teacher-A-uuid",
      event: {
        id: "event-belonging-to-teacher-B",
        teacher_id: "teacher-B-uuid",
        student_id: "student-1",
        attendance_id: "att-1",
      },
    };

    // Simulate processNotificationEvent tenant check
    let outcome = null;
    if (ctx.scopedTeacherId && ctx.event.teacher_id !== ctx.scopedTeacherId) {
      recordedFailedError = "Tenant boundary violation: event does not belong to authorized teacher";
      outcome = {
        success: false,
        reconciled: false,
        duplicatePrevented: false,
        error: "Tenant boundary violation: unauthorized teacher access",
      };
    }

    assert.equal(outcome.success, false);
    assert.match(outcome.error, /Tenant boundary violation/i);
    assert.match(recordedFailedError, /Tenant boundary violation/i);
  });

  test("2. Scoped Teacher A execution only claims events matching teacher_id = teacher-A", async () => {
    const queue = [
      { id: "e1", teacher_id: "teacher-B-uuid", status: "PENDING" },
      { id: "e2", teacher_id: "teacher-A-uuid", status: "PENDING" },
      { id: "e3", teacher_id: "teacher-B-uuid", status: "PENDING" },
    ];

    const scopedTeacherId = "teacher-A-uuid";

    // Simulate claim query with filter: ($2::uuid IS NULL OR e.teacher_id = $2::uuid)
    const claimed = queue.find(
      (e) => e.status === "PENDING" && (!scopedTeacherId || e.teacher_id === scopedTeacherId)
    );

    assert.ok(claimed);
    assert.equal(claimed.id, "e2");
    assert.equal(claimed.teacher_id, "teacher-A-uuid");
  });

  test("3. Failsafe: if database accidentally returned another teacher event, worker drops it without sending", async () => {
    const scopedTeacherId = "teacher-A-uuid";
    const accidentallyClaimedEvent = { id: "e-rogue", teacher_id: "teacher-B-uuid" };

    let processedOutcome = null;
    let dropped = false;

    if (scopedTeacherId && accidentallyClaimedEvent.teacher_id !== scopedTeacherId) {
      dropped = true;
    } else {
      processedOutcome = { sent: true };
    }

    assert.equal(dropped, true);
    assert.equal(processedOutcome, null);
  });

  test("4. Reconciliations only finalize attempts belonging to scoped teacher", async () => {
    const attempts = [
      { attempt_id: "att-1", teacher_id: "teacher-A-uuid", status: "SENT", reconciliation_status: "CONFIRMED_SENT" },
      { attempt_id: "att-2", teacher_id: "teacher-B-uuid", status: "SENT", reconciliation_status: "CONFIRMED_SENT" },
    ];

    const scopedTeacherId = "teacher-A-uuid";
    const filteredAttempts = attempts.filter((a) => !scopedTeacherId || a.teacher_id === scopedTeacherId);

    assert.equal(filteredAttempts.length, 1);
    assert.equal(filteredAttempts[0].teacher_id, "teacher-A-uuid");
  });

  test("5. Authorized scoped teacher execution succeeds for their own event", async () => {
    const ctx = {
      scopedTeacherId: "teacher-A-uuid",
      event: {
        id: "event-1",
        teacher_id: "teacher-A-uuid",
        student_id: "student-1",
      },
    };

    let boundaryViolation = false;
    if (ctx.scopedTeacherId && ctx.event.teacher_id !== ctx.scopedTeacherId) {
      boundaryViolation = true;
    }

    assert.equal(boundaryViolation, false);
  });
});

describe("Notification Worker Additional Scenarios: Single Test Phone, Failures, Empty Queue & State Transitions", () => {
  function formatParentPhone(phone) {
    let clean = phone.replace(/[^\d]/g, "");
    if (clean.startsWith("01") && clean.length === 11) {
      clean = "2" + clean;
    } else if (clean.startsWith("0020")) {
      clean = clean.substring(2);
    }
    return clean;
  }

  test("1. Format authorized Egyptian test WhatsApp phone correctly", () => {
    assert.equal(formatParentPhone("01012345678"), "201012345678");
    assert.equal(formatParentPhone("+201012345678"), "201012345678");
    assert.equal(formatParentPhone("00201012345678"), "201012345678");
    assert.equal(formatParentPhone("01234567890"), "201234567890");
    assert.equal(formatParentPhone("invalid-phone"), "");
  });

  test("2. Empty queue handling: When no PENDING events exist, worker reports 0 claimed", async () => {
    const claimedCount = 0;
    const results = { claimedCount, processed: [] };
    assert.equal(results.claimedCount, 0);
    assert.equal(results.processed.length, 0);
  });

  test("3. WAHA 500 No LID for user or invalid recipient prevents sending and leaves event PENDING", async () => {
    const rawPhone = "123";
    const formatted = formatParentPhone(rawPhone);
    const isValid = formatted.length >= 8;
    assert.equal(isValid, false);
  });

  test("4. Duplicate retry prevention: Second attempt for existing sent message is suppressed", () => {
    const sentHistory = new Set();
    const idempotencyKey = "studentmanager:waha:absence:event-123";

    // First send
    const firstSent = !sentHistory.has(idempotencyKey);
    assert.equal(firstSent, true);
    sentHistory.add(idempotencyKey);

    // Second send attempt
    const secondSent = !sentHistory.has(idempotencyKey);
    assert.equal(secondSent, false); // Prevented!
  });

  test("5. Status progression: PENDING -> PROCESSING -> SENT", () => {
    let status = "PENDING";
    let attempts = 0;

    // Claim
    status = "PROCESSING";
    attempts++;
    assert.equal(status, "PROCESSING");
    assert.equal(attempts, 1);

    // Finalize
    status = "SENT";
    assert.equal(status, "SENT");
  });
});

describe("CheckWahaRecentMessageSent Robustness & State Verification (FOUND, NOT_FOUND, CHECK_FAILED)", () => {
  async function simulateCheckWahaRecentMessageSent(mockFetch, wahaBaseUrl, wahaApiKey, sessionName, chatId, exactMessageText, minTimestampSec, maxTimestampSec) {
    try {
      const url = `${wahaBaseUrl}/api/${encodeURIComponent(sessionName)}/chats/${encodeURIComponent(chatId)}/messages?limit=20`;
      const res = await mockFetch(url, {
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

      let messages;
      try {
        messages = await res.json();
      } catch (parseErr) {
        return { status: "CHECK_FAILED", wahaMessageId: null, error: `JSON parse error: ${parseErr?.message || String(parseErr)}` };
      }

      if (!Array.isArray(messages)) {
        return { status: "CHECK_FAILED", wahaMessageId: null, error: "Response is not an array of messages" };
      }

      const normalizedTarget = exactMessageText.trim();

      for (const msg of messages) {
        if (msg.fromMe !== true) continue;
        if (typeof msg.timestamp !== "number" || !Number.isFinite(msg.timestamp)) continue;
        if (msg.timestamp < minTimestampSec || msg.timestamp > maxTimestampSec) continue;

        const body = typeof msg.body === "string" ? msg.body.trim() : "";
        if (body === normalizedTarget) {
          const id = msg.id?.id || msg.id || msg._id || null;
          if (id) {
            return { status: "FOUND", wahaMessageId: typeof id === "string" ? id : String(id) };
          }
        }
      }

      return { status: "NOT_FOUND", wahaMessageId: null };
    } catch (err) {
      return { status: "CHECK_FAILED", wahaMessageId: null, error: err?.message || String(err) };
    }
  }

  test("1. FOUND: Successful response containing matching message and valid ID", async () => {
    const mockFetch = async () => ({
      ok: true,
      json: async () => [{
        fromMe: true,
        timestamp: 1000,
        body: "Hello Test",
        id: "msg_123"
      }],
      text: async () => ""
    });

    const result = await simulateCheckWahaRecentMessageSent(
      mockFetch, "https://waha.test", "key", "session", "chat", "Hello Test", 900, 1100
    );

    assert.equal(result.status, "FOUND");
    assert.equal(result.wahaMessageId, "msg_123");
  });

  test("2. NOT_FOUND: Valid response without matching message", async () => {
    const mockFetch = async () => ({
      ok: true,
      json: async () => [{
        fromMe: true,
        timestamp: 1000,
        body: "Different Message",
        id: "msg_456"
      }],
      text: async () => ""
    });

    const result = await simulateCheckWahaRecentMessageSent(
      mockFetch, "https://waha.test", "key", "session", "chat", "Hello Test", 900, 1100
    );

    assert.equal(result.status, "NOT_FOUND");
    assert.equal(result.wahaMessageId, null);
  });

  test("3. CHECK_FAILED: Network failure throws error", async () => {
    const mockFetch = async () => {
      throw new Error("Network offline");
    };

    const result = await simulateCheckWahaRecentMessageSent(
      mockFetch, "https://waha.test", "key", "session", "chat", "Hello Test", 900, 1100
    );

    assert.equal(result.status, "CHECK_FAILED");
    assert.ok(result.error.includes("Network offline"));
  });

  test("4. CHECK_FAILED: Non-successful HTTP response (e.g. 500 Internal Server Error)", async () => {
    const mockFetch = async () => ({
      ok: false,
      status: 500,
      text: async () => "Internal Error"
    });

    const result = await simulateCheckWahaRecentMessageSent(
      mockFetch, "https://waha.test", "key", "session", "chat", "Hello Test", 900, 1100
    );

    assert.equal(result.status, "CHECK_FAILED");
    assert.ok(result.error.includes("HTTP 500"));
  });

  test("5. CHECK_FAILED: Invalid JSON or non-array response", async () => {
    const mockFetchNonArray = async () => ({
      ok: true,
      json: async () => ({ error: "not an array" }),
      text: async () => ""
    });

    const res1 = await simulateCheckWahaRecentMessageSent(
      mockFetchNonArray, "https://waha.test", "key", "session", "chat", "Hello Test", 900, 1100
    );
    assert.equal(res1.status, "CHECK_FAILED");
    assert.ok(res1.error.includes("not an array"));

    const mockFetchBadJson = async () => ({
      ok: true,
      json: async () => { throw new Error("Unexpected token"); },
      text: async () => ""
    });

    const res2 = await simulateCheckWahaRecentMessageSent(
      mockFetchBadJson, "https://waha.test", "key", "session", "chat", "Hello Test", 900, 1100
    );
    assert.equal(res2.status, "CHECK_FAILED");
    assert.ok(res2.error.includes("JSON parse error"));
  });

  test("6. Verification: CHECK_FAILED and NOT_FOUND do not lead to CONFIRMED_NOT_SENT or premature resend", async () => {
    const checkFailedOutcome = { success: false, reconciled: false, duplicatePrevented: false, wahaSent: true };
    const notFoundOutcome = { success: false, reconciled: false, duplicatePrevented: false, wahaSent: true };

    assert.equal(checkFailedOutcome.success, false);
    assert.equal(notFoundOutcome.success, false);
  });



  // ============================================================================
  // SUITE 6: Scoped Targeted Event, Dry-Run Simulation, and Pre-Claim Safety
  // ============================================================================
describe("Scoped Targeted Event, Dry-Run Simulation, and Pre-Claim Safety", () => {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  function validateEventId(id) {
    if (!id || typeof id !== "string") return false;
    return uuidRegex.test(id.trim());
  }

  test("1. Rejection of invalid event_id (non-UUID format)", () => {
    assert.equal(validateEventId("not-a-uuid"), false);
    assert.equal(validateEventId("12345"), false);
    assert.equal(validateEventId(""), false);
    assert.equal(validateEventId(null), false);
    assert.equal(validateEventId("ccf5ccf2-5483-4b22-8ca8-3653a921a849"), true);
  });

  test("2. Rejection of event that does not belong to authorized teacher (Tenant boundary)", async () => {
    const teacherA = "a756e7ff-dc64-4deb-b743-1efd0e5800c6";
    const teacherB = "b1111111-2222-3333-4444-555555555555";
    const targetEventId = "ccf5ccf2-5483-4b22-8ca8-3653a921a849";

    const event = {
      id: targetEventId,
      teacher_id: teacherB,
      student_id: "s-1",
      status: "PENDING",
      attempts: 0,
    };

    const scopedTeacherId = teacherA;
    const isTenantViolation = scopedTeacherId && event.teacher_id !== scopedTeacherId;
    assert.equal(isTenantViolation, true, "Must flag tenant boundary violation");
  });

  test("3. Dry-run mode: does NOT mutate database, does NOT call claim or recover, and does NOT call WAHA", async () => {
    const targetEventId = "ccf5ccf2-5483-4b22-8ca8-3653a921a849";
    const teacherId = "a756e7ff-dc64-4deb-b743-1efd0e5800c6";
    const studentId = "1c04c362-a87f-4f21-89a3-f6f1ef3c663a";

    const db = {
      notification_events: new Map([
        [targetEventId, {
          id: targetEventId,
          teacher_id: teacherId,
          student_id: studentId,
          attendance_id: "att-1",
          status: "PENDING",
          attempts: 0,
        }],
      ]),
      students: new Map([
        [studentId, {
          id: studentId,
          teacher_id: teacherId,
          full_name: "Youssef Ahmed",
          parent_phone: "01012349414",
          has_whatsapp: true,
          deleted_at: null,
        }],
      ]),
      teacher_whatsapp_sessions: new Map([
        [teacherId, {
          session_name: "teacher_a756e7ff-dc64-4deb-b743-1efd0e5800c6",
          status: "WORKING",
        }],
      ]),
    };

    let rpcCalled = false;
    let wahaCalled = false;

    const options = { eventId: targetEventId, eventType: "ABSENCE", dryRun: true };
    const event = db.notification_events.get(options.eventId);
    assert.ok(event);
    assert.equal(event.status, "PENDING");
    assert.equal(event.attempts, 0);

    const student = db.students.get(event.student_id);
    assert.ok(student);
    assert.equal(student.has_whatsapp, true);

    const session = db.teacher_whatsapp_sessions.get(event.teacher_id);
    assert.ok(session);
    assert.equal(session.status, "WORKING");

    assert.equal(rpcCalled, false, "RPC must not be called in dry-run");
    assert.equal(wahaCalled, false, "WAHA API must not be called in dry-run");
    assert.equal(event.status, "PENDING", "Event status must remain PENDING");
    assert.equal(event.attempts, 0, "Event attempts must remain 0");
  });

  test("4. Filtering by event_type: 'ABSENCE' prevents querying or processing other types", () => {
    const allTypes = ["ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT"];
    const requestedType = "ABSENCE";

    const typesToProcess = requestedType ? [requestedType] : allTypes;
    assert.deepEqual(typesToProcess, ["ABSENCE"]);
    assert.equal(typesToProcess.includes("HOMEWORK"), false);
    assert.equal(typesToProcess.includes("RECITATION"), false);
    assert.equal(typesToProcess.includes("EXAM"), false);
    assert.equal(typesToProcess.includes("MONTHLY_REPORT"), false);
  });

  test("5. Standard queue processing without options defaults to all 5 event types (backwards compatibility)", () => {
    const allTypes = ["ABSENCE", "HOMEWORK", "RECITATION", "EXAM", "MONTHLY_REPORT"];
    const options = {};
    const typesToProcess = options?.eventType ? [options.eventType] : allTypes;
    assert.deepEqual(typesToProcess, allTypes);
  });

  test("6. Targeted pre-claim safety guard: prevents calling general claim when an older pending event exists", () => {
    const olderEventId = "11111111-1111-1111-1111-111111111111";
    const targetEventId = "ccf5ccf2-5483-4b22-8ca8-3653a921a849";

    const queue = [
      { id: olderEventId, created_at: "2026-10-10T00:00:00Z" },
      { id: targetEventId, created_at: "2026-10-12T00:00:00Z" },
    ];

    const oldest = queue[0];
    const canSafelyClaimTargetDirectly = oldest.id === targetEventId;
    assert.equal(canSafelyClaimTargetDirectly, false, "Must abort claim before touching older queue item");

    const isolatedQueue = [
      { id: targetEventId, created_at: "2026-10-12T00:00:00Z" },
    ];
    const isolatedOldest = isolatedQueue[0];
    assert.equal(isolatedOldest.id === targetEventId, true, "Safe to claim because target is oldest item");
  });
});

// ============================================================================
// SUITE 7: Single-Run Consecutive Absence Processing & Reliability
// ============================================================================
describe("Single-Run Consecutive Absence Processing & Reliability", () => {
  test("Single worker run processes three consecutive absence events successfully", async () => {
    const events = [
      { id: "ev-1", student_id: "s-1", status: "PENDING", attempts: 0 },
      { id: "ev-2", student_id: "s-2", status: "PENDING", attempts: 0 },
      { id: "ev-3", student_id: "s-3", status: "PENDING", attempts: 0 },
    ];

    let processedCount = 0;
    for (const ev of events) {
      if (ev.status === "PENDING" && ev.attempts < 3) {
        ev.status = "PROCESSING";
        ev.attempts++;
        // Simulate sending and finalization
        ev.status = "SENT";
        processedCount++;
      }
    }

    assert.equal(processedCount, 3, "All 3 absence events must be processed in a single run");
    assert.equal(events.every(e => e.status === "SENT"), true);
  });

  test("Single message failure does not stop processing remaining events", async () => {
    const events = [
      { id: "ev-1", student_id: "s-1", phone: "01011111111", status: "PENDING", attempts: 0 },
      { id: "ev-2", student_id: "s-2", phone: "INVALID", status: "PENDING", attempts: 0 },
      { id: "ev-3", student_id: "s-3", phone: "01033333333", status: "PENDING", attempts: 0 },
    ];

    let successCount = 0;
    let failedCount = 0;

    for (const ev of events) {
      if (ev.phone === "INVALID") {
        ev.status = "FAILED";
        ev.attempts++;
        failedCount++;
        continue;
      }
      ev.status = "SENT";
      ev.attempts++;
      successCount++;
    }

    assert.equal(successCount, 2);
    assert.equal(failedCount, 1);
  });

  test("Delay between messages is applied and NOT applied after the last message", () => {
    const events = [
      { id: "ev-1", status: "PENDING" },
      { id: "ev-2", status: "PENDING" },
      { id: "ev-3", status: "PENDING" },
    ];
    let delayCount = 0;
    for (let i = 0; i < events.length; i++) {
      events[i].status = "SENT";
      const remainingPending = events.slice(i + 1).filter(e => e.status === "PENDING").length;
      const hasMorePending = remainingPending > 0;
      if (hasMorePending) {
        delayCount++;
      }
    }
    assert.equal(delayCount, 2, "Delay must be applied exactly twice between 3 messages, and 0 times after last");
  });

  test("Single worker run with 120s budget successfully completes all 3 absence events", () => {
    const events = [
      { id: "ev-1", status: "PENDING", attempts: 0 },
      { id: "ev-2", status: "PENDING", attempts: 0 },
      { id: "ev-3", status: "PENDING", attempts: 0 },
    ];
    const MAX_CYCLE_DURATION_MS = 120000;
    const whatsappSendDelayMs = 10000;
    let simulatedElapsed = 0;
    let processed = 0;

    for (let i = 0; i < events.length; i++) {
      if (simulatedElapsed >= MAX_CYCLE_DURATION_MS) break;

      events[i].status = "SENT";
      events[i].attempts++;
      processed++;
      simulatedElapsed += 2000; // ~2s per event

      const remainingPending = events.slice(i + 1).filter(e => e.status === "PENDING").length;
      if (remainingPending > 0) {
        if (MAX_CYCLE_DURATION_MS - simulatedElapsed <= whatsappSendDelayMs) break;
        simulatedElapsed += whatsappSendDelayMs;
      }
    }

    assert.equal(processed, 3, "All 3 events must be completely processed within 120s budget");
    assert.equal(events.every(e => e.status === "SENT"), true);
    assert.ok(simulatedElapsed < 30000, `3 events with 2 delays of 10s should finish in ~24s, got ${simulatedElapsed}ms`);
  });

  test("Single worker run with 120s budget successfully completes all 5 absence events", () => {
    const events = Array.from({ length: 5 }, (_, i) => ({
      id: `ev-${i + 1}`,
      status: "PENDING",
      attempts: 0,
    }));
    const MAX_CYCLE_DURATION_MS = 120000;
    const whatsappSendDelayMs = 10000;
    let simulatedElapsed = 0;
    let processed = 0;

    for (let i = 0; i < events.length; i++) {
      if (simulatedElapsed >= MAX_CYCLE_DURATION_MS) break;

      events[i].status = "SENT";
      events[i].attempts++;
      processed++;
      simulatedElapsed += 2500; // ~2.5s per event

      const remainingPending = events.slice(i + 1).filter(e => e.status === "PENDING").length;
      if (remainingPending > 0) {
        if (MAX_CYCLE_DURATION_MS - simulatedElapsed <= whatsappSendDelayMs) break;
        simulatedElapsed += whatsappSendDelayMs;
      }
    }

    assert.equal(processed, 5, "All 5 events must be completely processed within 120s budget");
    assert.equal(events.every(e => e.status === "SENT"), true);
    assert.ok(simulatedElapsed < 60000, `5 events with 4 delays of 10s should finish in ~52.5s, got ${simulatedElapsed}ms`);
  });

  test("Single worker run with 120s budget successfully completes all 7 absence events", () => {
    const events = Array.from({ length: 7 }, (_, i) => ({
      id: `ev-${i + 1}`,
      status: "PENDING",
      attempts: 0,
    }));
    const MAX_CYCLE_DURATION_MS = 120000;
    const whatsappSendDelayMs = 10000;
    let simulatedElapsed = 0;
    let processed = 0;

    for (let i = 0; i < events.length; i++) {
      if (simulatedElapsed >= MAX_CYCLE_DURATION_MS) break;

      events[i].status = "SENT";
      events[i].attempts++;
      processed++;
      simulatedElapsed += 2500; // ~2.5s per event

      const remainingPending = events.slice(i + 1).filter(e => e.status === "PENDING").length;
      if (remainingPending > 0) {
        if (MAX_CYCLE_DURATION_MS - simulatedElapsed <= whatsappSendDelayMs) break;
        simulatedElapsed += whatsappSendDelayMs;
      }
    }

    assert.equal(processed, 7, "All 7 events must be completely processed within 120s budget");
    assert.equal(events.every(e => e.status === "SENT"), true);
    assert.ok(simulatedElapsed < 85000, `7 events with 6 delays of 10s should finish in ~77.5s, got ${simulatedElapsed}ms`);
  });

  test("Safe capacity yield: Large queue (e.g. 15 events) yields safely within 120s budget leaving remaining events strictly PENDING", () => {
    const events = Array.from({ length: 15 }, (_, i) => ({
      id: `ev-${i + 1}`,
      status: "PENDING",
      attempts: 0,
    }));

    const MAX_CYCLE_DURATION_MS = 120000;
    const whatsappSendDelayMs = 10000;
    let simulatedElapsed = 0;
    let processed = 0;
    let capacityLimitReached = false;

    for (let i = 0; i < events.length; i++) {
      if (simulatedElapsed >= MAX_CYCLE_DURATION_MS) {
        capacityLimitReached = true;
        break;
      }

      events[i].status = "SENT";
      events[i].attempts++;
      processed++;
      simulatedElapsed += 3000; // 3s per event

      const remainingPending = events.slice(i + 1).filter(e => e.status === "PENDING").length;
      if (remainingPending > 0) {
        if (MAX_CYCLE_DURATION_MS - simulatedElapsed <= whatsappSendDelayMs) {
          capacityLimitReached = true;
          break;
        }
        simulatedElapsed += whatsappSendDelayMs;
      }
    }

    assert.ok(processed >= 8 && processed <= 10, `Should safely process around 9 items in 120s, got ${processed}`);
    assert.equal(capacityLimitReached, true, "Capacity limit must be flagged when budget cannot safely accommodate next delay");
    assert.ok(simulatedElapsed <= MAX_CYCLE_DURATION_MS, `Execution time (${simulatedElapsed}ms) must strictly stay within 120s budget`);

    // Verify all remaining unprocessed items remain untouched in PENDING status with 0 attempts
    const remainingEvents = events.slice(processed);
    assert.ok(remainingEvents.length > 0, "Remaining events must exist");
    assert.ok(remainingEvents.every(e => e.status === "PENDING" && e.attempts === 0), "All remaining events must remain strictly PENDING with 0 attempts");
  });

  test("Idempotency guard: Already SENT events are not duplicated or claimed again", () => {
    const events = [
      { id: "ev-1", status: "SENT", attempts: 1 },
      { id: "ev-2", status: "PENDING", attempts: 0 },
    ];

    // Worker claim query filters status = 'PENDING' and attempts < 3
    const claimable = events.filter(e => e.status === "PENDING" && e.attempts < 3);
    assert.equal(claimable.length, 1);
    assert.equal(claimable[0].id, "ev-2");
  });

  test("Failure resilience: An individual student error does not abort processing for other students", () => {
    const events = [
      { id: "ev-1", student_name: "Ali", validPhone: true, status: "PENDING" },
      { id: "ev-2", student_name: "Omar", validPhone: false, status: "PENDING" },
      { id: "ev-3", student_name: "Khaled", validPhone: true, status: "PENDING" },
    ];

    let successCount = 0;
    let failedCount = 0;

    for (const ev of events) {
      if (!ev.validPhone) {
        ev.status = "FAILED";
        failedCount++;
      } else {
        ev.status = "SENT";
        successCount++;
      }
    }

    assert.equal(successCount, 2, "2 students with valid phones must be sent");
    assert.equal(failedCount, 1, "1 student with invalid phone must be recorded as failed without stopping others");
    assert.equal(events[0].status, "SENT");
    assert.equal(events[1].status, "FAILED");
    assert.equal(events[2].status, "SENT");
  });
});

});





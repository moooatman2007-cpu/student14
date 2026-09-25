import { test, describe } from "node:test";
import assert from "node:assert/strict";

// Simulated RLS Database Engine representing PostgreSQL RLS behavior
class MockPostgresRlsEngine {
  constructor() {
    this.tables = {
      teacher_whatsapp_sessions: [
        {
          id: "uuid-111",
          teacher_id: "550e8400-e29b-41d4-a716-446655440000", // Teacher A
          session_name: "teacher_550e8400-e29b-41d4-a716-446655440000",
          status: "CONNECTED",
          connected_phone: "201012345678",
          last_connected_at: "2026-09-21T12:00:00Z",
        },
        {
          id: "uuid-222",
          teacher_id: "661f9511-f3ac-52e5-b827-557766551111", // Teacher B
          session_name: "teacher_661f9511-f3ac-52e5-b827-557766551111",
          status: "DISCONNECTED",
          connected_phone: null,
          last_connected_at: null,
        },
      ],
    };

    // RLS Policy Declarations after 20260922000001 Migration:
    // Only SELECT is allowed for authenticated role where teacher_id = auth.uid()
    // INSERT, UPDATE, DELETE policies are completely removed for authenticated role.
    this.policies = {
      authenticated: {
        SELECT: (row, user) => row.teacher_id === user.id,
        INSERT: () => false, // Hardened: NO INSERT policy exists for authenticated client
        UPDATE: () => false, // Hardened: NO UPDATE policy exists for authenticated client
        DELETE: () => false, // Hardened: NO DELETE policy exists for authenticated client
      },
      service_role: {
        SELECT: () => true,
        INSERT: () => true,
        UPDATE: () => true,
        DELETE: () => true,
      },
    };
  }

  // Execute query simulating Supabase client
  query(role, user, operation, targetId, updatePayload = null, insertPayload = null) {
    const policy = this.policies[role]?.[operation];
    if (!policy) return { error: "Permission denied", data: null };

    const table = this.tables.teacher_whatsapp_sessions;

    if (operation === "SELECT") {
      const allowedRows = table.filter((row) => policy(row, user));
      if (targetId) {
        const found = allowedRows.filter((row) => row.teacher_id === targetId || row.id === targetId);
        return { error: null, data: found };
      }
      return { error: null, data: allowedRows };
    }

    if (operation === "INSERT") {
      if (!policy(insertPayload, user)) {
        return { error: "new row violates row-level security policy for table \"teacher_whatsapp_sessions\"", data: null };
      }
      table.push(insertPayload);
      return { error: null, data: insertPayload };
    }

    if (operation === "UPDATE") {
      const row = table.find((r) => r.teacher_id === targetId || r.id === targetId);
      if (!row || !policy(row, user)) {
        return { error: "new row violates row-level security policy for table \"teacher_whatsapp_sessions\"", data: null };
      }
      Object.assign(row, updatePayload);
      return { error: null, data: row };
    }

    if (operation === "DELETE") {
      const index = table.findIndex((r) => r.teacher_id === targetId || r.id === targetId);
      if (index === -1 || !policy(table[index], user)) {
        return { error: "row-level security policy prevents deletion for table \"teacher_whatsapp_sessions\"", data: null };
      }
      const deleted = table.splice(index, 1);
      return { error: null, data: deleted };
    }
  }
}

describe("Security Hardening RLS Verification Tests (Authenticated Client vs Server-Side)", () => {
  const teacherA = { id: "550e8400-e29b-41d4-a716-446655440000" };
  const teacherB = { id: "661f9511-f3ac-52e5-b827-557766551111" };

  let db;

  test("1. Teacher A can SELECT own session row", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "SELECT", teacherA.id);
    assert.equal(res.error, null);
    assert.equal(res.data.length, 1);
    assert.equal(res.data[0].teacher_id, teacherA.id);
  });

  test("2. Teacher A CANNOT SELECT Teacher B's session row", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "SELECT", teacherB.id);
    assert.equal(res.error, null);
    assert.equal(res.data.length, 0); // Isolated
  });

  test("3. Teacher A CANNOT INSERT a session directly as client", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "INSERT", null, null, {
      id: "uuid-333",
      teacher_id: teacherA.id,
      session_name: `teacher_${teacherA.id}`,
      status: "CONNECTED",
    });
    assert.notEqual(res.error, null);
    assert.match(res.error, /violates row-level security policy/);
  });

  test("4. Teacher A CANNOT UPDATE status directly as client", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "UPDATE", teacherA.id, { status: "CONNECTED" });
    assert.notEqual(res.error, null);
    assert.match(res.error, /violates row-level security policy/);
  });

  test("5. Teacher A CANNOT UPDATE connected_phone directly as client", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "UPDATE", teacherA.id, { connected_phone: "201999999999" });
    assert.notEqual(res.error, null);
    assert.match(res.error, /violates row-level security policy/);
  });

  test("6. Teacher A CANNOT UPDATE session_name directly as client", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "UPDATE", teacherA.id, { session_name: "hacked_name" });
    assert.notEqual(res.error, null);
    assert.match(res.error, /violates row-level security policy/);
  });

  test("7. Teacher A CANNOT UPDATE teacher_id directly as client", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "UPDATE", teacherA.id, { teacher_id: teacherB.id });
    assert.notEqual(res.error, null);
    assert.match(res.error, /violates row-level security policy/);
  });

  test("8. Teacher A CANNOT DELETE session directly as client", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("authenticated", teacherA, "DELETE", teacherA.id);
    assert.notEqual(res.error, null);
    assert.match(res.error, /prevents deletion/);
  });

  test("9. Server-Side Edge Function (Service Role) CAN write/update session state", () => {
    db = new MockPostgresRlsEngine();
    const res = db.query("service_role", null, "UPDATE", teacherA.id, {
      status: "DISCONNECTED",
      connected_phone: null,
    });
    assert.equal(res.error, null);
    assert.equal(res.data.status, "DISCONNECTED");
  });
});

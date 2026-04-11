// ============================================================
// index.js — Lambda handler for the aicollab cloud context API.
//
// WHAT THIS HANDLER DOES (one sentence):
// Serves GET/POST /context for the aicollab Java client, persisting
// one OrganizationContext document per orgId in MongoDB.
//
// CONTRACT (matches AwsContextSource.java):
//   GET  /context?orgId=<id>
//     headers: x-api-key: <shared>
//     200 -> full OrganizationContext JSON (or {} on first read)
//     401 -> { error: "unauthorized" }
//     400 -> { error: "orgId required" }
//
//   POST /context?orgId=<id>
//     headers: x-api-key: <shared>, Content-Type: application/json
//     body:    full OrganizationContext JSON
//     200 -> { ok: true }
//     4xx  -> { error: "..." }
//
// AUTH MODEL (v1):
// A single shared API key lives in the API_KEY env var. API Gateway's
// usage plan also enforces this key on the edge; we re-check in code
// so the Lambda can't be invoked directly without the header.
// See README.md for the "first thing to harden" notes.
//
// MONGO CONNECTION REUSE:
// Lambdas freeze between invocations — the MongoClient lives in module
// scope so warm starts reuse the same pool. See the AWS docs on
// "reuse the MongoClient across invocations" for context.
// ============================================================

const { MongoClient, ServerApiVersion } = require('mongodb');

// --- module-scoped client so warm invocations reuse the connection ---
let cachedClient = null;

async function getCollection() {
  if (!cachedClient) {
    const uri = process.env.MONGODB_URI;
    if (!uri) throw new Error('MONGODB_URI env var is not set');
    cachedClient = new MongoClient(uri, {
      serverApi: { version: ServerApiVersion.v1, strict: true, deprecationErrors: true },
      // Short timeouts so a bad Atlas network doesn't hold the Lambda hostage.
      serverSelectionTimeoutMS: 5_000,
      connectTimeoutMS: 5_000,
    });
    await cachedClient.connect();
  }
  const dbName = process.env.DB_NAME || 'aicollab';
  return cachedClient.db(dbName).collection('org_context');
}

// ============================================================
// Entry point. Works under both REST API Gateway (event.httpMethod)
// and HTTP API v2 (event.requestContext.http.method).
// ============================================================
exports.handler = async (event) => {
  const method = event.httpMethod
               || (event.requestContext && event.requestContext.http && event.requestContext.http.method)
               || 'GET';
  const rawPath = event.path || event.rawPath || '';
  const query   = event.queryStringParameters || {};
  const headers = normalizeHeaders(event.headers || {});

  // ---------- auth ----------
  const providedKey = headers['x-api-key'];
  const expectedKey = process.env.API_KEY;
  if (!expectedKey) {
    return json(500, { error: 'server misconfigured: API_KEY unset' });
  }
  if (!providedKey || providedKey !== expectedKey) {
    return json(401, { error: 'unauthorized' });
  }

  // Only /context is exposed; everything else 404s.
  if (!rawPath.endsWith('/context')) {
    return json(404, { error: 'not found' });
  }

  try {
    const col = await getCollection();

    if (method === 'GET') {
      const orgId = query.orgId;
      if (!orgId) return json(400, { error: 'orgId required' });

      const doc = await col.findOne({ _id: orgId });
      if (!doc || !doc.context) {
        // First read for a brand new org: return an empty object so the
        // Java client can deserialize it to a fresh OrganizationContext.
        return json(200, {});
      }
      return json(200, doc.context);
    }

    if (method === 'POST') {
      const orgId = query.orgId;
      if (!orgId) return json(400, { error: 'orgId required' });

      let parsed;
      try {
        parsed = JSON.parse(event.body || '{}');
      } catch (e) {
        return json(400, { error: 'invalid JSON body' });
      }

      await col.updateOne(
        { _id: orgId },
        {
          $set: {
            context: parsed,
            updatedAt: new Date(),
          },
          $setOnInsert: { createdAt: new Date() },
        },
        { upsert: true },
      );

      return json(200, { ok: true });
    }

    return json(405, { error: 'method not allowed' });

  } catch (err) {
    console.error('[context-api] handler error:', err);
    return json(500, { error: err.message || 'internal error' });
  }
};

// ---------- helpers ----------

function json(statusCode, body) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

// API Gateway lower-cases headers under HTTP API v2 but preserves case
// under REST. Normalize to lowercase so the handler is consistent.
function normalizeHeaders(raw) {
  const out = {};
  for (const k of Object.keys(raw)) out[k.toLowerCase()] = raw[k];
  return out;
}

# cloud/context-api — aicollab shared context backend

Thin AWS Lambda + API Gateway HTTP API that persists one
`OrganizationContext` document per `orgId` in MongoDB. Paired with
`AwsContextSource` on the Java client side — when
`aws.context.enabled=true` in `config.properties`, the Java app reads
and writes shared team context through this service instead of the
local `org_context.json` file.

## Contract

| Method | Path                    | Headers                    | Body                               | Response            |
|--------|-------------------------|----------------------------|------------------------------------|---------------------|
| GET    | `/context?orgId=<id>`   | `x-api-key: <shared>`      | —                                  | 200 + full context JSON (or `{}` on first read) |
| POST   | `/context?orgId=<id>`   | `x-api-key: <shared>`, `Content-Type: application/json` | full `OrganizationContext` JSON    | 200 + `{ok:true}` |

All other paths return 404. Missing/invalid `x-api-key` returns 401.
Missing `orgId` returns 400.

## MongoDB layout

Database: `aicollab` (override via `DB_NAME` env var).
Collection: `org_context`.
One document per organization, keyed by `_id = orgId`:

```json
{
  "_id": "acme-eng-leads",
  "context": { /* full OrganizationContext JSON */ },
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}
```

No indexes beyond the default `_id` — lookups are always by that key.

## Environment variables

| Name          | Purpose                                                  |
|---------------|----------------------------------------------------------|
| `MONGODB_URI` | Full MongoDB connection string (Atlas `mongodb+srv://…`) |
| `DB_NAME`     | DB name, defaults to `aicollab`                          |
| `API_KEY`     | Shared key the Java client sends as `x-api-key`          |

## Deploy with SAM

Prereqs: AWS CLI, AWS SAM CLI, Node.js 20+.

```sh
cd cloud/context-api
npm install          # pulls in the mongodb driver
sam build
sam deploy --guided  # first time only — answers are saved to samconfig.toml
```

Guided prompts to answer:

- **MongoDbUri** → your Atlas connection string
- **DbName** → `aicollab` (or whatever)
- **SharedApiKey** → a long random string; paste the same value into
  the Java `config.properties` under `aws.context.apiKey`
- **Confirm changes before deploy** → yes, so you see the IAM diff
- **Allow SAM CLI IAM role creation** → yes

After deploy, the stack output `ContextApiUrl` is the base URL you
paste into `aws.context.url` (no trailing `/context` — the Java client
appends the path).

Subsequent deploys:

```sh
sam build && sam deploy
```

## Wire the Java client

In `config.properties`:

```
aws.context.enabled=true
aws.context.url=https://abcd1234.execute-api.us-east-1.amazonaws.com
aws.context.apiKey=<same value you passed to SharedApiKey>
aws.context.orgId=acme-eng-leads
```

Restart the app. `MainGui.initApplication` will swap in
`AwsContextSource` instead of `LocalContextSource`. Everything else —
`ContextController`, all agentic tasks, the reconciliation service —
keeps working unchanged.

## Local testing without AWS

You can exercise the handler without deploying it. Install deps and
hand it a fake API Gateway event:

```sh
cd cloud/context-api
npm install
export MONGODB_URI="mongodb://localhost:27017"
export DB_NAME="aicollab"
export API_KEY="dev-shared-key"

node -e '
const h = require("./index.js").handler;
(async () => {
  const getEvt = {
    httpMethod: "GET", path: "/context",
    queryStringParameters: { orgId: "test-org" },
    headers: { "x-api-key": "dev-shared-key" }
  };
  console.log(await h(getEvt));

  const postEvt = {
    httpMethod: "POST", path: "/context",
    queryStringParameters: { orgId: "test-org" },
    headers: { "x-api-key": "dev-shared-key" },
    body: JSON.stringify({ topPriorities: { value: "ship v1" } })
  };
  console.log(await h(postEvt));
})();
'
```

## Security posture (v1)

This is a **trusted-team** deployment model. The shared API key is
good enough for a small team where everyone already has access to
the same shared context anyway — losing the key is no worse than
losing the Git repo it lives next to.

**First thing to harden** when this goes beyond a trusted team:

1. Swap shared-key auth for per-user Cognito or SigV4 auth. The
   Java client already has a natural place to attach credentials
   (the `AwsContextSource` constructor), so this is a localized
   change.
2. Replace the single shared document model with per-user
   write-layer + merge-on-read, so one user can't clobber another's
   in-flight edits. The reconciliation service on the Java side
   already handles the merge semantics — the server just needs to
   store per-user deltas instead of the full blob.
3. Add rate limiting via an API Gateway usage plan.
4. Turn on CloudWatch request logs and a simple dashboard so abuse
   is visible.

None of these are required for the class project / POC.

## Known limitations

- **Cold starts**: Lambda + Atlas cold starts can be ~1–2s on a free
  Atlas tier. Acceptable for v1; provisioned concurrency is the
  escape hatch if it becomes annoying.
- **Whole-document writes**: POST replaces the full context blob. No
  optimistic concurrency. Two users saving simultaneously: last
  write wins. The reconciliation service mitigates this by only
  writing diffs in practice.
- **No schema validation**: the handler stores whatever the Java
  client sends. Gson on both ends keeps the shape consistent — no
  need to duplicate the schema in Node.

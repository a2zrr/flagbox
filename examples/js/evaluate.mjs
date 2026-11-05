// FlagBox example client. Plain Node 18+, no dependencies.
//
//   FLAGBOX_CLIENT_KEY=fbx_client_... node evaluate.mjs [flagKey] [userId...]
//
// If FlagBox answers FLAG_NOT_FOUND, or the request fails, the app uses its own
// local default. A flag that is unknown to the server is not an error.

const baseUrl = process.env.FLAGBOX_URL ?? 'http://localhost:8080';
const clientKey = process.env.FLAGBOX_CLIENT_KEY;
if (!clientKey) {
  console.error('Set FLAGBOX_CLIENT_KEY to a CLIENT key for the environment.');
  process.exit(1);
}

const [flagKey = 'new-checkout', ...userIds] = process.argv.slice(2);
const users = userIds.length > 0 ? userIds : ['user-1', 'user-2', 'user-3', 'user-4', 'user-5'];

// The app's own fallback. Used whenever FlagBox cannot give an answer.
const LOCAL_DEFAULT = false;

async function evaluate(userId) {
  try {
    const response = await fetch(`${baseUrl}/api/v1/evaluate`, {
      method: 'POST',
      headers: { 'X-API-Key': clientKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ flagKey, context: { userId } }),
      signal: AbortSignal.timeout(2000),
    });
    if (!response.ok) {
      return { value: LOCAL_DEFAULT, reason: `HTTP ${response.status}`, source: 'local default' };
    }
    const body = await response.json();
    if (body.reason === 'FLAG_NOT_FOUND') {
      return { value: LOCAL_DEFAULT, reason: body.reason, source: 'local default' };
    }
    return { value: body.value, reason: body.reason, source: 'flagbox' };
  } catch (error) {
    // fetch wraps network errors in a TypeError whose cause holds the real code, e.g. ECONNREFUSED.
    return { value: LOCAL_DEFAULT, reason: error.cause?.code ?? error.name, source: 'local default' };
  }
}

for (const userId of users) {
  const result = await evaluate(userId);
  console.log(`${userId.padEnd(12)} value=${String(result.value).padEnd(5)} reason=${result.reason.padEnd(14)} (${result.source})`);
}

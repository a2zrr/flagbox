# FlagBox example client

Evaluates a flag for a few users in plain Node 18+ (no dependencies), falling back to a local default when FlagBox can't answer.
Git Bash: `FLAGBOX_URL=http://localhost:8080 FLAGBOX_CLIENT_KEY=fbx_client_... node evaluate.mjs new-checkout user-1 user-2`
PowerShell: `$env:FLAGBOX_URL="http://localhost:8080"; $env:FLAGBOX_CLIENT_KEY="fbx_client_..."; node evaluate.mjs new-checkout user-1 user-2`
`FLAGBOX_URL` defaults to `http://localhost:8080`; `FLAGBOX_CLIENT_KEY` is required and must be a CLIENT key.
`FLAG_NOT_FOUND` and unreachable-server errors print the local default, not a failure.

# GitHub checks and manual internal releases

`checks.yml` runs automatically on pull requests to `main` and pushes to `main`.
It uses no environment secrets or OIDC permission. It builds the ordinary key-free
app, runs fixture-only helper and app unit tests, checks lint, and inspects the
release bundle for an empty weather-key field/resource and absent fixture screens.
No APK/AAB or cache is uploaded. A standard GitHub-hosted Ubuntu runner is used.

`internal-play.yml` is a manual `workflow_dispatch` workflow. It must be on the
repository's default branch before the Run workflow button becomes available.
It verifies the app before entering the protected `play-internal` environment.
The environment permits `main` only, requires the owner's review, and does not
allow administrators to bypass approval. Workflow-level permissions are read-only;
only the private job can request an OIDC token. Actions are pinned to reviewed
commits from GitHub and Google. SDK packages come from the official SDK manager;
the workflow does not automatically accept new license agreements.

## Private build inputs

The protected environment holds these previously approved secret names:

- `WEATHERAPP_UPLOAD_KEYSTORE_B64`: dedicated existing upload keystore.
- `WEATHERAPP_UPLOAD_PASSWORD`: its password.
- `WEATHERAPP_WEATHER_API_KEY`: approved provider key for private internal tests.

The public upload certificate fingerprint and alias are pinned in
`signing-public.json`. No keystore, password, provider key or local path belongs
in source control. Ordinary local release builds remain key-free and ignore the
local debug configuration. The optional CI init script sets a new version code
and can embed the weather key only in the explicitly selected trusted release
build. It does not change the tracked app build configuration.

The weather key is **extractable** from an installed app, even on internal testing.
Embedding it is an explicit private-testing choice, not a secret-storage mechanism.
A future public release needs a separate credential/exposure decision.

Private inputs are written inside an owner-only temporary directory. Private build
and signing diagnostics are captured rather than printed to public Actions logs.
Build/configuration caches are disabled. The always-run cleanup removes only this
job's temporary inputs, app build outputs and dedicated Gradle home. GitHub
runner disposal is the fallback if a runner terminates before cleanup. Neither
workflow publishes downloadable GitHub artifacts. Build-only mode discards its
private bundle; it is not an artifact distribution channel.

## Publishing identity: separate approval required

Publishing stays disabled until an explicitly approved Google identity and Play
permissions are configured. No long-lived Google JSON key is needed. The proposed
identity uses a dedicated service account and Workload Identity Federation provider
with GitHub's issuer `https://token.actions.githubusercontent.com`. Restrict trust
to this repository's immutable ID, owner ID and actor ID, manual dispatch, `main`,
this workflow's exact path/ref, and the `play-internal` environment. Grant
`roles/iam.workloadIdentityUser` on that service account only, to the
repository-specific principal set. Do not give it project-wide Editor/Owner,
Token Creator or domain-wide delegation.

In Play Console, give the service-account email access to **Weatherapp only**:

- View app information (read-only), `CAN_VIEW_NON_FINANCIAL_DATA`.
- Release apps to testing tracks, `CAN_MANAGE_TRACK_APKS`.

Google's latter permission covers testing tracks generally. The publisher narrows
its own behavior to the constant `internal` track; it never edits production,
store listings, policy declarations, account settings or tester lists. Grant no
production, financial, administrator or tester-management permissions.

Google's setup guide lists billing as a prerequisite. Eligibility with billing
disabled must be checked during the separately approved setup; stop if Google
requires billing or payment. Do not silently enable billing or additional APIs.
The documented setup APIs are IAM, Resource Manager, IAM Credentials and STS,
plus Android Publisher for Play uploads. Service enablement and the persistent
federation/Play grants require their own concrete approval.

After that setup is approved and verified, the protected environment needs these
nonsecret variables (use the actual approved resources, not guessed values):

- `PLAY_GCP_PROJECT_ID`
- `PLAY_WIF_PROVIDER`: full provider resource name with numeric project number.
- `PLAY_SERVICE_ACCOUNT`
- `PLAY_APP_BOOTSTRAPPED=true`: the first manual Console upload is complete.
- `PLAY_PUBLISH_READY=true`: only after trust and Play app permissions are verified.

An initial Console upload already bootstrapped this app at version code **1**.
Future codes must be integers from **2** through 2,100,000,000, higher than every
existing uploaded/released version. The publisher reads bundle and track versions
before uploading; never reuse a code after an uncertain upload.

## Manual run and verification

Select `main`, explicitly enter a fresh version code greater than every uploaded
version (the required field has no default), decide whether to include the approved
weather key, and explicitly select `upload_to_internal`. Upload defaults to false.
Before publishing, inspect the current internal tester list in Play Console and
confirm that it still contains only the approved owner account. The workflow
requires that confirmation; it does not claim to verify email-list membership
through an API and it cannot modify testers with its proposed permissions.

After owner environment approval, the job builds and validates the private bundle,
checks its upload certificate, package, version, nondebuggable state and selected
key mode. Only then does Google's pinned auth action request a five-minute
Android Publisher access token. It writes no credential file. The publisher
uploads once, updates only `internal`, validates and commits the edit once, then
reads back the completed version through a new edit and deletes that readback edit.
A failed/uncertain commit is not retried automatically. Inspect Console state
before any subsequent dispatch; a timeout is not proof that the upload failed.

No release dispatch is part of adding these workflows. Local fixture checks do
not prove Google federation/Play access works. Device tests and live provider
requests are separate from automatic CI; app unit tests use fixtures/fakes.

References:

- [GitHub OIDC claims and trust conditions](https://docs.github.com/actions/reference/openid-connect-reference)
- [Google federation setup](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [Google's official auth action](https://github.com/google-github-actions/auth)
- [Play app-level permissions](https://developers.google.com/android-publisher/api-ref/rest/v3/grants)
- [Play edits and first-upload requirements](https://developers.google.com/android-publisher/edits)

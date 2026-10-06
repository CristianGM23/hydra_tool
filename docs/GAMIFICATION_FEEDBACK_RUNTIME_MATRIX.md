# HYDRA gamification feedback runtime matrix

R5C13 Fix 11 separates verified gamification semantics from host presentation. The canonical ledger remains the durable source of truth; `UserFeedbackEnvelope` is projected deterministically from that ledger, so UI delivery can be retried without granting an award twice.

| Host | Immediate feedback | Persistent surface | Failure behaviour |
|---|---|---|---|
| HYDRA Web model editor | toast/status feedback | Activity drawer | polling/presentation failures are isolated from model editing |
| Standalone Web editor | toast feedback | Gamification/activity panel | localStorage fallback uses model annotations; bounded feedback history |
| VS Code / Langium generated hosts | `notifyFeedback` bridge | host-defined view/activity | optional bridge; missing/throwing host callback does not roll back semantic commit |
| Eclipse / Sirius / GLSP generated Java hosts | `Host.notifyFeedback` | host-defined view/status | default no-op method preserves compatibility |
| Python/BESSER generated hosts | optional `notify_feedback` | host-defined UI/log | missing callback is ignored |

## Canonical contract

`UserFeedbackEnvelope` carries a stable id, semantic scope, actor, kind, severity, title/message/reason/next-step, points delta, achievement/challenge/reward/level ids, progress, source event/correlation ids, presentation policy, actions and metadata.

Presentation is controlled by annotations with safe defaults:

- `hydra.feedback.enabled`
- `hydra.feedback.surfaces` (`TOAST,ACTIVITY,STATUS,PROGRESS,DIALOG`)
- `hydra.feedback.durationMillis`
- `hydra.feedback.persistActivity`
- `hydra.feedback.showPoints`
- `hydra.feedback.showProgress`
- `hydra.feedback.maxVisible`
- `hydra.feedback.dedupeWindowSeconds`
- `hydra.feedback.aggregateWindowMillis`
- `hydra.feedback.includeRejected`

A rule can override the global policy with `hydra.feedback.rule.<ruleId>.<property>`. Invalid policy values fall back or clamp to bounded values.

## Reliability rules

1. Granting, penalties and compensations commit before presentation.
2. Feedback is reconstructed from the compensable ledger instead of using a second mutable outbox.
3. Stable feedback ids make polling and host retries idempotent.
4. Host presentation errors are isolated and logged; they cannot undo semantic state.
5. Runtime event depth and queue bounds prevent feedback/event cycles from exhausting a host.
6. Web surfaces bound visible toasts and standalone history.
7. Rejected awards are silent by default and can be enabled explicitly.

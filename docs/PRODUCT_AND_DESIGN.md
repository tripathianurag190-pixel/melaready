# Product and design direction

## Problem

At an event, a message being sent does not mean someone received it, took responsibility or followed it through. Teams also need to practise these responsibilities before an event. MelaReady starts with a shared venue context and role-based communication rehearsal.

## Proposed positioning

**Event preparation and rehearsal for volunteer teams, with a path to accountable live coordination.**

Primary intended hackathon fit: disaster preparedness/mitigation and mass gatherings such as Kumbh. Secondary fit: education and skills through practice. The current prototype is specifically preparation and a communication drill; it does not simulate physical crowd safety or generate operational evacuation instructions.

## Screen hierarchy

| Destination | Purpose | Current implementation |
|---|---|---|
| Home | Understand event and next actions | Event summary, planning estimate, preparation and drill links |
| Team | Understand responsibilities | Local roster, display name, role, zone |
| Practice | Rehearse decisions | Three-step communication scenario and feedback |
| Map | Understand place and location limits | Fictional sketch plus separate device snapshot |
| Drill log | Follow a reported observation | Local simulated state transitions |
| Checklist | Prepare with sources | Local checks, official external portals |
| Voice | Short joined-team clips in connected mode | Requires login, microphone consent and the bundled server |

## Visual rules

Use one primary action per section. Keep Home spacious. Never place tracking, roster, training, permits and voice controls into one dense dashboard. Let text wrap and cards grow. Use colour with text, not as the sole status cue. Keep emergency claims out of decorative UI.

Ivory / dark teal / soft sage / saffron form the palette. Prefer quiet borders to heavy shadows. The custom M mark is a simple original geometric vector. The preview is a design reference; render and review the native UI on devices before design sign-off.

## Inspiration, without copying

The product combines common interaction patterns: press-to-talk channels, task acknowledgement, small role-based exercises and event preparation lists. Differentiation should come from a venue-specific practice-to-live workflow with transparent message delivery and responsibility, rather than the claim that any one feature has never existed.

Do not reuse competitor logos, code, illustrations, exact layouts or marketing text. Do not claim world-first novelty or guaranteed hackathon results.

## Later features, in order

1. Authenticated event membership, roles and remote incident state. Server-enforced access, audit trail, retries and explicit delivery states.
2. One event voice channel tested on two phones, with server-issued short-lived tokens, microphone consent, reconnect and clear network dependence.
3. Opt-in location sharing with expiry, reported accuracy, freshness and a stop-sharing control. Permission boundaries for volunteers and leads.
4. Trainer-reviewed scenarios using the actual event's verified venue plan, and supervised practice sessions.
5. Authority documents and official alerts with source, date, jurisdiction and verification status. Do not infer a permit checklist from GPS alone.
6. Load tests and operational agreements before any large-event claim. Subdivide event teams and channels; never broadcast everyone's precise location to an entire gathering.

## Reference entry points

- MY Bharat: https://mybharat.gov.in/pages/hack-for-social-cause
- Android Compose: https://developer.android.com/develop/ui/compose
- Android location permissions: https://developer.android.com/develop/sensors-and-location/location/permissions
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- NDMA SACHET: https://sachet.ndma.gov.in/
- Haryana Police: https://haryanapolice.gov.in/

Links are reference entry points, not integrations, affiliations or permissions. Current competition rules and authority requirements must be checked at submission/deployment time.

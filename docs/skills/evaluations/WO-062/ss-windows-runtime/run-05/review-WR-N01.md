# C5 review — WR-N01

## Verdict: BLOCKED

The C5 session returned normally in **357.235 seconds**, without watchdog expiration or
automatic retry. It used an actual Windows PowerShell **5.1 Desktop** conductor. Its separate
preflight registered that runtime before any fixture fingerprint, confirmed its own dependency
surface and .NET SHA-256 fallback, and launched no Java process.

The normal invocation then discovered and used direct Java 25. It observed `failure` under a
retained Process object and copied native code **23** immediately. The captured fixture output
contains the fresh UUID, PID and `café équipe`. These are useful partial observations, but they
do not qualify the required two-mode behaviour.

The unchanged conductor recorded `image=null` without an image-observation error, then its
`Assert-ProbeIdentity` rejected `failure`. It therefore did not start `sleep`. There is no
observation of a live process at 1,500 ms after READY, no owned forced stop, and no corresponding
five-second exit confirmation. The oracle makes each of those facts mandatory.

The conductor cleaned its own two UUID roots and its independent postflight found those roots,
their temp subdirectories and the owned conductor/JVM PIDs absent. The outer collector still
records `RuntimeRootResidue` because the empty `output/runtime` parent directory exists. That
mechanical collection issue does not change the substantive block and does not convert the
partial `failure` result into a PASS.

The response makes these limits explicit and does not claim a complete qualification. This is a
**postfreeze review by the executing agent**, not an independent review or a human validation.
No candidate-body regression is demonstrated. A future run needs a separately authorised,
corrected conductor and fresh evidence for both modes.

Raw frozen evidence: `.tmp/wo062-evaluations/ss-windows-runtime/run-05-host-formal-20260916/frozen/WR-N01`.

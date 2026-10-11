# 86Box Android v0.9.15 – Android NDK CPU-Affinity Build-Fix

## Behoben

Der v0.9.14 Performance-Affinity-Code verwendete `cpu_set_t`, `CPU_ZERO`, `CPU_ISSET`, `CPU_SET`, `sched_getaffinity()` und `sched_setaffinity()`. Diese GNU-Schnittstellen waren im Android-NDK-Build nicht sichtbar und ließen `android_bridge.c` beim ARM64-Build abbrechen.

v0.9.15 verwendet deshalb eine eigene kleine CPU-Bitmaske und ruft Android/Linux direkt über `SYS_sched_getaffinity` und `SYS_sched_setaffinity` auf. Die Laufzeitfunktion bleibt gleich: Balanced stellt die ursprüngliche CPU-Maske wieder her; Performance/Maximum bevorzugen die leistungsstärkeren CPU-Kerne.

ThinLTO, Universal ARM64, Auto/Balanced/Performance/Maximum sowie alle Touch-, Maus- und CD/Lifecycle-Fixes bleiben erhalten.

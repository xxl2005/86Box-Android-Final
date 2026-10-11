# 86Box Android v0.9.32 – ARM64 New-Dynarec Hot-Path

Diese Version optimiert gezielt den seriellen ARM64-New-Dynarec-Pfad.

- Android ARM64: logischer Codeblock-Cache von 32K auf 64K Eintraege erweitert.
- CPU-State-Pointer wird beim Eintritt in einen kompilierten Block als AArch64-Argument in X0 uebergeben und nach X29 kopiert; die bisherige absolute Pointer-Materialisierung pro Blockeintritt entfällt.
- readlookup2/writelookup2 werden in den zentralen ARM64-Speicher-Helfern per LDR-Literal geladen statt pro Gast-Speicherzugriff mit mehreren MOVZ/MOVK-Instruktionen aufgebaut.
- MOVX_IMM startet allgemein beim ersten nicht-null 16-Bit-Halfword und spart damit bei sparsamen/alignten 64-Bit-Konstanten eine unnoetige MOVZ-Instruktion.
- Keine Aenderung an Gast-CPU-Semantik, CPU-Modell, Timingtabellen, ADPF, Thread Assist oder DOSBox-Pure-Smooth-v2-Regler.

Ziel ist mehr Rohleistung bei 200-MHz-Gaesten, ohne die emulierte CPU-Frequenz kuenstlich abzusenken. Die reale Verbesserung muss auf dem Zielgeraet gemessen werden.

# Folio icons

`sheet-1.webp` and `sheet-2.webp` are the source sheets (9 × 12 each). The app's icons in
`app/src/main/java/app/folio/ui/icons/FolioIcons.kt` are traced from them:

1. `python3 trace.py` cuts each sheet into cells, thickens the lines a little (filled icons
   excepted) and traces every cell to a vector path on a 24 × 24 grid, into `traced.json`.
2. `python3 gen.py` picks the cells Folio uses, names them after the Material icons they
   replaced, and writes `FolioIcons.kt`.

Needs `pip install potracer scipy pillow numpy`. To change an icon, regenerate its sheet,
run both scripts, and check the result in the app.

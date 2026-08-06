# Canvases

Interactive Cursor canvases live here in the repo for sharing, but the IDE only
loads `.canvas.tsx` files from your **local** Cursor projects directory:

```text
~/.cursor/projects/<workspace>/canvases/
```

## Install the migration shim deck (local machine)

From a **local** Agent chat (not Cloud), ask Cursor to place the deck, or run:

```bash
# Discover your workspace canvases folder
ls ~/.cursor/projects/*/canvases 2>/dev/null

# Copy (adjust the projects path if you have more than one workspace)
cp canvases/migration-shim-deck.canvas.tsx ~/.cursor/projects/*/canvases/
```

Then open [migration-shim-deck.canvas.tsx](migration-shim-deck.canvas.tsx) from
that local `canvases/` folder beside chat.

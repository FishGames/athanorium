# TODO

Placeholders and planned work. Everything listed here has to be done or deliberately dropped before a 1.0 release.

## Placeholders

- Mod name "Athanorium" is a working name.
- License: `gradle.properties` says "All Rights Reserved", the GitHub repository was planned as MIT. Decide and make both match.
- All GUI sprites and block textures are generated placeholders (`tools/gen_textures.py`). Replace them with hand drawn ones. Delete the script when the last sprite is replaced.
- `core/score/YieldFormula.java` is a simple placeholder. Balancing comes later.
- Part values in `data/athanorium/part_value/` are first guesses.
- Example puzzles have no reference solutions yet.

## Planned features

- Puzzle editor (phase 1c).
- Triple bond (`core/part/BondType.java` is prepared for more types).
- Yield display in the GUI was removed for now and may come back.
- Reading puzzle and solution files from other sources, with the renamed part ids (`bind`, `multi_bind`, `unbind`). Anything called "bonder" elsewhere maps to "bind".

## Checks still open

- Mouse interaction in the GUI has only been tried by hand, not covered by an automated test.
- Multiplayer, rate limit and split packets on a real server with two players.

## Ports

- Newer Minecraft versions.
- NeoForge instead of Forge. Keep loader specific code out of `common`.

## Release

- Write the README, then rewrite it by hand so it reads like it was written by a person.
- Update the planning documents, "Bonder" still appears there.

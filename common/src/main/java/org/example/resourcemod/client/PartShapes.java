package org.example.resourcemod.client;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.molecule.Molecule;
import org.example.resourcemod.core.part.PartType;
import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/** Which hexes a placed part covers; used for drawing, picking and marking parts outside the field. */
final class PartShapes {
    private PartShapes() {
    }

    /** The molecule an input or output shows, turned by the part's rotation; null for other parts. */
    static Molecule molecule(Part part, Puzzle puzzle, Registries reg) {
        PartType pt = reg.parts.get(part.type());
        if (pt == null || puzzle == null) {
            return null;
        }
        if (pt.kind() == PartType.Kind.OUTPUT) {
            return puzzle.product().rotated(Math.floorMod(part.rotation(), 6));
        }
        if (pt.kind() == PartType.Kind.INPUT && part.inputIndex() >= 0 && part.inputIndex() < puzzle.inputs().size()) {
            return puzzle.inputs().get(part.inputIndex()).rotated(Math.floorMod(part.rotation(), 6));
        }
        return null;
    }

    static List<Hex> hexes(Part part, Puzzle puzzle, Registries reg) {
        List<Hex> out = new ArrayList<>();
        PartType pt = reg.parts.get(part.type());
        if (pt == null) {
            out.add(part.pos());
            return out;
        }
        switch (pt.kind()) {
            case INPUT, OUTPUT -> {
                Molecule m = molecule(part, puzzle, reg);
                if (m == null) {
                    out.add(part.pos());
                } else {
                    for (Molecule.Atom a : m.atoms()) {
                        out.add(part.pos().plus(a.pos()));
                    }
                }
            }
            case TRACK -> {
                for (Hex h : part.trackHexes()) {
                    out.add(part.pos().plus(h));
                }
            }
            case GLYPH -> {
                out.add(part.pos());
                for (Hex h : pt.footprintBesidesOrigin()) {
                    out.add(part.pos().plus(h.rotateCcw(Math.floorMod(part.rotation(), 6))));
                }
            }
            default -> out.add(part.pos());
        }
        return out;
    }
}

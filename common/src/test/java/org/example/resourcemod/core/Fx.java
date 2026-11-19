package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.molecule.Molecule;
import org.example.resourcemod.core.part.BondType;
import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.registry.Registries;
import org.example.resourcemod.core.score.PartValue;
import org.example.resourcemod.core.score.PartValues;
import org.example.resourcemod.core.sim.SimResult;
import org.example.resourcemod.core.sim.Simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helpers: short builders for puzzles, parts and programs. */
final class Fx {
    static final Registries REG = Registries.createDefault();

    private Fx() {
    }

    static Hex h(int q, int r) {
        return new Hex(q, r);
    }

    /** Single atom molecule. */
    static Molecule atom(String element) {
        return new Molecule(List.of(new Molecule.Atom(Id.of(element), Hex.ZERO)), List.of());
    }

    /** Molecule from "element:q,r" atoms and "q,r>q,r" bonds. */
    static Molecule mol(String[] atoms, String... bonds) {
        List<Molecule.Atom> a = new ArrayList<>();
        for (String s : atoms) {
            String[] parts = s.split(":");
            String[] c = parts[1].split(",");
            a.add(new Molecule.Atom(Id.of(parts[0]), h(Integer.parseInt(c[0]), Integer.parseInt(c[1]))));
        }
        List<Molecule.Bond> b = new ArrayList<>();
        for (String s : bonds) {
            String[] ends = s.split(">");
            String[] f = ends[0].split(",");
            String[] t = ends[1].split(",");
            b.add(new Molecule.Bond(h(Integer.parseInt(f[0]), Integer.parseInt(f[1])),
                    h(Integer.parseInt(t[0]), Integer.parseInt(t[1])), BondType.NORMAL));
        }
        return new Molecule(a, b);
    }

    static Puzzle puzzle(List<Molecule> inputs, Molecule product, int testProducts) {
        return new Puzzle(1, null, null, inputs, product, null, null, Id.of("cycles"), 10.0, testProducts, null, null);
    }

    static Part input(int index, Hex pos, int rotation) {
        return Part.input(Id.of("input"), pos, rotation, index);
    }

    static Part output(Hex pos, int rotation) {
        return Part.output(Id.of("output"), pos, rotation);
    }

    static Part glyph(String type, Hex pos, int rotation) {
        return Part.glyph(Id.parse(type), pos, rotation);
    }

    static Part track(Hex pos, Hex... hexes) {
        return Part.track(Id.of("track"), pos, List.of(hexes));
    }

    /** Arm with instructions given as "slot=instruction" pairs. */
    static Part arm(String type, Hex pos, int rotation, int length, String... instr) {
        Map<Integer, Id> tape = new LinkedHashMap<>();
        for (String s : instr) {
            String[] kv = s.split("=");
            tape.put(Integer.parseInt(kv[0]), Id.parse(kv[1]));
        }
        return Part.arm(Id.parse(type), pos, rotation, length, tape);
    }

    static Program program(Part... parts) {
        return new Program(List.of(parts));
    }

    static PartValues values() {
        PartValues v = new PartValues();
        for (String t : new String[]{"arm", "double_arm", "triple_arm", "hex_arm", "piston_arm", "bind",
                "multi_bind", "unbind", "disposer"}) {
            v.put(Id.of(t), new PartValue(10, false));
        }
        v.put(Id.of("track"), new PartValue(5, true));
        return v;
    }

    static SimResult run(Puzzle puzzle, Program program, HexField field, int limit) {
        return Simulation.simulate(puzzle, program, field, REG, limit);
    }
}

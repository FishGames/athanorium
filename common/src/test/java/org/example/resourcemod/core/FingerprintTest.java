package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.puzzle.Fingerprint;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.puzzle.PuzzleReader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.example.resourcemod.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintTest {
    private static final String A = "{\"element\":\"earth\",\"pos\":[0,0]}";
    private static final String B = "{\"element\":\"fire\",\"pos\":[0,0]}";

    private static String json(String name, String firstInput, String secondInput, boolean reordered) {
        String inputs = "\"inputs\":[{\"atoms\":[" + firstInput + "]},{\"atoms\":[" + secondInput + "]}]";
        String product = "\"product\":{\"atoms\":[" + A + "]}";
        String scoring = "\"scoring\":{\"reference\":10,\"metric\":\"cycles\"}";
        String nameField = name == null ? "" : "\"name\":\"" + name + "\",";
        return reordered
                ? "{" + scoring + "," + product + "," + inputs + "," + nameField + "\"format\":1}"
                : "{\"format\":1," + nameField + inputs + "," + product + "," + scoring + "}";
    }

    private static Puzzle load(String json) {
        return PuzzleReader.read(json, "p.json", REG, new ArrayList<>());
    }

    private static Program program(int firstIndex, int secondIndex) {
        return Fx.program(input(firstIndex, h(1, 0), 0), input(secondIndex, h(-1, 0), 0),
                output(h(0, 2), 0), arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"));
    }

    @Test
    void fingerprintIgnoresKeyOrderAndNames() {
        Puzzle p1 = load(json("One", A, B, false));
        Puzzle p2 = load(json("Something else", A, B, true));
        Puzzle p3 = load(json(null, A, B, false));
        String f = Fingerprint.solvability(p1, program(0, 1), HexField.bounded(3), REG);
        assertEquals(f, Fingerprint.solvability(p2, program(0, 1), HexField.bounded(3), REG));
        assertEquals(f, Fingerprint.solvability(p3, program(0, 1), HexField.bounded(3), REG));
        assertEquals(64, f.length());
        assertTrue(f.matches("[0-9a-f]{64}"));
    }

    @Test
    void fingerprintChangesWhenTwoInputsSwap() {
        Puzzle p = load(json("x", A, B, false));
        Puzzle swapped = load(json("x", B, A, false));
        String f = Fingerprint.solvability(p, program(0, 1), HexField.bounded(3), REG);
        assertNotEquals(f, Fingerprint.solvability(swapped, program(0, 1), HexField.bounded(3), REG));
        // the solvability checksum describes the puzzle, not where the solution places its inputs
        assertEquals(f, Fingerprint.solvability(p, program(1, 0), HexField.bounded(3), REG));
    }

    @Test
    void fingerprintDependsOnProgramAndField() {
        Puzzle p = load(json("x", A, B, false));
        String f = Fingerprint.solvability(p, program(0, 1), HexField.bounded(3), REG);
        assertNotEquals(f, Fingerprint.solvability(p, program(0, 1), HexField.bounded(4), REG));
        assertNotEquals(f, Fingerprint.solvability(p, program(0, 1), HexField.UNBOUNDED, REG));
        Program other = Fx.program(input(0, h(1, 0), 0), input(1, h(-1, 0), 0), output(h(0, 2), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_cw"));
        assertNotEquals(f, Fingerprint.solvability(p, other, HexField.bounded(3), REG));
    }

    @Test
    void fingerprintIgnoresProductAtomOrder() {
        Puzzle p1 = load("{\"format\":1,\"inputs\":[{\"atoms\":[" + A + "]}],\"product\":{\"atoms\":["
                + "{\"element\":\"earth\",\"pos\":[0,0]},{\"element\":\"fire\",\"pos\":[1,0]}],"
                + "\"bonds\":[{\"from\":[0,0],\"to\":[1,0]}]},\"scoring\":{\"metric\":\"cycles\",\"reference\":3}}");
        Puzzle p2 = load("{\"format\":1,\"inputs\":[{\"atoms\":[" + A + "]}],\"product\":{\"atoms\":["
                + "{\"element\":\"fire\",\"pos\":[1,0]},{\"element\":\"earth\",\"pos\":[0,0]}],"
                + "\"bonds\":[{\"from\":[1,0],\"to\":[0,0]}]},\"scoring\":{\"metric\":\"cycles\",\"reference\":3}}");
        Program prog = Fx.program(input(0, h(1, 0), 0), output(h(0, 2), 0));
        assertEquals(Fingerprint.solvability(p1, prog, HexField.bounded(3), REG),
                Fingerprint.solvability(p2, prog, HexField.bounded(3), REG));
    }

    @Test
    void scoringFingerprintFollowsThePartValues() {
        Puzzle p = load(json("x", A, B, false));
        Program prog = program(0, 1);
        String f = Fingerprint.scoring(p, prog, values());
        assertEquals(f, Fingerprint.scoring(p, prog, values()));
        var changed = values();
        changed.put(org.example.resourcemod.core.id.Id.of("arm"),
                new org.example.resourcemod.core.score.PartValue(11, false));
        assertNotEquals(f, Fingerprint.scoring(p, prog, changed));
    }
}

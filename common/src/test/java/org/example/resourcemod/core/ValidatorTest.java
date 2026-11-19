package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.program.ProgramError;
import org.example.resourcemod.core.program.ProgramValidator;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.resourcemod.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {
    private static final Puzzle P = puzzle(List.of(atom("earth")), atom("earth"), 1);

    private static List<String> keys(Puzzle p, Program prog, HexField f) {
        return ProgramValidator.validate(prog, p, f, REG, values()).stream().map(ProgramError::key).toList();
    }

    private static List<String> keys(Program prog) {
        return keys(P, prog, HexField.bounded(3));
    }

    private static Puzzle with(List<Id> forbiddenParts, List<Id> forbiddenInstructions, Integer maxLength) {
        return new Puzzle(1, null, null, P.inputs(), P.product(), forbiddenParts, forbiddenInstructions,
                Id.of("cycles"), 10.0, null, maxLength, null);
    }

    @Test
    void aGoodProgramHasNoErrors() {
        assertEquals(List.of(), keys(SimulationBasicTest.transport()));
    }

    @Test
    void forbiddenPartsAndInstructions() {
        Program prog = program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"));
        assertTrue(keys(with(List.of(Id.of("arm")), null, null), prog, HexField.bounded(3))
                .contains("program.error.forbidden_part"));
        assertTrue(keys(with(null, List.of(Id.of("grab")), null), prog, HexField.bounded(3))
                .contains("program.error.forbidden_instruction"));
    }

    @Test
    void unknownTypesAndMissingValues() {
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0), glyph("addon:laser", h(0, 0), 0)))
                .contains("program.error.unknown_part"));
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=addon:teleport"))).contains("program.error.unknown_instruction"));
    }

    @Test
    void ordinaryInstructionLimitsAreReported() {
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0), arm("arm", h(0, 0), 0, 4)))
                .contains("program.error.arm_length"));
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0), arm("arm", h(0, 0), 6, 1)))
                .contains("program.error.rotation_range"));
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "1000000=grab"))).contains("program.error.tape_slot_limit"));
    }

    @Test
    void inputIndexAndDuplicatesAreChecked() {
        assertTrue(keys(program(input(1, h(1, 0), 0), output(h(1, -1), 0))).contains("program.error.input_index"));
        assertTrue(keys(program(input(0, h(1, 0), 0), input(0, h(2, 0), 0), output(h(1, -1), 0)))
                .contains("program.error.input_twice"));
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0), output(h(2, -1), 0)))
                .contains("program.error.output_twice"));
    }

    @Test
    void outsideAndOverlapReported() {
        assertTrue(keys(program(input(0, h(9, 0), 0), output(h(1, -1), 0))).contains("program.error.outside_field"));
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, 0), 0))).contains("program.error.overlap"));
    }

    @Test
    void anArmMayStandOnATrackButNotOnOtherParts() {
        Program onTrack = program(input(0, h(2, 0), 0), output(h(1, -1), 0),
                track(h(0, 0), h(0, 0), h(1, 0)), arm("arm", h(0, 0), 0, 1, "0=track_forward"));
        assertEquals(List.of(), keys(onTrack));
        Program onGlyph = program(input(0, h(2, 0), 0), output(h(1, -1), 0),
                glyph("bind", h(0, 0), 0), arm("arm", h(0, 0), 0, 1));
        assertTrue(keys(onGlyph).contains("program.error.overlap"));
    }

    @Test
    void trackRulesAreChecked() {
        assertTrue(keys(program(input(0, h(2, 0), 0), output(h(1, -1), 0), track(h(0, 0))))
                .contains("program.error.track_empty"));
        assertTrue(keys(program(input(0, h(2, 0), 0), output(h(1, -1), 0), track(h(0, 0), h(0, 0), h(2, 0))))
                .contains("program.error.track_not_connected"));
        assertTrue(keys(program(input(0, h(2, 0), 0), output(h(1, -1), 0), track(h(0, 0), h(0, 0), h(0, 0))))
                .contains("program.error.track_duplicate"));
        assertTrue(keys(program(input(0, h(2, 0), 0), output(h(1, -1), 0), track(h(0, 0), h(1, 0), h(2, 0))))
                .contains("program.error.track_origin"));
    }

    @Test
    void pistonAndTrackInstructionsNeedParts() {
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0), arm("arm", h(0, 0), 0, 1, "0=extend")))
                .contains("program.error.instruction_needs_piston"));
        assertTrue(keys(program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=track_forward"))).contains("program.error.instruction_needs_track"));
    }

    @Test
    void theProgramLengthLimitCountsInstructions() {
        Program prog = program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "2=drop", "3=rotate_cw"));
        assertTrue(keys(with(null, null, 3), prog, HexField.bounded(3)).contains("program.error.program_too_long"));
        assertFalse(keys(with(null, null, 4), prog, HexField.bounded(3)).contains("program.error.program_too_long"));
    }

    @Test
    void tapeCompileErrorsAreReported() {
        Program prog = program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "2=reset", "3=repeat"));
        assertTrue(keys(prog).stream().anyMatch(k -> k.contains("repeat_overlaps_reset")), keys(prog).toString());
    }
}

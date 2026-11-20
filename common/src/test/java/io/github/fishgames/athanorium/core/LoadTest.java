package io.github.fishgames.athanorium.core;

import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.program.Part;
import io.github.fishgames.athanorium.core.program.SolutionFile;
import io.github.fishgames.athanorium.core.program.SolutionIo;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.puzzle.PuzzleReader;
import io.github.fishgames.athanorium.core.puzzle.PuzzleWriter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.fishgames.athanorium.core.Fx.REG;
import static org.junit.jupiter.api.Assertions.*;

class LoadTest {
    static final String ATOM = "{\"element\":\"earth\",\"pos\":[0,0]}";
    static final String MINIMAL = "{\"format\":1,\"inputs\":[{\"atoms\":[" + ATOM + "]}],"
            + "\"product\":{\"atoms\":[" + ATOM + "]},\"scoring\":{\"metric\":\"cycles\",\"reference\":10}}";

    private static Puzzle load(String json) {
        return PuzzleReader.read(json, "test.json", REG, new ArrayList<>());
    }

    private static DataException fails(String json) {
        return assertThrows(DataException.class, () -> load(json));
    }

    @Test
    void minimalPuzzleLoads() {
        Puzzle p = load(MINIMAL);
        assertNull(p.name());
        assertNull(p.forbiddenParts());
        assertNull(p.maxProgramLength());
        assertEquals(6, p.effectiveTestProducts());
        assertEquals(100_000, p.effectiveMaxCycles());
    }

    @Test
    void puzzleWriteReadRoundTrip() {
        Puzzle p = load(MINIMAL);
        String out = PuzzleWriter.write(p);
        assertFalse(out.contains("test_products"));
        assertFalse(out.contains("forbidden_parts"));
        assertEquals(p, load(out));
        assertEquals(out, PuzzleWriter.write(load(out)));
    }

    @Test
    void duplicateKeysAreRejected() {
        DataException e = fails(MINIMAL.replace("\"format\":1,", "\"format\":1,\"format\":1,"));
        assertTrue(e.getMessage().toLowerCase().contains("duplicate"), e.getMessage());
    }

    @Test
    void unknownFieldsAreRejectedAndNamed() {
        DataException e = fails(MINIMAL.replace("\"format\":1,", "\"format\":1,\"colour\":\"red\","));
        assertTrue(e.getMessage().contains("colour"), e.getMessage());
        assertEquals("test.json", e.file());
    }

    @Test
    void nonIntegerPositionsAreRejected() {
        fails(MINIMAL.replaceFirst("\\[0,0\\]", "[0.5,0]"));
        fails(MINIMAL.replaceFirst("\\[0,0\\]", "[0,0,0]"));
        fails(MINIMAL.replaceFirst("\\[0,0\\]", "[\"0\",0]"));
        fails(MINIMAL.replaceFirst("\\[0,0\\]", "[0,null]"));
    }

    @Test
    void formatMustBePresentAndNotNewerThanKnown() {
        fails(MINIMAL.replace("\"format\":1,", ""));
        fails(MINIMAL.replace("\"format\":1", "\"format\":2"));
        fails(MINIMAL.replace("\"format\":1", "\"format\":0"));
        fails(MINIMAL.replace("\"format\":1", "\"format\":\"1\""));
    }

    @Test
    void brokenJsonIsRejected() {
        fails("{\"format\":1,");
        fails("[]");
        fails("");
        fails(MINIMAL + " trailing");
    }

    @Test
    void unknownIdsInForbiddenListsOnlyWarn() {
        List<String> warnings = new ArrayList<>();
        Puzzle p = PuzzleReader.read(MINIMAL.replace("\"format\":1,",
                "\"format\":1,\"forbidden_parts\":[\"other:magic_arm\",\"disposer\"],"
                        + "\"forbidden_instructions\":[\"other:teleport\"],"), "test.json", REG, warnings);
        assertEquals(2, warnings.size());
        assertEquals(2, p.forbiddenParts().size());
    }

    @Test
    void unknownElementsAndBondTypesAreRejected() {
        fails(MINIMAL.replaceFirst("earth", "gold"));
        String twoAtoms = "{\"atoms\":[{\"element\":\"earth\",\"pos\":[0,0]},{\"element\":\"fire\",\"pos\":[1,0]}],"
                + "\"bonds\":[{\"from\":[0,0],\"to\":[1,0],\"type\":\"other:magic\"}]}";
        fails(MINIMAL.replaceFirst("\"inputs\":\\[\\{.*?\\}\\]\\}\\]", "\"inputs\":[" + twoAtoms + "]"));
    }

    @Test
    void invalidMoleculesAreRejected() {
        String disconnected = "{\"atoms\":[{\"element\":\"earth\",\"pos\":[0,0]},{\"element\":\"fire\",\"pos\":[1,0]}]}";
        fails(MINIMAL.replaceFirst("\"product\":\\{.*?\\}\\]\\}", "\"product\":" + disconnected));
        fails(MINIMAL.replaceFirst("\"product\":\\{.*?\\}\\]\\}", "\"product\":{\"atoms\":[]}"));
        String same = "{\"atoms\":[" + ATOM + "," + ATOM + "]}";
        fails(MINIMAL.replaceFirst("\"product\":\\{.*?\\}\\]\\}", "\"product\":" + same));
        String farBond = "{\"atoms\":[{\"element\":\"earth\",\"pos\":[0,0]},{\"element\":\"fire\",\"pos\":[2,0]}],"
                + "\"bonds\":[{\"from\":[0,0],\"to\":[2,0]}]}";
        fails(MINIMAL.replaceFirst("\"product\":\\{.*?\\}\\]\\}", "\"product\":" + farBond));
    }

    @Test
    void rangesAreChecked() {
        fails(MINIMAL.replace("\"reference\":10", "\"reference\":0"));
        fails(MINIMAL.replace("\"reference\":10", "\"reference\":-3"));
        fails(MINIMAL.replace("\"metric\":\"cycles\"", "\"metric\":\"nonsense\""));
        fails(MINIMAL.replaceFirst("\\[0,0\\]", "[5000,0]"));
        fails(MINIMAL.replace("\"format\":1,", "\"format\":1,\"test_products\":0,"));
        fails(MINIMAL.replace("\"format\":1,", "\"format\":1,\"max_cycles\":0,"));
    }

    @Test
    void idsWithDotsAreRejected() {
        fails(MINIMAL.replace("\"format\":1,", "\"format\":1,\"forbidden_parts\":[\"my.mod:arm\"],"));
    }

    // solutions

    static final String SOLUTION = "{\"format\":1,\"puzzle\":\"tutorial\",\"radius\":3,"
            + "\"versions\":{\"rules\":1,\"types\":{\"arm\":1}},\"expected\":{\"cycles\":3,\"area\":3},"
            + "\"parts\":["
            + "{\"type\":\"input\",\"pos\":[1,0],\"rotation\":0,\"index\":0},"
            + "{\"type\":\"output\",\"pos\":[1,-1],\"rotation\":0},"
            + "{\"type\":\"arm\",\"pos\":[0,0],\"rotation\":0,\"length\":1,\"instructions\":"
            + "{\"0\":\"grab\",\"1\":\"rotate_ccw\",\"2\":\"drop\",\"3\":\"rotate_cw\"}}]}";

    @Test
    void solutionRoundTrip() {
        SolutionFile s = SolutionIo.read(SOLUTION, "sol.json", REG);
        assertEquals(3, s.program().parts().size());
        assertEquals("input", s.program().parts().get(0).type().path());
        assertEquals("arm", s.program().parts().get(2).type().path());
        assertEquals(HexField.bounded(3), s.field());
        String out = SolutionIo.write(s, REG);
        SolutionFile again = SolutionIo.read(out, "sol.json", REG);
        assertEquals(s, again);
        assertEquals(out, SolutionIo.write(again, REG));
    }

    @Test
    void solutionKeepsPartOrder() {
        String json = "{\"format\":1,\"puzzle\":\"p\",\"radius\":\"unbounded\",\"versions\":{\"rules\":1,\"types\":{}},"
                + "\"expected\":{},\"parts\":["
                + "{\"type\":\"unbind\",\"pos\":[0,0],\"rotation\":0},"
                + "{\"type\":\"bind\",\"pos\":[5,0],\"rotation\":1},"
                + "{\"type\":\"disposer\",\"pos\":[9,0],\"rotation\":2},"
                + "{\"type\":\"bind\",\"pos\":[-5,0],\"rotation\":3}]}";
        SolutionFile s = SolutionIo.read(json, "s.json", REG);
        SolutionFile again = SolutionIo.read(SolutionIo.write(s, REG), "s.json", REG);
        List<String> order = again.program().parts().stream().map(p -> p.type().path() + p.pos().q()).toList();
        assertEquals(List.of("unbind0", "bind5", "disposer9", "bind-5"), order);
        assertFalse(again.field().isBounded());
    }

    @Test
    void unknownPartTypesPassThroughUnchanged() {
        String json = SOLUTION.replace("\"parts\":[", "\"parts\":[{\"type\":\"addon:laser\",\"pos\":[7,7],"
                + "\"rotation\":2,\"power\":5},");
        SolutionFile s = SolutionIo.read(json, "sol.json", REG);
        Part unknown = s.program().parts().get(0);
        assertEquals("laser", unknown.type().path());
        String out = SolutionIo.write(s, REG);
        assertTrue(out.contains("\"power\": 5"), out);
        assertEquals(s, SolutionIo.read(out, "sol.json", REG));
    }

    @Test
    void badSolutionsAreRejected() {
        assertThrows(DataException.class, () -> SolutionIo.read(SOLUTION.replace("\"radius\":3", "\"radius\":1"), "s", REG));
        assertThrows(DataException.class, () -> SolutionIo.read(SOLUTION.replace("\"radius\":3,", ""), "s", REG));
        assertThrows(DataException.class, () -> SolutionIo.read(SOLUTION.replace("\"0\":\"grab\"", "\"00\":\"grab\""), "s", REG));
        assertThrows(DataException.class, () -> SolutionIo.read(SOLUTION.replace("\"0\":\"grab\"", "\"-1\":\"grab\""), "s", REG));
        assertThrows(DataException.class, () -> SolutionIo.read(SOLUTION.replace("\"length\":1", "\"length\":1,\"colour\":1"), "s", REG));
        assertThrows(DataException.class, () -> SolutionIo.read(SOLUTION.replace("\"format\":1", "\"format\":9"), "s", REG));
    }
}

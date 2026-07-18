package net.klaaswhite.c2w.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlockType")
class BlockTypeTest {

    @Test
    @DisplayName("parse simple material name")
    void parseSimpleMaterial() {
        var def = BlockType.parse("CHEST");
        assertNotNull(def);
        assertEquals("CHEST", def.material());
        assertEquals("", def.state());
    }

    @Test
    @DisplayName("parse material with block state")
    void parseWithState() {
        var def = BlockType.parse("CHEST[facing=north]");
        assertNotNull(def);
        assertEquals("CHEST", def.material());
        assertEquals("facing=north", def.state());
    }

    @Test
    @DisplayName("parse returns null for null input")
    void parseNull() {
        assertNull(BlockType.parse(null));
    }

    @Test
    @DisplayName("parse returns null for blank input")
    void parseBlank() {
        assertNull(BlockType.parse(""));
        assertNull(BlockType.parse("   "));
    }

    @Test
    @DisplayName("parse is case-insensitive — material is uppercased")
    void parseCaseInsensitive() {
        var def = BlockType.parse("chest");
        assertNotNull(def);
        assertEquals("CHEST", def.material());
    }

    @Test
    @DisplayName("parse mixed case material is uppercased")
    void parseMixedCase() {
        var def = BlockType.parse("OakPlanks");
        assertNotNull(def);
        assertEquals("OAKPLANKS", def.material());
    }

    @Test
    @DisplayName("toString returns material name for simple def")
    void toStringSimple() {
        var def = new BlockType("STONE");
        assertEquals("STONE", def.toString());
    }

    @Test
    @DisplayName("toString includes state bracket")
    void toStringWithState() {
        var def = new BlockType("CHEST", "facing=north");
        assertEquals("CHEST[facing=north]", def.toString());
    }

    @Test
    @DisplayName("toString round-trips through parse")
    void toStringRoundTrip() {
        var def = BlockType.parse("CHEST[facing=north]");
        assertNotNull(def);
        assertEquals("CHEST[facing=north]", def.toString());
    }

    @Test
    @DisplayName("constructor with just material has empty state")
    void constructorMaterialOnly() {
        var def = new BlockType("DIRT");
        assertEquals("DIRT", def.material());
        assertEquals("", def.state());
    }

    @Test
    @DisplayName("parse material with empty brackets")
    void parseEmptyBrackets() {
        var def = BlockType.parse("STONE[]");
        assertNotNull(def);
        assertEquals("STONE", def.material());
        assertEquals("", def.state());
    }

    @Test
    @DisplayName("parse material with complex block state")
    void parseComplexState() {
        var def = BlockType.parse("furnace[facing=north,lit=true]");
        assertNotNull(def);
        assertEquals("FURNACE", def.material());
        assertEquals("facing=north,lit=true", def.state());
    }

    @Test
    @DisplayName("parse material with no closing bracket")
    void parseNoClosingBracket() {
        var def = BlockType.parse("CHEST[facing=north");
        assertNotNull(def);
        assertEquals("CHEST", def.material());
        // No closing bracket means statePart stays empty
        assertEquals("", def.state());
    }

    @Test
    @DisplayName("parse unknown material still creates BlockType")
    void parseUnknownMaterial() {
        // BlockType does not validate material names — it accepts any non-blank string
        var def = BlockType.parse("NOT_A_REAL_MATERIAL");
        assertNotNull(def);
        assertEquals("NOT_A_REAL_MATERIAL", def.material());
    }

    @Test
    @DisplayName("record equality based on material and state")
    void recordEquality() {
        var a = new BlockType("CHEST", "facing=north");
        var b = new BlockType("CHEST", "facing=north");
        var c = new BlockType("CHEST", "facing=south");

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("parse with only whitespace material returns null")
    void parseWhitespaceMaterial() {
        assertNull(BlockType.parse("  [facing=north]"));
    }
}

package com.hannes.familytree.gedcom

import androidx.compose.ui.unit.Density
import org.folg.gedcom.parser.ModelParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GedcomImporterTest {
    private val sample = """
        0 HEAD
        1 GEDC
        2 VERS 5.5.1
        0 @I1@ INDI
        1 NAME John /Doe/
        1 BIRT
        2 DATE 12 MAR 1950
        0 @I2@ INDI
        1 NAME Mary /Doe/
        0 @I3@ INDI
        1 NAME Kid /Doe/
        0 @F1@ FAM
        1 HUSB @I1@
        1 WIFE @I2@
        1 CHIL @I3@
        0 TRLR
    """.trimIndent()

    @Test
    fun mapsPeopleAndFamilies() {
        val gedcom = assertNotNull(ModelParser().parseGedcom(sample.lineSequence()))
        val graph = GedcomImporter.mapToFamilyGraph(gedcom)
        assertEquals(3, graph.persons.size)
        assertEquals("John Doe", graph.persons["I1"]?.name)
        assertEquals(1950, graph.persons["I1"]?.birthYear)
        assertEquals(listOf("I3"), graph.persons["I2"]?.childrenIds?.toList())
        assertEquals(listOf("I1", "I2"), graph.unions.first().spouseIds)
    }

    @Test
    fun importsAndLaysOutGedcomContent() {
        val graph = assertNotNull(GedcomImporter.import(sample, Density(1f)))
        assertEquals(3, graph.persons.size)
        assertEquals("Mary Doe", graph.persons["I2"]?.name)
        assertTrue(graph.width > 0f && graph.height > 0f)
        assertTrue(graph.persons.values.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun rejectsFilesWithoutPeople() {
        assertNull(GedcomImporter.import("0 HEAD\n0 TRLR", Density(1f)))
    }

    @Test
    fun reportsMalformedContent() {
        for (content in listOf("", "not a GEDCOM file")) {
            assertFailsWith<RuntimeException> {
                GedcomImporter.import(content, Density(1f))
            }
        }
    }
}

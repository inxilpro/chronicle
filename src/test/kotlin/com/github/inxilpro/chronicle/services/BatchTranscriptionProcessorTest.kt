package com.github.inxilpro.chronicle.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTranscriptionProcessorTest {

    @Test
    fun testFillerDetectionSingleWords() {
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Um."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Uh"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Hmm"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Yeah."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Ok"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Okay"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("So"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Right"))
    }

    @Test
    fun testFillerDetectionShortPhrases() {
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Thank you."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("So basically."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("- Yeah."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Let's see here."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("Let's see."))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("I mean"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("You know"))
    }

    @Test
    fun testFillerDetectionParenthetical() {
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("(sighs)"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("(laughs)"))
        assertTrue(BatchTranscriptionProcessor.isFillerOrNoise("(clears throat)"))
    }

    @Test
    fun testMeaningfulTextNotFiltered() {
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("I want to refactor this function"))
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("Let's move this to a new class"))
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("The authentication flow needs work"))
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("We need to add error handling here"))
    }

    @Test
    fun testShortMeaningfulTextNotFiltered() {
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("Fix the bug"))
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("Add tests"))
    }

    @Test
    fun testLongerPhrasesNeverFiltered() {
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("Um well I think we should"))
        assertFalse(BatchTranscriptionProcessor.isFillerOrNoise("So basically the idea is"))
    }
}

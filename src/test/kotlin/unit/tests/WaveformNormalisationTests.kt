package unit.tests

import Audiowaveform
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import json
import normalise
import org.junit.jupiter.api.Test
import java.io.File

internal class WaveformNormalisationTests {

    @Test
    fun `Waveform is correctly normalised`() {
        val exampleInputFile = File("src/test/resources/waveform/audiowaveform-input.json")
        val exampleInputString = exampleInputFile.readLines().single()
        val exampleWaveform = json.parse(Audiowaveform.serializer(), exampleInputString)

        val expectedFile = File("src/test/resources/waveform/approved-audiowaveform.json")
        val expectedString = expectedFile.readLines().single()
        val expectedData = json.parse(Audiowaveform.serializer(), expectedString).data

        val normalisedData = exampleWaveform.normalise().data

        assertThat(normalisedData, equalTo(expectedData))
    }
}

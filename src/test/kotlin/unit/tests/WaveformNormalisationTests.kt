package unit.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

internal class WaveformNormalisationTests {
    private val json = Json(JsonConfiguration.Stable)

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

@Serializable
data class Audiowaveform(
    val bits: Int,
    val data: List<Double>,
    val length: Long,
    val version: Int,
    val channels: Int,
    val sample_rate: Int,
    val samples_per_pixel: Int
)

private fun Audiowaveform.normalise(): Audiowaveform =
    this.data.max()?.let { maxPeak ->
        this.copy(
            data = this.data
                .map { BigDecimal(it / maxPeak).setScale(2, RoundingMode.HALF_EVEN).toDouble() }
        )
    } ?: error("Cannot get max of empty list.")

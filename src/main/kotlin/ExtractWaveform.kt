import json.json
import kotlinx.serialization.Serializable
import storage.Waveform
import storage.Waveform.Companion.Bits
import storage.Waveform.Companion.Channels
import storage.Waveform.Companion.Data
import storage.Waveform.Companion.Length
import storage.Waveform.Companion.SampleRate
import storage.Waveform.Companion.SamplesPerPixel
import storage.Waveform.Companion.Version
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

object ExtractWaveform : (File) -> Waveform {
    override operator fun invoke(file: File): Waveform {
        val outputFile = "/tmp/${file.nameWithoutExtension}.json"

        ProcessBuilder().command(listOf(
            "./lib/audiowaveform",
            "-i",
            file.absolutePath,
            "-o",
            outputFile,
            "--pixels-per-second",
            "20",
            "--bits",
            "8"
        )).start().waitFor()

        val output = File(outputFile).reader().readLines().joinToString("")
        val audiowaveform = json.parse(Audiowaveform.serializer(), output).normalise()

        return Waveform(
            Bits(audiowaveform.bits),
            Data(audiowaveform.data),
            Length(audiowaveform.length),
            Version(audiowaveform.version),
            Channels(audiowaveform.channels),
            SampleRate(audiowaveform.sample_rate),
            SamplesPerPixel(audiowaveform.samples_per_pixel)
        )
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

fun Audiowaveform.normalise(): Audiowaveform =
    this.data.max()?.let { maxPeak ->
        this.copy(
            data = this.data
                .map { BigDecimal(it / maxPeak).setScale(2, RoundingMode.HALF_EVEN).toDouble() }
        )
    } ?: error("Cannot get max of empty list.")

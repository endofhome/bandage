package handlers

import Logging.logger
import RouteMappings.dashboard
import handlers.UploadPreview.ViewModels.PreProcessedAudioTrackMetadata
import handlers.UploadPreview.months
import handlers.UploadPreview.tempDir
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.formAsMap
import result.flatMap
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.FileStorage
import storage.FileStoragePermission.PasswordProtected
import storage.HasPresentationFormat.Companion.presentationFormat
import storage.MetadataStorage
import storage.Waveform
import storage.toBitRate
import storage.toDuration
import java.io.File
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

object Upload {
    private val monthRange = IntRange(months.first().number, months.last().number)
    val dayRange = 1..31
    val hourRange = 0..23
    val timeRange = 0..59

    operator fun invoke(request: Request, metadataStorage: MetadataStorage, fileStorage: FileStorage, fileStoragePassword: String, extractWaveform: (File) -> Waveform): Response {
        val preProcessedAudioTrackMetadata: PreProcessedAudioTrackMetadata = try {
            val formAsMap = request.formAsMap()

            val artist = formAsMap.singleOrLog("artist") ?: return Response(BAD_REQUEST)
            val title = formAsMap.singleOrLog("title") ?: return Response(BAD_REQUEST)
            val workingTitle = formAsMap.singleOrLog("working_title") ?: return Response(BAD_REQUEST)
            val duration = formAsMap.singleOrLog("duration_raw") ?: return Response(BAD_REQUEST)
            val format = formAsMap.singleOrLog("format") ?: return Response(BAD_REQUEST)
            val bitRate = formAsMap.singleOrLog("bitrate_raw") ?: return Response(BAD_REQUEST)
            val recordedYear = formAsMap.singleOrLog("recorded_year") ?: return Response(BAD_REQUEST)
            val recordedMonth = formAsMap.singleOrLog("recorded_month") ?: return Response(BAD_REQUEST)
            val recordedDay = formAsMap.singleOrLog("recorded_day") ?: return Response(BAD_REQUEST)
            val recordedHour = formAsMap.singleOrLog("recorded_hour") ?: return Response(BAD_REQUEST)
            val recordedMinute = formAsMap.singleOrLog("recorded_minute") ?: return Response(BAD_REQUEST)
            val recordedSecond = formAsMap.singleOrLog("recorded_second") ?: return Response(BAD_REQUEST)
            val filename = formAsMap.singleOrLog("filename") ?: return Response(BAD_REQUEST)
            val normalisedFileSize = formAsMap.singleOrLog("normalised_file_size") ?: return Response(BAD_REQUEST)
            val hash = formAsMap.singleOrLog("hash") ?: return Response(BAD_REQUEST)

            PreProcessedAudioTrackMetadata(
                artist,
                null,
                title.ifEmpty { null },
                workingTitle.ifEmpty { null },
                format,
                bitRate.ifEmpty { null }?.toBitRate()?.presentationFormat(),
                bitRate.ifEmpty { null },
                duration.ifEmpty { null }?.toDuration()?.presentationFormat(),
                duration.ifEmpty { null },
                hash,
                filename,
                normalisedFileSize.toLong(),
                recordedYear.toInt(),
                recordedMonth.ifEmpty { null }?.toInt()?.also { it.mustBeIn(monthRange) },
                recordedDay.ifEmpty { null }?.toInt()?.also { it.mustBeIn(dayRange) },
                recordedHour.ifEmpty { null }?.also { it.toInt().mustBeIn(hourRange) },
                recordedMinute.ifEmpty { null }?.also { it.toInt().mustBeIn(timeRange) },
                recordedSecond.ifEmpty { null }?.also { it.toInt().mustBeIn(timeRange) }
            )
        } catch (e: Exception) {
            logger.warn(e.message)
            return Response(BAD_REQUEST)
        }


        val (recordedTimestamp: ZonedDateTime, recordedTimestampPrecision: ChronoUnit) =
            AssembleTimestamp(
                preProcessedAudioTrackMetadata.recordedYear,
                preProcessedAudioTrackMetadata.recordedMonth,
                preProcessedAudioTrackMetadata.recordedDay,
                preProcessedAudioTrackMetadata.recordedHour?.toInt(),
                preProcessedAudioTrackMetadata.recordedMinute?.toInt(),
                preProcessedAudioTrackMetadata.recordedSecond?.toInt()
            ) // TODO handle failure

        val foldername = recordedTimestamp.toFoldername()
        val destinationPath = "/$foldername/${preProcessedAudioTrackMetadata.filename}"
        val tempFile = File("$tempDir/${preProcessedAudioTrackMetadata.filename}")

        val waveform = extractWaveform(tempFile) // TODO do this whilst uploading file not before

        return fileStorage.uploadFile(tempFile, destinationPath).flatMap {
            fileStorage.publicLink(destinationPath, PasswordProtected(fileStoragePassword)).map { passwordProtectedLink ->
                val uuid = UUID.randomUUID()
                metadataStorage.addTracks(
                    listOf(
                        AudioTrackMetadata(
                            uuid,
                            preProcessedAudioTrackMetadata.artist.orEmpty(),
                            "",
                            preProcessedAudioTrackMetadata.title ?: "untitled",
                            preProcessedAudioTrackMetadata.workingTitle?.let { listOf(it) }.orEmpty(),
                            preProcessedAudioTrackMetadata.format,
                            preProcessedAudioTrackMetadata.bitRateRaw?.toBitRate(),
                            preProcessedAudioTrackMetadata.durationRaw?.toDuration(),
                            tempFile.length().toInt(),
                            preProcessedAudioTrackMetadata.normalisedFileSize,
                            "",
                            recordedTimestamp,
                            recordedTimestampPrecision,
                            Instant.now().atZone(UTC),
                            passwordProtectedLink,
                            destinationPath,
                            preProcessedAudioTrackMetadata.hash,
                            emptyList(),
                            waveform
                        )
                    )
                )
                uuid
            }.map { uuid ->
                Response(SEE_OTHER).header("Location", "$dashboard?highlighted=$uuid")
            }
        }.orElse {
            logger.warn(it.message)
            Response(INTERNAL_SERVER_ERROR)
        }
    }

    private fun Int.mustBeIn(range: IntRange) {
        require(this in range) { "${this} was not between ${range.first} and ${range.last}" }
    }

    private fun ZonedDateTime.toFoldername(): String = "$year-${monthValue.pad()}-${dayOfMonth.pad()}"

    private fun Int.pad() = toString().padStart(2, '0')

    private fun Map<String, List<String?>>.singleOrLog(field: String): String? {
        val extracted = this[field]?.single()
        return if (extracted != null) {
            extracted
        } else {
            logger.warn("Failed to extract '$field' field from form during file upload")
            null
        }
    }
}

object AssembleTimestamp {
    operator fun invoke(
        recordedYear: Int,
        recordedMonth: Int?,
        recordedDay: Int?,
        recordedHour: Int?,
        recordedMinute: Int?,
        recordedSecond: Int?
    ): Pair<ZonedDateTime, ChronoUnit> {
        // TODO possibly put the require() here rather, as it matters here the most.

        val precisionList = listOf(
            recordedMonth to ChronoUnit.MONTHS,
            recordedDay to ChronoUnit.DAYS,
            recordedHour to ChronoUnit.HOURS,
            recordedMinute to ChronoUnit.MINUTES,
            recordedSecond to ChronoUnit.SECONDS
        )

        val initialPair = ZonedDateTime.of(recordedYear, 1, 1, 0, 0, 0, 0, UTC) to ChronoUnit.YEARS

        fun assemble(assembled: Pair<ZonedDateTime, ChronoUnit>, remainder: List<Pair<Int?, ChronoUnit>>): Pair<ZonedDateTime, ChronoUnit> {
            val nextPair = remainder.firstOrNull()
            val nextTimestamp = nextPair?.first
            return if (nextTimestamp == null) {
                assembled
            } else {
                val nextPrecision = nextPair.second
                val amountToAdd = if (nextPrecision == ChronoUnit.MONTHS || nextPrecision == ChronoUnit.DAYS) {
                    nextTimestamp.toLong() - 1L
                } else {
                    nextTimestamp.toLong()
                }

                val newTimestamp = assembled.first.plus(amountToAdd, nextPrecision)
                assemble(newTimestamp to nextPrecision, remainder.drop(1))
            }
        }

        return assemble(initialPair, precisionList)
    }
}

object DisassembleTimestamp {
    operator fun invoke(timestamp: ZonedDateTime, precision: ChronoUnit): DisassembledTimestamp {

        val precisionList = listOf(
            ChronoUnit.YEARS,
            ChronoUnit.MONTHS,
            ChronoUnit.DAYS,
            ChronoUnit.HOURS,
            ChronoUnit.MINUTES,
            ChronoUnit.SECONDS
        )

        // TODO handle with Result?
        require(precision in precisionList) { "Precision $precision is not in supported list: $precisionList" }

        fun validChronoUnits(units: List<ChronoUnit>, remainder: List<ChronoUnit>, stop: Boolean = false): List<ChronoUnit> =
            if (remainder.isEmpty() || stop) {
                units
            } else {
                val next = remainder.first()
                validChronoUnits(units + next, remainder.drop(1), next == precision)
            }

        val unitsToTake = validChronoUnits(precisionList.take(1), precisionList.drop(1))
        val initialDisassembledTimestamp = DisassembledTimestamp(timestamp.year)

        fun disassemble(disassembled: DisassembledTimestamp, remainder: List<ChronoUnit>): DisassembledTimestamp =
            if (remainder.isEmpty()) {
                disassembled
            } else {
                when (remainder.first()) {
                    ChronoUnit.YEARS   -> disassemble(disassembled.copy(year = timestamp.year), remainder.drop(1))
                    ChronoUnit.MONTHS  -> disassemble(disassembled.copy(month = timestamp.monthValue), remainder.drop(1))
                    ChronoUnit.DAYS    -> disassemble(disassembled.copy(day = timestamp.dayOfMonth), remainder.drop(1))
                    ChronoUnit.HOURS   -> disassemble(disassembled.copy(hour = timestamp.hour), remainder.drop(1))
                    ChronoUnit.MINUTES -> disassemble(disassembled.copy(minute = timestamp.minute), remainder.drop(1))
                    ChronoUnit.SECONDS -> disassemble(disassembled.copy(second = timestamp.second), remainder.drop(1))
                    else               -> error("unsupported precision: ${remainder.first()}")
                }
            }

        return disassemble(initialDisassembledTimestamp, unitsToTake.drop(1))
    }
}

data class DisassembledTimestamp(
    val year: Int,
    val month: Int? = null,
    val day: Int? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val second: Int? = null
)

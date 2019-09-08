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

    operator fun invoke(request: Request, metadataStorage: MetadataStorage, fileStorage: FileStorage, fileStoragePassword: String): Response {
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
                recordedYear.toInt(),
                recordedMonth.ifEmpty { null }?.toInt()?.also { it.requireInRange(monthRange) },
                recordedDay.ifEmpty { null }?.toInt()?.also { it.requireInRange(dayRange) },
                recordedHour.ifEmpty { null }?.also { it.toInt().requireInRange(hourRange) },
                recordedMinute.ifEmpty { null }?.also { it.toInt().requireInRange(timeRange) },
                recordedSecond.ifEmpty { null }?.also { it.toInt().requireInRange(timeRange) }
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
                            "",
                            recordedTimestamp,
                            recordedTimestampPrecision,
                            Instant.now().atZone(UTC),
                            passwordProtectedLink,
                            destinationPath,
                            preProcessedAudioTrackMetadata.hash,
                            emptyList()
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

    private fun Int.requireInRange(range: IntRange) {
        require(this in range) { "${this} was not between ${range.first} and ${range.last}" }
    }

    private fun ZonedDateTime.toFoldername(): String = "$year-${monthValue.pad()}-${dayOfMonth.pad()}"

    private fun Int.pad() = toString().padStart(2, '0')

    private fun Map<String, List<String?>>.singleOrLog(field: String): String? {
        val extracted = this[field]?.single()
        return if (extracted != null) {
            extracted
        } else {
            logger.warn("Failure to extract '$field' field from form during file upload")
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

        fun assemble(
            assembled: Pair<ZonedDateTime, ChronoUnit>,
            remainder: List<Pair<Int?, ChronoUnit>>
        ): Pair<ZonedDateTime, ChronoUnit> {
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
    operator fun invoke(
        timestamp: ZonedDateTime,
        precision: ChronoUnit
    ): DisassembledTimestamp {

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

        fun validChronoUnits(units: List<ChronoUnit>, next: ChronoUnit?, remainder: List<ChronoUnit>, stop: Boolean = false): List<ChronoUnit> {
            return if (next == null || stop) {
                units
            } else {
                validChronoUnits(units + next, remainder.firstOrNull(), remainder.drop(1), next == precision)
            }
        }

        val unitsToTake = validChronoUnits(listOf(ChronoUnit.YEARS), precisionList.first(), precisionList.drop(1))
        val initialDisassembledTimestamp = DisassembledTimestamp(timestamp.year, null, null, null, null, null)

        fun recurse(disassembledTimestamp: DisassembledTimestamp, next: ChronoUnit?, remainder: List<ChronoUnit>): DisassembledTimestamp {
            return if (next == null) {
                disassembledTimestamp
            } else {
                when (next) {
                    ChronoUnit.YEARS -> recurse(disassembledTimestamp.copy(year = timestamp.year), remainder.firstOrNull(), remainder.drop(1))
                    ChronoUnit.MONTHS -> recurse(disassembledTimestamp.copy(month = timestamp.monthValue), remainder.firstOrNull(), remainder.drop(1))
                    ChronoUnit.DAYS -> recurse(disassembledTimestamp.copy(day = timestamp.dayOfMonth), remainder.firstOrNull(), remainder.drop(1))
                    ChronoUnit.HOURS -> recurse(disassembledTimestamp.copy(hour = timestamp.hour), remainder.firstOrNull(), remainder.drop(1))
                    ChronoUnit.MINUTES -> recurse(disassembledTimestamp.copy(minute = timestamp.minute), remainder.firstOrNull(), remainder.drop(1))
                    ChronoUnit.SECONDS -> recurse(disassembledTimestamp.copy(second = timestamp.second), remainder.firstOrNull(), remainder.drop(1))
                    else -> TODO("unsupported operation")
                }
            }
        }

        return recurse(initialDisassembledTimestamp, unitsToTake.firstOrNull(), unitsToTake.drop(1))
    }

}

data class DisassembledTimestamp(val year: Int, val month: Int?, val day: Int?, val hour: Int?, val minute: Int?, val second: Int?)

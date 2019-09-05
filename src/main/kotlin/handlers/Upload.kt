package handlers

import Logging.logger
import RouteMappings.dashboard
import handlers.UploadPreview.ViewModels.PreProcessedAudioTrackMetadata
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
    operator fun invoke(request: Request, metadataStorage: MetadataStorage, fileStorage: FileStorage, fileStoragePassword: String): Response {
        val preProcessedAudioTrackMetadata: PreProcessedAudioTrackMetadata = try {
            val formAsMap = request.formAsMap()

            val artist = formAsMap.singleOrLog("artist") ?: return Response(BAD_REQUEST)
            val title = formAsMap.singleOrLog("title") ?: return Response(BAD_REQUEST)
            val workingTitle = formAsMap.singleOrLog("working_title") ?: return Response(BAD_REQUEST)
            val duration = formAsMap.singleOrLog("duration_raw") ?: return Response(BAD_REQUEST)
            val format = formAsMap.singleOrLog("format") ?: return Response(BAD_REQUEST)
            val bitRate = formAsMap.singleOrLog("bitrate_raw") ?: return Response(BAD_REQUEST)
            // TODO use snake case
            val recordedYear = formAsMap.singleOrLog("recordedYear") ?: return Response(BAD_REQUEST)
            val recordedMonth = formAsMap.singleOrLog("recordedMonth") ?: return Response(BAD_REQUEST)
            val recordedDay = formAsMap.singleOrLog("recordedDay") ?: return Response(BAD_REQUEST)
            val recordedHour = formAsMap.singleOrLog("recordedHour") ?: return Response(BAD_REQUEST)
            val recordedMinute = formAsMap.singleOrLog("recordedMinute") ?: return Response(BAD_REQUEST)
            val recordedSecond = formAsMap.singleOrLog("recordedSecond") ?: return Response(BAD_REQUEST)
            val filename = formAsMap.singleOrLog("filename") ?: return Response(BAD_REQUEST)
            val hash = formAsMap.singleOrLog("hash") ?: return Response(BAD_REQUEST)

            val monthRange = 1..12
            val dayRange = 1..31
            val hourRange = 0..23
            val timeRange = 0..59
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
                recordedMonth.ifEmpty { null }?.toInt()?.also { require(it in monthRange) { "$it was not between ${monthRange.first} and ${monthRange.last}" } },
                recordedDay.ifEmpty { null }?.toInt()?.also { require(it in dayRange) { "$it was not between ${dayRange.first} and ${dayRange.last}" } },
                recordedHour.ifEmpty { null }?.also { require(it.toInt() in hourRange) { "$it was not between ${hourRange.first} and ${hourRange.last}" } },
                recordedMinute.ifEmpty { null }?.also { require(it.toInt() in timeRange) { "$it was not between ${timeRange.first} and ${timeRange.last}" } },
                recordedSecond.ifEmpty { null }?.also { require(it.toInt() in timeRange) { "$it was not between ${timeRange.first} and ${timeRange.last}" } }
            )
        } catch (e: Exception) {
            logger.warn(e.message)
            return Response(BAD_REQUEST)
        }


        val (recordedTimestamp: ZonedDateTime, recordedTimestampPrecision: ChronoUnit) =
            AssembleTimestamp(
                preProcessedAudioTrackMetadata.recordedYear!!, // TODO remove "!!"
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
                Response(SEE_OTHER).header("Location", "$dashboard?highlighted=$uuid}")
            }
        }.orElse {
            logger.warn(it.message)
            Response(INTERNAL_SERVER_ERROR)
        }
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

        fun recurse(timestampToPrecision: Pair<ZonedDateTime,ChronoUnit>, nextPair: Pair<Int?,ChronoUnit>?, remainder: List<Pair<Int?, ChronoUnit>>): Pair<ZonedDateTime, ChronoUnit> {
            return if (nextPair?.first == null) {
                timestampToPrecision
            } else {
                val amountToAdd = if (nextPair.second == ChronoUnit.MONTHS || nextPair.second == ChronoUnit.DAYS) {
                    nextPair.first!!.toLong() - 1L
                } else {
                    nextPair.first!!.toLong()
                }

                val newTimestamp = timestampToPrecision.first.plus(amountToAdd, nextPair.second) // TODO remove "!!"
                recurse(newTimestamp to nextPair.second, remainder.firstOrNull(), remainder.drop(1))
            }
        }

        return recurse(initialPair, precisionList.firstOrNull(), precisionList.drop(1))
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
                // TODO remove "!!"
                validChronoUnits(units + next!!, remainder.firstOrNull(), remainder.drop(1), next == precision)
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

fun main() {
    val blah = DisassembleTimestamp(ZonedDateTime.of(12, 11, 10, 9, 8, 7, 0, UTC), ChronoUnit.YEARS)
    println("blah = ${blah}")
}
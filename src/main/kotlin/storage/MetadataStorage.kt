package storage

import scripts.BandageFileMetadata
import java.io.FileWriter

interface MetadataStorage {
    fun write(metadata: List<BandageFileMetadata>)
}

object CsvMetadataStorage : MetadataStorage {
    private const val outputFileName = "seed-data.txt"
    private val fileWriter = FileWriter(outputFileName, true)
    private val lineSeparator = System.lineSeparator()
    private val headerLine =
        "ID,Artist,Album,Title,Format,Bitrate,Duration,Size,Recorded date,Password protected link,Path,SHA-256$lineSeparator"

    override fun write(metadata: List<BandageFileMetadata>) {
        fileWriter.append(headerLine)

        metadata.forEach { singleFileMetadata ->
            fileWriter.append(singleFileMetadata.run {
                "$uuid,$artist,$album,$title,$format,$bitRate,$duration,$fileSize,$recordedDate,$passwordProtectedLink,$path,$hash$lineSeparator"
            })
        }

        fileWriter.flush()
        fileWriter.close()
    }
}
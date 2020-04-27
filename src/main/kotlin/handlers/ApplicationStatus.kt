package handlers

import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import java.io.BufferedReader
import java.io.InputStreamReader

object ApplicationStatus {
    operator fun invoke(): Response {
        val audiowaveformVersion = "./lib/audiowaveform -v".split(" ")
        val process = ProcessBuilder().command(audiowaveformVersion).start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val version = reader.readLine().trim()

        return Response(OK).body(
        """
            {
                audiowaveform: $version
            }
        """.trimIndent()
        )
    }
}
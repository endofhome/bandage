package handlers

import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import java.io.BufferedReader
import java.io.InputStreamReader

object ApplicationStatus {
    private val libs = mapOf(
        "audiowaveform" to "-v",
        "ffmpeg" to "-version",
        "ffprobe" to "-version"
    )

    operator fun invoke(): Response =
        Response(OK).body(
            libs.map { (command, args) ->
                val process = ProcessBuilder().command(listOf("./lib/$command", args)).start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                command to reader.readLine().trim()
            }.joinToString(
                separator = ",\n",
                prefix = "{\n",
                postfix = "\n}\n"
            ) { "  ${it.first}: ${it.second}" }
        )
}

package storage

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleAudioTrackMetadata
import org.junit.jupiter.api.Test

internal class AudioTrackMetadataEnhancerTests {
    @Test
    fun `Adds take numbers based on preferred title`() = with(AudioTrackMetadataEnhancer) {
        val tracks = listOf(
            exampleAudioTrackMetadata.copy(title = "a really unique title", workingTitles = emptyList()),
            exampleAudioTrackMetadata.copy(title = "some title", workingTitles = listOf("another title")),
            exampleAudioTrackMetadata.copy(title = "", workingTitles = listOf("some title")),
            exampleAudioTrackMetadata.copy(title = "some title", workingTitles = emptyList()),
            exampleAudioTrackMetadata.copy(title = "", workingTitles = listOf("another unique title")),
            exampleAudioTrackMetadata.copy(title = "", workingTitles = listOf("another title"))
        )

        val takes = tracks.enhanceWithTakeNumber().map { it.takeNumber }

        assertThat(takes, equalTo(listOf(null, 1, 2, 3, null, null)))
    }
}

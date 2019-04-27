package functional.tests

import Bandage
import config.BandageConfig
import org.junit.jupiter.api.Test
import storage.DummyMetadataStorage

class BandageConfigTest {

    @Test
    fun `application is configured correctly`() {
        Bandage.init(BandageConfig, DummyMetadataStorage)
    }
}

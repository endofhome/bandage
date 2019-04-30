package functional.tests

import Bandage
import config.BandageConfig
import org.junit.jupiter.api.Test
import storage.DummyMetadataStorageFactory

class BandageConfigTest {

    @Test
    fun `application is configured correctly`() {
        Bandage.init(BandageConfig, DummyMetadataStorageFactory)
    }
}

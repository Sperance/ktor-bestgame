package features.startup

import io.ktor.server.config.yaml.YamlConfigLoader
import kotlin.test.*
import org.bson.Document
import ru.descend.infrastructure.http.transactionCapableTopology

/** Guards the two startup contracts that only break outside CI: the deployment config and the MongoDB topology. */
class StartupContractTest {
    @Test fun deploymentPortFallsBackToTheDocumentedDefault() {
        val config = assertNotNull(YamlConfigLoader().load("application.yaml"), "application.yaml must stay loadable")
        assertEquals("8080", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("ru.descend.bootstrap.ApplicationKt.module"), config.property("ktor.application.modules").getList())
    }

    @Test fun onlyTransactionCapableDeploymentsAreAccepted() {
        assertEquals("rs0", transactionCapableTopology(Document("setName", "rs0").append("isWritablePrimary", true)))
        assertEquals("isdbgrid", transactionCapableTopology(Document("msg", "isdbgrid")))
        assertNull(transactionCapableTopology(Document("isWritablePrimary", true)), "standalone servers cannot run transactions")
    }
}

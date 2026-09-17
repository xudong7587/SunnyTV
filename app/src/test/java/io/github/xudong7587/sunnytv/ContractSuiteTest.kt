package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.contract.runContractSuite
import org.junit.Test

/** Executes the identical standalone suite in the Android JVM test task when Gradle is available. */
class ContractSuiteTest {
    @Test fun coreContracts() = runContractSuite()
}

package com.kittenmp.deps

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class MavenCentralClientTest {
  @Test
  fun testFindLatest() = runTest {
    val client = MavenCentralClient()
    val match = client.findLatest("clikt")
    println("Found clikt: $match")
    assertNotNull(match.version)
  }
}

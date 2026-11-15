// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contract the fake shares with the camera, so the scan screens built against it see the
 * same codes and errors as on a phone.
 */
class FakeQrScannerTest {

  @Test
  fun codes_whileCollected_emitsTheCodesInOrder() = runTest {
    val scanner = FakeQrScanner()
    val received = mutableListOf<String>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.toList(received) }

    scanner.emit("AB23CD")
    scanner.emit("EF45GH")

    assertEquals(listOf("AB23CD", "EF45GH"), received)
    job.cancel()
  }

  @Test
  fun codes_whileNobodyCollects_areDropped() = runTest {
    val scanner = FakeQrScanner()
    scanner.emit("AB23CD")
    val received = mutableListOf<String>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.toList(received) }

    scanner.emit("EF45GH")

    assertEquals(listOf("EF45GH"), received)
    job.cancel()
  }

  @Test
  fun codes_withTwoCollectors_reachBoth() = runTest {
    val scanner = FakeQrScanner()
    val first = mutableListOf<String>()
    val second = mutableListOf<String>()
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val jobs =
        listOf(
            launch(dispatcher) { scanner.codes.toList(first) },
            launch(dispatcher) { scanner.codes.toList(second) },
        )

    scanner.emit("AB23CD")

    assertEquals(listOf("AB23CD"), first)
    assertEquals(listOf("AB23CD"), second)
    jobs.forEach { it.cancel() }
  }

  @Test
  fun activeCollectors_countsTheCollectorsListening() = runTest {
    val scanner = FakeQrScanner()
    assertEquals(0, scanner.activeCollectors)

    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.collect {} }
    assertEquals(1, scanner.activeCollectors)

    job.cancel()
    assertEquals(0, scanner.activeCollectors)
  }

  @Test
  fun error_isNullWhileScanning() {
    assertNull(FakeQrScanner().error.value)
  }

  @Test
  fun error_givenBeforeAnyoneCollects_isKept() {
    val scanner = FakeQrScanner()

    scanner.fail(ScanError.NO_CAMERA)

    assertEquals(ScanError.NO_CAMERA, scanner.error.value)
  }

  @Test
  fun error_whileCollected_isEmitted() = runTest {
    val scanner = FakeQrScanner()
    val received = mutableListOf<ScanError?>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.error.toList(received) }

    scanner.fail(ScanError.CAMERA_FAILED)

    assertEquals(listOf(null, ScanError.CAMERA_FAILED), received)
    job.cancel()
  }

  @Test
  fun torch_withATorch_followsTheLastCall() {
    val scanner = FakeQrScanner()
    assertTrue(scanner.hasTorch.value)
    assertFalse(scanner.torchOn.value)

    scanner.torch(true)
    assertTrue(scanner.torchOn.value)

    scanner.torch(false)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun torch_withoutATorch_staysOff() {
    val scanner = FakeQrScanner(hasTorch = false)

    scanner.torch(true)

    assertFalse(scanner.hasTorch.value)
    assertFalse(scanner.torchOn.value)
  }

  @Test
  fun fail_turnsTheTorchOff() {
    val scanner = FakeQrScanner()
    scanner.torch(true)

    scanner.fail(ScanError.CAMERA_FAILED)

    assertFalse(scanner.torchOn.value)
  }
}

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MissionLineTest {

  private val bar =
      MissionLine(
          "m1",
          "Bar",
          Instant.parse("2026-11-27T18:00:00Z"),
          Instant.parse("2026-11-27T20:00:00Z"),
      )

  @Test
  fun encode_writesTheIdTheTitleTheStartAndTheEnd() {
    assertEquals("m1|Bar|2026-11-27T18:00:00Z|2026-11-27T20:00:00Z", bar.encode())
  }

  @Test
  fun aLineEndingBeforeItStarts_isRefused() {
    assertThrows(IllegalArgumentException::class.java) {
      bar.copy(endAt = bar.startAt.minusSeconds(1))
    }
  }

  @Test
  fun decode_readsBackAnEncodedLine() {
    assertEquals(bar, MissionLine.decode(bar.encode()))
  }

  @Test
  fun decode_keepsATitleHoldingTheSeparatorWhole() {
    val line = bar.copy(title = "Bar | Stage A")

    assertEquals(line, MissionLine.decode(line.encode()))
  }

  @Test
  fun decodeAll_keepsAMissionWhoseTitleHoldsLineBreaks() {
    val line = bar.copy(title = "Bar\nStage A\r\n\nNorth\n")

    assertEquals(
        listOf(line.copy(title = "Bar Stage A North")),
        MissionLine.decodeAll(MissionLine.encodeAll(listOf(line))),
    )
  }

  @Test
  fun decode_refusesAMalformedLine() {
    assertNull(MissionLine.decode("m1|2026-11-27T18:00:00Z|2026-11-27T20:00:00Z"))
    assertNull(MissionLine.decode("|Bar|2026-11-27T18:00:00Z|2026-11-27T20:00:00Z"))
    assertNull(MissionLine.decode("m1|Bar|2026-11-27T18:00:00Z"))
    assertNull(MissionLine.decode("m1|Bar|tomorrow|2026-11-27T20:00:00Z"))
    assertNull(MissionLine.decode("m1|Bar|2026-11-27T18:00:00Z|later"))
  }

  @Test
  fun decode_skipsALineEndingBeforeItStarts() {
    assertNull(MissionLine.decode("m1|Bar|2026-11-27T20:00:00Z|2026-11-27T18:00:00Z"))
  }

  @Test
  fun decodeAll_readsEveryLineAndSkipsTheMalformedOnes() {
    val gate =
        MissionLine(
            "m2",
            "Gate",
            Instant.parse("2026-11-28T08:30:00Z"),
            Instant.parse("2026-11-28T12:00:00Z"),
        )
    val stored = MissionLine.encodeAll(listOf(bar, gate)) + "\nbroken\n"

    assertEquals(listOf(bar, gate), MissionLine.decodeAll(stored))
  }
}

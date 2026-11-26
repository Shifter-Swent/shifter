// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [PendingSeenStore]: what a check sees while it runs, and what reaches the stored
 * [SeenStore] once its notifications are shown or not.
 */
class PendingSeenStoreTest {

  private val stored = InMemorySeenStore().apply { markShown("old") }

  private val pending = PendingSeenStore(stored)

  @Test
  fun wasShown_seesTheChangesOfTheRun_beforeTheyAreCommitted() {
    pending.markShown("new")
    pending.forget("old")

    assertTrue(pending.wasShown("new"))
    assertFalse(pending.wasShown("old"))
    assertFalse(stored.wasShown("new"))
    assertTrue(stored.wasShown("old"))
  }

  @Test
  fun commit_whenShown_writesTheForgottenAndTheMarkedKeys() {
    pending.forget("old")
    pending.markShown("new")

    pending.commit(shown = true)

    assertFalse(stored.wasShown("old"))
    assertTrue(stored.wasShown("new"))
  }

  @Test
  fun commit_whenNotShown_writesOnlyTheForgottenKeys() {
    pending.forget("old")
    pending.markShown("new")

    pending.commit(shown = false)

    assertFalse(stored.wasShown("old"))
    assertFalse(stored.wasShown("new"))
  }

  @Test
  fun commit_whenNotShown_forgetsAKeyForgottenThenMarkedAgain() {
    // Its earlier mark must not stay, since the notification of the new mark was not shown.
    pending.forget("old")
    pending.markShown("old")

    pending.commit(shown = false)

    assertFalse(stored.wasShown("old"))
  }

  @Test
  fun commit_whenShown_marksAKeyForgottenThenMarkedAgain() {
    // The forget is written first, so the new mark is the one that stays.
    pending.forget("old")
    pending.markShown("old")

    pending.commit(shown = true)

    assertTrue(stored.wasShown("old"))
  }

  @Test
  fun commit_whenShown_forgetsAKeyMarkedThenForgotten() {
    pending.markShown("new")
    pending.forget("new")

    pending.commit(shown = true)

    assertFalse(pending.wasShown("new"))
    assertFalse(stored.wasShown("new"))
  }
}

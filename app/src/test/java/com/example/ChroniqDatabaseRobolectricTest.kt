package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChronoDateDatabase
import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmHistoryEntity
import com.example.data.model.AlarmHistoryOutcome
import com.example.data.model.SmartReminderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ChroniqDatabaseRobolectricTest {

    private lateinit var db: ChronoDateDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChronoDateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `saveAlarmComplete persists parent-child smart reminders and cascade deletes cleanly`() = runBlocking {
        val dao = db.alarmDao()
        val alarm = AlarmEntity(
            id = "alarm-parent-1",
            label = "Medical Appointment",
            description = "Bring lab results",
            category = "Health",
            hour = 9,
            minute = 0,
            isSmartReminderEnabled = true,
        )
        val dates = listOf(
            AlarmDateEntity(id = "d-1", alarmId = alarm.id, targetDate = "2026-12-25"),
        )
        val reminders = listOf(
            SmartReminderEntity(id = "r-1", parentAlarmId = alarm.id, offsetMinutes = 1440, customTitle = "Prepare docs"),
            SmartReminderEntity(id = "r-2", parentAlarmId = alarm.id, offsetMinutes = 60, customTitle = "Leave house"),
        )

        dao.saveAlarmComplete(alarm, dates, reminders)

        val loaded = dao.getAlarmWithDatesById(alarm.id)
        assertNotNull(loaded)
        assertEquals(1, loaded!!.dates.size)
        assertEquals(2, loaded.reminders.size)
        assertEquals("Bring lab results", loaded.alarm.description)
        assertEquals("Health", loaded.alarm.category)

        // Toggle single smart reminder off
        dao.setReminderEnabled("r-2", false, 99_999L)
        val updatedReminders = dao.getRemindersForAlarm(alarm.id)
        assertFalse(updatedReminders.first { it.id == "r-2" }.isEnabled)
        assertTrue(updatedReminders.first { it.id == "r-1" }.isEnabled)

        // Soft delete hides alarm from active non-deleted queries
        dao.softDeleteAlarmById(alarm.id, 100_000L)
        assertTrue(dao.getAllNonDeletedAlarmsWithDates().isEmpty())
        assertEquals(1, dao.getAllAlarmsIncludingDeleted().size)

        // Hard delete cascades to both alarm_dates and smart_reminders
        dao.deleteAlarmById(alarm.id)
        assertNull(dao.getAlarmWithDatesById(alarm.id))
        assertTrue(dao.getRemindersForAlarm(alarm.id).isEmpty())
    }

    @Test
    fun `alarm history records and clears properly`() = runBlocking {
        val dao = db.alarmDao()
        dao.insertAlarmHistory(
            AlarmHistoryEntity(
                alarmId = "a-1",
                alarmLabel = "Morning Shift",
                outcome = AlarmHistoryOutcome.COMPLETED.name,
                triggeredAt = 1_700_000_000_000L,
            ),
        )
        dao.insertAlarmHistory(
            AlarmHistoryEntity(
                alarmId = "a-1",
                alarmLabel = "Morning Shift",
                outcome = AlarmHistoryOutcome.SNOOZED.name,
                triggeredAt = 1_700_000_600_000L,
            ),
        )

        val all = dao.observeAlarmHistory().first()
        assertEquals(2, all.size)

        dao.clearAlarmHistory()
        assertTrue(dao.observeAlarmHistory().first().isEmpty())
    }
}

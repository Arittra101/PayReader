package com.example.noticereader.feature.transaction.domain

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * All bKash times are Asia/Dhaka (UTC+6), which never observes daylight saving.
 * Every part of the app that deals with payment time goes through here, so the
 * offset is defined in exactly one place.
 */
object BkashTime {

    val OFFSET: ZoneOffset = ZoneOffset.ofHours(6)

    /** The current wall-clock time in Dhaka. Used for `received_at`. */
    fun now(): OffsetDateTime = OffsetDateTime.now(OFFSET)

    fun fromEpochMillis(epochMillis: Long): OffsetDateTime =
        Instant.ofEpochMilli(epochMillis).atOffset(OFFSET)
}

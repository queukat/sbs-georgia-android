package com.queukat.sbsgeorgia.domain.service.tbc

import com.queukat.sbsgeorgia.domain.model.StatementMoney
import java.security.MessageDigest
import java.time.LocalDate

internal fun fingerprintFor(
    incomeDate: LocalDate,
    description: String,
    additionalInformation: String?,
    paidOut: StatementMoney?,
    paidIn: StatementMoney?,
    balance: StatementMoney?,
): String {
    val normalized = listOf(
        incomeDate.toString(),
        description.trim().lowercase(),
        additionalInformation?.trim()?.lowercase().orEmpty(),
        moneyForFingerprint(paidOut),
        moneyForFingerprint(paidIn),
        moneyForFingerprint(balance),
    ).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun moneyForFingerprint(money: StatementMoney?): String =
    if (money == null) "" else "${money.amount.stripTrailingZeros().toPlainString()}:${money.currency.orEmpty()}"

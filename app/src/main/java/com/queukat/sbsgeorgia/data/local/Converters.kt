package com.queukat.sbsgeorgia.data.local

import androidx.room.TypeConverter
import com.queukat.sbsgeorgia.domain.model.BaseCurrencyView
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AppTypeConverters {
    private val json = Json

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? =
        value?.let { json.encodeToString(ListSerializer(Int.serializer()), it) }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? =
        value?.let { json.decodeFromString(ListSerializer(Int.serializer()), it) }

    @TypeConverter
    fun fromThemeMode(value: ThemeMode?): String? = value?.name

    @TypeConverter
    fun toThemeMode(value: String?): ThemeMode? = value?.let(ThemeMode::valueOf)

    @TypeConverter
    fun fromBaseCurrencyView(value: BaseCurrencyView?): String? = value?.name

    @TypeConverter
    fun toBaseCurrencyView(value: String?): BaseCurrencyView? = value?.let(BaseCurrencyView::valueOf)

    @TypeConverter
    fun fromIncomeSourceType(value: IncomeSourceType?): String? = value?.name

    @TypeConverter
    fun toIncomeSourceType(value: String?): IncomeSourceType? = value?.let(IncomeSourceType::valueOf)

    @TypeConverter
    fun fromDeclarationInclusion(value: DeclarationInclusion?): String? = value?.name

    @TypeConverter
    fun toDeclarationInclusion(value: String?): DeclarationInclusion? = value?.let(DeclarationInclusion::valueOf)

    @TypeConverter
    fun fromFxRateSource(value: FxRateSource?): String? = value?.name

    @TypeConverter
    fun toFxRateSource(value: String?): FxRateSource? = value?.let(FxRateSource::valueOf)
}

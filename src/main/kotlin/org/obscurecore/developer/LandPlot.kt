import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные земельного участка")
data class LandPlot(
    @Schema(description = "Идентификатор участка", example = "FP123")
    val plotId: String?,

    @Schema(description = "Назначение участка", example = "Жилое строительство")
    val purpose: String?,

    @Schema(description = "Площадь участка в квадратных метрах", example = "1500.0")
    val area: Double?,

    @Schema(description = "Кадастровый номер участка", example = "77:12:3456789:0")
    val cadastralNumber: String?,

    @Schema(description = "Проект, к которому относится участок", example = "Проект А")
    val project: String?,

    @Schema(description = "Дополнительное поле для кросс-анализа 2", example = "Значение")
    val crossAnalysisField2: String?,

    @Schema(description = "Идентификатор Genplan", example = "GP123")
    val genplanId: String?,

    @Schema(description = "Зона Genplan", example = "Центральная")
    val genplanZone: String?,

    @Schema(description = "Номер зоны Genplan", example = "5")
    val genplanZoneNumber: String?,

    @Schema(description = "Идентификатор плана Genplan", example = "GPlan01")
    val genplanPlanId: String?,

    @Schema(description = "Идентификатор ОКН", example = "OKN456")
    val oknId: String?,

    @Schema(description = "Наименование ОКН", example = "ОКН Пример")
    val okn: String?,

    @Schema(description = "Идентификатор ЗРЗ", example = "ZRZ789")
    val zoningId: String?,

    @Schema(description = "Наименование ЗРЗ", example = "ЗРЗ Пример")
    val zoning: String?,

    @Schema(description = "Ограничение по высоте для ЗРЗ", example = "15 м")
    val zoningHeightRestriction: String?,

    @Schema(description = "Идентификатор ИЦГФО", example = "ICGFO101")
    val icgfoId: String?,

    @Schema(description = "Наименование ИЦГФО", example = "ИЦГФО Пример")
    val icgfo: String?,

    @Schema(description = "Идентификатор ППТ", example = "PPT202")
    val pptId: String?,

    @Schema(description = "Наименование ППТ", example = "ППТ Пример")
    val ppt: String?,

    @Schema(description = "Идентификатор территории ОКН", example = "OT303")
    val oknTerritoryId: String?,

    @Schema(description = "Наименование территории ОКН", example = "Территория Пример")
    val oknTerritory: String?,

    @Schema(description = "Дополнительное поле для кросс-анализа 1", example = "Значение")
    val crossAnalysisField1: String?,

    @Schema(description = "Идентификатор природно-рекреационного комплекса", example = "RC404")
    val recreationalComplexId: String?,

    @Schema(description = "Наименование природно-рекреационного комплекса", example = "Рекреационный комплекс Пример")
    val recreationalComplex: String?,

    @Schema(description = "Идентификатор подзоны ПЗЗ", example = "PZZ505")
    val pzzSubzoneId: String?,

    @Schema(description = "Наименование подзоны ПЗЗ", example = "Подзона Пример")
    val pzzSubzone: String?,

    @Schema(description = "Сокращённое название подзоны ПЗЗ", example = "ПЗЗ Сокр")
    val pzzSubzoneShort: String?,

    @Schema(description = "Идентификатор ПЗЗ", example = "PZZ606")
    val pzzId: String?,

    @Schema(description = "Наименование ПЗЗ", example = "ПЗЗ Пример")
    val pzz: String?
)
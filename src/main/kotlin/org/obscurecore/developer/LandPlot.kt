package org.obscurecore.developer

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LandPlot(
    val plotId: String?,
    val purpose: String?,
    val area: Double?,
    val cadastralNumber: String?,
    val project: String?,
    val crossAnalysisField2: String?,
    val genplanId: String?,
    val genplanZone: String?,
    val genplanZoneNumber: String?,
    val genplanPlanId: String?,
    val oknId: String?,
    val okn: String?,
    val zoningId: String?,
    val zoning: String?,
    val zoningHeightRestriction: String?,
    val icgfoId: String?,
    val icgfo: String?,
    val pptId: String?,
    val ppt: String?,
    val oknTerritoryId: String?,
    val oknTerritory: String?,
    val crossAnalysisField1: String?,
    val recreationalComplexId: String?,
    val recreationalComplex: String?,
    val pzzSubzoneId: String?,
    val pzzSubzone: String?,
    val pzzSubzoneShort: String?,
    val pzzId: String?,
    val pzz: String?
)
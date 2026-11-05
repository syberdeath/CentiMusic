package com.hkm.carlauncher.map

/** A road under the car, as read from the rendered map. */
data class RoadFeature(val name: String?, val roadClass: String?)

private val classRank = listOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service")

/** The most important named road; roads of unknown class rank last. */
fun pickStreetName(features: List<RoadFeature>): String? =
    features
        .filter { !it.name.isNullOrBlank() }
        .minByOrNull { f -> classRank.indexOf(f.roadClass).let { if (it < 0) classRank.size else it } }
        ?.name?.trim()

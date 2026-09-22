/*
 * Copyright (c) 2026 Jayvardhan Potabatti
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.adhytm.astronomy

/**
 * One Vedic whole-sign drishti from [fromGraha] onto [toGraha]'s rashi.
 *
 * @property house the house count from the aspecting graha to the target (7 = opposition, etc.).
 */
data class AspectLink(
    val fromGraha: Graha,
    val fromRasi: Rasi,
    val toGraha: Graha,
    val toRasi: Rasi,
    val house: Int,
)

/**
 * All graha→graha whole-sign aspects in this chart (Rahu/Ketu never aspect).
 * Conjunction is not included — only true aspect houses.
 */
fun NatalChart.aspectLinks(): List<AspectLink> {
    val links = ArrayList<AspectLink>()
    for (from in grahas) {
        for (to in grahas) {
            if (from.graha == to.graha) continue
            if (!Drishti.aspects(from.graha, from.rasi.index, to.rasi.index)) continue
            links.add(
                AspectLink(
                    fromGraha = from.graha,
                    fromRasi = from.rasi,
                    toGraha = to.graha,
                    toRasi = to.rasi,
                    house = houseFrom(from.rasi.index, to.rasi.index),
                ),
            )
        }
    }
    return links
}

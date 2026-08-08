package com.badminton.bot.service;

import java.util.Collection;
import java.util.Locale;

/**
 * Модель «скилловости»:
 * <ul>
 *   <li>опыт → часы подтверждённой игры;</li>
 *   <li>скилл игрока 0–10 с убывающей отдачей;</li>
 *   <li>скилл слота — среднее по известным игрокам (не сумма часов).</li>
 * </ul>
 * Гости (+N) в среднее не входят — их уровень неизвестен.
 */
public final class SlotSkillModel {

    /** При стольких часах скилл достигает 10. */
    public static final double HOURS_FOR_MAX_SKILL = 63.0;

    private SlotSkillModel() {
    }

    /**
     * Скилл игрока 0..10: {@code 10 * log2(1+h) / log2(1+Hmax)}.
     * Новички быстро набирают очки, дальше рост замедляется.
     */
    public static double playerSkill(double hoursPlayed) {
        if (hoursPlayed <= 0 || Double.isNaN(hoursPlayed)) {
            return 0.0;
        }
        double denom = Math.log(1.0 + HOURS_FOR_MAX_SKILL) / Math.log(2.0);
        double raw = Math.log(1.0 + hoursPlayed) / Math.log(2.0);
        return Math.min(10.0, 10.0 * raw / denom);
    }

    public static double playerSkillFromMinutes(long minutesPlayed) {
        return playerSkill(minutesPlayed / 60.0);
    }

    /** Скилл слота — среднее арифметическое скиллов известных игроков. */
    public static double slotSkill(Collection<Double> playerSkills) {
        if (playerSkills == null || playerSkills.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        int n = 0;
        for (Double s : playerSkills) {
            if (s == null) {
                continue;
            }
            sum += s;
            n++;
        }
        return n == 0 ? 0.0 : sum / n;
    }

    public static String formatSkill(double skill) {
        return String.format(Locale.US, "%.1f", clamp(skill));
    }

    public static String bandLabel(double skill) {
        double s = clamp(skill);
        if (s < 2.0) {
            return "новички";
        }
        if (s < 4.0) {
            return "начинающие";
        }
        if (s < 6.0) {
            return "микс";
        }
        if (s < 8.0) {
            return "сильнее";
        }
        return "топ";
    }

    public static String formatSlotBadge(double slotSkillValue) {
        return formatSkill(slotSkillValue) + " " + bandLabel(slotSkillValue);
    }

    private static double clamp(double skill) {
        if (Double.isNaN(skill) || skill < 0) {
            return 0;
        }
        return Math.min(10.0, skill);
    }
}

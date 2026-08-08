package com.badminton.bot;

import com.badminton.bot.service.SlotSkillModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotSkillModelTest {

    @Test
    void zeroHoursIsZeroSkill() {
        assertEquals(0.0, SlotSkillModel.playerSkill(0), 1e-9);
    }

    @Test
    void moreHoursGivesHigherSkillWithDiminishingReturns() {
        double s1 = SlotSkillModel.playerSkill(1);
        double s5 = SlotSkillModel.playerSkill(5);
        double s20 = SlotSkillModel.playerSkill(20);
        double s60 = SlotSkillModel.playerSkill(60);
        assertTrue(s1 < s5);
        assertTrue(s5 < s20);
        assertTrue(s20 < s60);
        assertTrue(s5 - s1 > s60 - s20); // ранняя отдача выше
        assertTrue(s60 <= 10.0);
    }

    @Test
    void slotSkillIsAverageNotSum() {
        double slot = SlotSkillModel.slotSkill(List.of(2.0, 8.0));
        assertEquals(5.0, slot, 1e-9);
        assertEquals("микс", SlotSkillModel.bandLabel(slot));
    }

    @Test
    void ceilingAroundReferenceHours() {
        assertEquals(10.0, SlotSkillModel.playerSkill(SlotSkillModel.HOURS_FOR_MAX_SKILL), 0.05);
    }
}

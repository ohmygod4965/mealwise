package com.mealwise.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MealTextParserTest {

    @Test
    @DisplayName("规则解析：切段、剥时间词、抽数量")
    void parsesTwoFoods() {
        List<MealTextParser.RawItem> items = MealTextParser.ruleParse("中午吃了两碗米饭和一份鸡胸肉");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("米饭");
        assertThat(items.get(0).quantity()).isEqualTo("两碗");
        assertThat(items.get(1).name()).isEqualTo("鸡胸肉");
        assertThat(items.get(1).quantity()).isEqualTo("一份");
    }

    @Test
    @DisplayName("没有数量词时 quantity 为 null，交给上层追问")
    void missingQuantity() {
        List<MealTextParser.RawItem> items = MealTextParser.ruleParse("晚上吃了西兰花");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).name()).isEqualTo("西兰花");
        assertThat(items.get(0).quantity()).isNull();
    }

    @Test
    @DisplayName("显式克数保持原样")
    void keepsGramQuantity() {
        List<MealTextParser.RawItem> items = MealTextParser.ruleParse("午饭 米饭 200克，鸡胸肉 150g");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).quantity()).isEqualTo("200克");
        assertThat(items.get(1).quantity()).isEqualTo("150g");
    }

    @Test
    @DisplayName("中文数字：两 / 半 / 二十三")
    void parsesChineseNumbers() {
        assertThat(MealTextParser.parseChineseNumber("两")).isEqualByComparingTo("2");
        assertThat(MealTextParser.parseChineseNumber("半")).isEqualByComparingTo("0.5");
        assertThat(MealTextParser.parseChineseNumber("二十三")).isEqualByComparingTo("23");
        assertThat(MealTextParser.parseChineseNumber("2.5")).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("模型 JSON → RawItem")
    void parsesLlmJson() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree("""
                {"items":[{"name":"米饭","quantity":"两碗"},{"name":"鸡胸肉","quantity":null}]}
                """);

        List<MealTextParser.RawItem> items = MealTextParser.fromLlmJson(root);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).quantity()).isEqualTo("两碗");
        assertThat(items.get(1).quantity()).isNull();
        assertThat(new BigDecimal("1")).isNotNull();
    }
}

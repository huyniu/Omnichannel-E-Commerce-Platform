package com.omni.product.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugifyTest {

    @Test
    void removesVietnameseAccents() {
        assertThat(ProductService.slugify("Áo thun Basic Cotton")).isEqualTo("ao-thun-basic-cotton");
        assertThat(ProductService.slugify("Điện thoại  iPhone 15!")).isEqualTo("dien-thoai-iphone-15");
    }
}

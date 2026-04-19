package com.meridianmart.integration;

import com.meridianmart.model.Product;
import com.meridianmart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;

    private Long productId;
    private Long lowStockProductId;

    @BeforeEach
    void setUpProducts() {
        productRepository.deleteAll();

        Product p1 = Product.builder()
                .name("Test TV").description("A great TV")
                .price(BigDecimal.valueOf(499.99)).stockQuantity(10)
                .category("Electronics").imageUrl("/img/tv.jpg").build();
        Product p2 = Product.builder()
                .name("Low Stock Item").description("Nearly gone")
                .price(BigDecimal.valueOf(99.99)).stockQuantity(1)
                .category("Electronics").imageUrl("/img/low.jpg").build();

        p1 = productRepository.save(p1);
        p2 = productRepository.save(p2);
        productId = p1.getId();
        lowStockProductId = p2.getId();
    }

    @Test
    void getCatalogReturns200WithItemsPageSizeTotal() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.size").isNumber())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void getProductReturns200WithFullFields() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.name").value("Test TV"))
                .andExpect(jsonPath("$.data.price").isNumber())
                .andExpect(jsonPath("$.data.stockQuantity").isNumber())
                .andExpect(jsonPath("$.data.stockWarning").isBoolean());
    }

    @Test
    void stockWarningIsTrueWhenStockBelowTwo() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/products/" + lowStockProductId)
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockWarning").value(true));
    }

    @Test
    void getProductNotFoundReturns404WithErrorMessage() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/products/99999")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorMessage").isString());
    }
}

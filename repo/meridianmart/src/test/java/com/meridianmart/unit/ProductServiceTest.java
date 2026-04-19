package com.meridianmart.unit;

import com.meridianmart.dto.PagedResponse;
import com.meridianmart.dto.ProductDto;
import com.meridianmart.model.Product;
import com.meridianmart.repository.ProductRepository;
import com.meridianmart.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @InjectMocks ProductService productService;

    private static final String STORE = "STORE_001";

    private Product buildProduct(Long id, int stock) {
        return Product.builder()
                .id(id)
                .name("Test Product " + id)
                .description("Description")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(stock)
                .category("Electronics")
                .imageUrl("/img/test.jpg")
                .newArrival(false)
                .storeId(STORE)
                .build();
    }

    @Test
    void catalogReturnsPaginatedListWithCorrectFields() {
        List<Product> products = List.of(buildProduct(1L, 10), buildProduct(2L, 5));
        Page<Product> page = new PageImpl<>(products, PageRequest.of(0, 20), 2);
        when(productRepository.findByStoreId(eq(STORE), any(PageRequest.class))).thenReturn(page);

        PagedResponse<ProductDto> result = productService.getCatalog(STORE, 0, 20);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Test Product 1");
        assertThat(result.getItems().get(0).getPrice()).isEqualTo(BigDecimal.valueOf(99.99));
    }

    @Test
    void productDetailReturnsFullProductObject() {
        Product product = buildProduct(1L, 10);
        when(productRepository.findByIdAndStoreId(eq(1L), eq(STORE))).thenReturn(Optional.of(product));

        ProductDto dto = productService.getProductById(1L, STORE);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Test Product 1");
        assertThat(dto.getCategory()).isEqualTo("Electronics");
        assertThat(dto.getPrice()).isEqualTo(BigDecimal.valueOf(99.99));
    }

    @Test
    void stockWarningIsTrueWhenStockBelowTwo() {
        Product lowStock = buildProduct(1L, 1);
        when(productRepository.findByIdAndStoreId(eq(1L), eq(STORE))).thenReturn(Optional.of(lowStock));

        ProductDto dto = productService.getProductById(1L, STORE);
        assertThat(dto.isStockWarning()).isTrue();
    }

    @Test
    void stockWarningIsFalseWhenStockSufficient() {
        Product product = buildProduct(1L, 10);
        when(productRepository.findByIdAndStoreId(eq(1L), eq(STORE))).thenReturn(Optional.of(product));

        ProductDto dto = productService.getProductById(1L, STORE);
        assertThat(dto.isStockWarning()).isFalse();
    }

    @Test
    void getProductByIdThrows404WhenNotFound() {
        when(productRepository.findByIdAndStoreId(eq(99999L), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99999L, STORE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(404);
    }

    @Test
    void decrementStockReducesQuantity() {
        Product product = buildProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.decrementStock(1L, 3);
        assertThat(result.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void decrementStockThrowsWhenInsufficientStock() {
        Product product = buildProduct(1L, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decrementStock(1L, 5))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(400);
    }
}
